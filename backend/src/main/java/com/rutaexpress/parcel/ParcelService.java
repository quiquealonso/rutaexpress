package com.rutaexpress.parcel;

import com.rutaexpress.parcel.ParcelDtos.AddressResponse;
import com.rutaexpress.parcel.ParcelDtos.CreateParcelRequest;
import com.rutaexpress.parcel.ParcelDtos.ImportError;
import com.rutaexpress.parcel.ParcelDtos.ImportResult;
import com.rutaexpress.parcel.ParcelDtos.ParcelEventResponse;
import com.rutaexpress.parcel.ParcelDtos.ParcelFilter;
import com.rutaexpress.parcel.ParcelDtos.ParcelInfo;
import com.rutaexpress.parcel.ParcelDtos.ParcelListItem;
import com.rutaexpress.parcel.ParcelDtos.ParcelResponse;
import com.rutaexpress.parcel.ParcelDtos.PublicEvent;
import com.rutaexpress.parcel.ParcelDtos.PublicParcelView;
import com.rutaexpress.parcel.ParcelDtos.RecipientDto;
import com.rutaexpress.parcel.ParcelDtos.StatusCountResponse;
import com.rutaexpress.parcel.ParcelDtos.UpdateParcelRequest;
import com.rutaexpress.shared.error.BusinessException;
import com.rutaexpress.shared.error.NotFoundException;
import com.rutaexpress.shared.events.EventSource;
import com.rutaexpress.shared.geo.Geometries;
import com.rutaexpress.shared.security.CurrentUser;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ParcelService {

    private static final Logger log = LoggerFactory.getLogger(ParcelService.class);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParcelRepository parcels;
    private final RecipientRepository recipients;
    private final ParcelEventRepository events;
    private final AddressService addressService;
    private final LabelService labelService;
    private final Validator validator;

    ParcelService(ParcelRepository parcels, RecipientRepository recipients, ParcelEventRepository events,
                  AddressService addressService, LabelService labelService, Validator validator) {
        this.parcels = parcels;
        this.recipients = recipients;
        this.events = events;
        this.addressService = addressService;
        this.labelService = labelService;
        this.validator = validator;
    }

    // ---------- Gestión ----------

    @Transactional
    public ParcelResponse create(CreateParcelRequest r) {
        Parcel parcel = createEntity(r);
        return toResponse(parcel);
    }

    private Parcel createEntity(CreateParcelRequest r) {
        String code = r.trackingCode() != null ? r.trackingCode() : newTrackingCode();
        if (parcels.existsByTrackingCode(code)) {
            throw new BusinessException("tracking-code-taken", "Ya existe un paquete con el código " + code);
        }
        Recipient recipient = recipients.save(new Recipient(r.recipient().name(), r.recipient().phone(),
                r.recipient().email()));
        Address address = addressService.findOrCreate(r.address());
        Parcel parcel = new Parcel(code, r.weightKg(), r.depotId(), recipient, address);
        parcel.setDimensions(r.lengthCm(), r.widthCm(), r.heightCm());
        parcel.setWindow(r.windowStart(), r.windowEnd());
        parcel.setNotes(r.notes());
        parcel.setAssignedDriverId(r.assignedDriverId());
        parcels.save(parcel);
        events.save(new ParcelEvent(parcel, "CREATED", CurrentUser.actorName(), EventSource.API,
                Map.of("needsReview", address.isNeedsReview())));
        return parcel;
    }

    @Transactional
    public ParcelResponse update(UUID id, UpdateParcelRequest r) {
        Parcel parcel = detailed(id);
        if (parcel.getStatus() != ParcelStatus.REGISTERED && parcel.getStatus() != ParcelStatus.FAILED_ATTEMPT) {
            throw new BusinessException("parcel-not-editable", "Solo se pueden editar paquetes que no estén en reparto");
        }
        Recipient recipient = parcel.getRecipient();
        recipient.setName(r.recipient().name());
        recipient.setPhone(r.recipient().phone());
        recipient.setEmail(r.recipient().email());
        parcel.setAddress(addressService.findOrCreate(r.address()));
        parcel.setWeightKg(r.weightKg());
        parcel.setDimensions(r.lengthCm(), r.widthCm(), r.heightCm());
        parcel.setWindow(r.windowStart(), r.windowEnd());
        parcel.setNotes(r.notes());
        parcel.setAssignedDriverId(r.assignedDriverId());
        events.save(new ParcelEvent(parcel, "UPDATED", CurrentUser.actorName(), EventSource.API, Map.of()));
        return toResponse(parcel);
    }

    @Transactional
    public void cancel(UUID id) {
        Parcel parcel = detailed(id);
        parcel.transitionTo(ParcelStatus.CANCELLED);
        events.save(new ParcelEvent(parcel, "CANCELLED", CurrentUser.actorName(), EventSource.API, Map.of()));
    }

    public ParcelResponse get(UUID id) {
        return toResponse(detailed(id));
    }

    public Page<ParcelListItem> list(ParcelFilter filter, Pageable pageable) {
        return parcels.findAll(specification(filter), pageable).map(ParcelService::toListItem);
    }

    public List<ParcelEventResponse> history(UUID id) {
        return events.findByParcelIdOrderByOccurredAtAsc(id).stream()
                .map(e -> new ParcelEventResponse(e.getId(), e.getType(), e.getOccurredAt(), e.getActor(),
                        e.getSource().name(), e.getData()))
                .toList();
    }

    public List<StatusCountResponse> statusCounts(Instant since) {
        return parcels.countByStatusSince(since).stream()
                .map(c -> new StatusCountResponse(c.getStatus().name(), c.getTotal()))
                .toList();
    }

    public byte[] labels(List<UUID> ids) {
        List<Parcel> list = parcels.findDetailedByIdIn(ids);
        if (list.isEmpty()) {
            throw new NotFoundException("Paquetes", ids);
        }
        return labelService.render(list);
    }

    /**
     * Importación CSV: cada fila se valida y se guarda de forma independiente, de modo que una fila errónea
     * no invalida las demás.
     */
    @Transactional
    public ImportResult importCsv(InputStream csv, UUID depotId) throws IOException {
        int created = 0, needsReview = 0;
        List<ImportError> errors = new ArrayList<>();
        for (CsvParcelParser.Row row : CsvParcelParser.parse(csv, depotId)) {
            if (row.error() != null) {
                errors.add(new ImportError(row.number(), row.error()));
                continue;
            }
            Set<ConstraintViolation<CreateParcelRequest>> violations = validator.validate(row.request());
            if (!violations.isEmpty()) {
                errors.add(new ImportError(row.number(), violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage()).collect(Collectors.joining("; "))));
                continue;
            }
            try {
                Parcel p = createEntity(row.request());
                created++;
                if (p.getAddress().isNeedsReview()) {
                    needsReview++;
                }
            } catch (RuntimeException e) {
                errors.add(new ImportError(row.number(), e.getMessage()));
            }
        }
        log.info("Importación CSV: {} creados, {} con error, {} para revisar", created, errors.size(), needsReview);
        return new ImportResult(created, errors.size(), needsReview, errors);
    }

    // ---------- API para otros módulos ----------

    public List<ParcelInfo> findInfo(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return parcels.findDetailedByIdIn(ids).stream().map(ParcelService::toInfo).toList();
    }

    public Optional<ParcelInfo> findInfoByTrackingCode(String code) {
        return parcels.findByTrackingCode(code.trim().toUpperCase(Locale.ROOT)).map(ParcelService::toInfo);
    }

    /** Paquetes que un repartidor debería cargar: asignados a él + los de su zona sin asignar. */
    public List<UUID> findLoadableFor(UUID depotId, UUID driverId, Optional<Polygon> zone) {
        Set<UUID> ids = new LinkedHashSet<>(parcels.findLoadableAssignedTo(depotId, driverId));
        zone.ifPresent(z -> ids.addAll(parcels.findLoadableInZone(depotId, z.toText())));
        return List.copyOf(ids);
    }

    /** Busca entre los paquetes indicados por código, nombre del destinatario o calle. */
    public List<ParcelInfo> searchAmong(Collection<UUID> candidateIds, String query) {
        if (candidateIds.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return parcels.searchAmong(candidateIds, pattern).stream().map(ParcelService::toInfo).toList();
    }

    public Optional<PublicParcelView> publicView(String trackingCode) {
        return parcels.findByTrackingCode(trackingCode.trim().toUpperCase(Locale.ROOT)).map(p ->
                new PublicParcelView(p.getId(), p.getTrackingCode(), p.getStatus().name(), p.getAddress().getCity(),
                        p.getAddress().getPostalCode(),
                        events.findByParcelIdOrderByOccurredAtAsc(p.getId()).stream()
                                .filter(e -> PUBLIC_EVENT_TYPES.contains(e.getType()))
                                .map(e -> new PublicEvent(e.getType(), e.getOccurredAt()))
                                .toList()));
    }

    private static final Set<String> PUBLIC_EVENT_TYPES = Set.of("CREATED", "LOADED", "OUT_FOR_DELIVERY",
            "DELIVERED", "FAILED_ATTEMPT", "RETURNED", "CANCELLED");

    /** Cambia el estado de varios paquetes y deja constancia en el historial. */
    @Transactional
    public void changeStatus(Collection<UUID> ids, ParcelStatus target, String actor, EventSource source,
                             Map<String, Object> data) {
        for (Parcel parcel : parcels.findDetailedByIdIn(ids)) {
            if (parcel.getStatus() == target || !parcel.getStatus().canTransitionTo(target)) {
                log.debug("Paquete {}: se ignora la transición {} -> {}", parcel.getTrackingCode(),
                        parcel.getStatus(), target);
                continue;
            }
            parcel.transitionTo(target);
            events.save(new ParcelEvent(parcel, target.name(), actor, source, data));
        }
    }

    @Transactional
    public void recordEvent(Collection<UUID> ids, String type, String actor, EventSource source,
                            Map<String, Object> data) {
        for (Parcel parcel : parcels.findAllById(ids)) {
            events.save(new ParcelEvent(parcel, type, actor, source, data));
        }
    }

    // ---------- Internos ----------

    private Parcel detailed(UUID id) {
        return parcels.findDetailedById(id).orElseThrow(() -> new NotFoundException("Paquete", id));
    }

    private String newTrackingCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("RX");
            for (int i = 0; i < 10; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!parcels.existsByTrackingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de seguimiento único");
    }

    private static Specification<Parcel> specification(ParcelFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Parcel, Recipient> recipient = root.join("recipient");
            Join<Parcel, Address> address = root.join("address");
            if (f.status() != null && !f.status().isBlank()) {
                predicates.add(cb.equal(root.get("status"), ParcelStatus.valueOf(f.status())));
            }
            if (f.depotId() != null) {
                predicates.add(cb.equal(root.get("depotId"), f.depotId()));
            }
            if (f.assignedDriverId() != null) {
                predicates.add(cb.equal(root.get("assignedDriverId"), f.assignedDriverId()));
            }
            if (f.needsReview() != null) {
                predicates.add(cb.equal(address.get("needsReview"), f.needsReview()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String pattern = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("trackingCode")), pattern),
                        cb.like(cb.lower(recipient.get("name")), pattern),
                        cb.like(cb.lower(address.get("street")), pattern),
                        cb.like(cb.lower(address.get("city")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static ParcelResponse toResponse(Parcel p) {
        Address a = p.getAddress();
        Recipient r = p.getRecipient();
        return new ParcelResponse(p.getId(), p.getTrackingCode(), p.getStatus().name(), p.getWeightKg(),
                p.getLengthCm(), p.getWidthCm(), p.getHeightCm(), p.getWindowStart(), p.getWindowEnd(), p.getNotes(),
                p.getDepotId(), p.getAssignedDriverId(), p.getDeliveryAttempts(),
                new RecipientDto(r.getName(), r.getPhone(), r.getEmail()),
                new AddressResponse(a.getId(), a.getStreet(), a.getNumber(), a.getExtra(), a.getPostalCode(),
                        a.getCity(), a.getCountry(), Geometries.toGeoPoint(a.getLocation()), a.getGeocodeQuality(),
                        a.getGeocodeProvider(), a.isNeedsReview(), a.oneLine()),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private static ParcelListItem toListItem(Parcel p) {
        return new ParcelListItem(p.getId(), p.getTrackingCode(), p.getStatus().name(), p.getRecipient().getName(),
                p.getAddress().oneLine(), p.getAddress().getCity(), p.getAddress().isNeedsReview(), p.getWeightKg(),
                p.getAssignedDriverId(), p.getCreatedAt());
    }

    private static ParcelInfo toInfo(Parcel p) {
        Address a = p.getAddress();
        return new ParcelInfo(p.getId(), p.getTrackingCode(), p.getStatus().name(), p.getDepotId(),
                p.getAssignedDriverId(), a.getId(), a.oneLine(), Geometries.toGeoPoint(a.getLocation()),
                a.isNeedsReview(), p.getRecipient().getName(), p.getRecipient().getPhone(), p.getNotes(),
                p.getWindowStart(), p.getWindowEnd(), p.getWeightKg());
    }
}
