package com.rutaexpress.delivery;

import com.rutaexpress.delivery.DeliveryDtos.DashboardResponse;
import com.rutaexpress.delivery.DeliveryDtos.DirectionsResponse;
import com.rutaexpress.delivery.DeliveryDtos.FailReason;
import com.rutaexpress.delivery.DeliveryDtos.IncidentResponse;
import com.rutaexpress.delivery.DeliveryDtos.ProofResponse;
import com.rutaexpress.delivery.DeliveryDtos.Step;
import com.rutaexpress.geo.GeoModels.RouteGeometry;
import com.rutaexpress.parcel.ParcelService;
import com.rutaexpress.routing.RouteService;
import com.rutaexpress.routing.RoutingDtos.DayStats;
import com.rutaexpress.routing.RoutingDtos.RouteDetail;
import com.rutaexpress.routing.RoutingDtos.StopDetail;
import com.rutaexpress.shared.error.BusinessException;
import com.rutaexpress.shared.error.NotFoundException;
import com.rutaexpress.shared.events.DomainEvents.IncidentReportedEvent;
import com.rutaexpress.shared.events.EventSource;
import com.rutaexpress.shared.geo.GeoPoint;
import com.rutaexpress.shared.geo.Geometries;
import com.rutaexpress.shared.security.CurrentUser;
import com.rutaexpress.tracking.TrackingService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Ejecución del reparto: entregas con prueba, ausencias, saltos, incidencias y recálculos.
 * La API REST, el asistente y el servidor MCP usan este mismo servicio, con las mismas validaciones.
 */
@Service
public class DeliveryService {

    private static final long MAX_PROOF_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final RouteService routes;
    private final TrackingService tracking;
    private final ParcelService parcels;
    private final ProofOfDeliveryRepository proofs;
    private final IncidentRepository incidents;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;

    DeliveryService(RouteService routes, TrackingService tracking, ParcelService parcels,
                    ProofOfDeliveryRepository proofs, IncidentRepository incidents, FileStorage storage,
                    ApplicationEventPublisher events) {
        this.routes = routes;
        this.tracking = tracking;
        this.parcels = parcels;
        this.proofs = proofs;
        this.incidents = incidents;
        this.storage = storage;
        this.events = events;
    }

    // ---------- Ruta ----------

    @Transactional
    public RouteDetail startRoute(UUID routeId) {
        return routes.start(routeId);
    }

    @Transactional
    public RouteDetail reoptimize(UUID routeId, GeoPoint position, EventSource source) {
        RouteDetail current = routes.get(routeId);
        GeoPoint from = position != null ? position : tracking.latestPoint(current.driverId()).orElse(null);
        return routes.reoptimize(routeId, from, source == EventSource.API ? "REOPTIMIZE" : "REOPTIMIZE_" + source);
    }

    // ---------- Paradas ----------

    /**
     * Entrega con prueba. Idempotente: si la parada ya está entregada (reenvío desde el modo sin conexión),
     * devuelve el estado actual en lugar de fallar.
     */
    @Transactional
    public StopDetail deliver(UUID stopId, ProofOfDelivery.Type proofType, String receiverName, MultipartFile file,
                              GeoPoint position, EventSource source) {
        StopDetail stop = routes.getStop(stopId);
        if ("DELIVERED".equals(stop.status())) {
            return stop;
        }
        String fileKey = null;
        String contentType = null;
        if (proofType == ProofOfDelivery.Type.SIGNATURE || proofType == ProofOfDelivery.Type.PHOTO) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("La entrega con " + proofType.name().toLowerCase(Locale.ROOT)
                        + " necesita el fichero");
            }
            validateFile(file);
            contentType = file.getContentType();
            fileKey = "%s/%s/%s.%s".formatted(LocalDate.now(), stop.routeId(), stopId, extension(contentType));
            try (InputStream in = file.getInputStream()) {
                storage.store(fileKey, in, file.getSize(), contentType);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        proofs.save(new ProofOfDelivery(stopId, proofType, fileKey, contentType, receiverName,
                position == null ? null : Geometries.point(position), CurrentUser.actorName()));
        return routes.markDelivered(stopId, source);
    }

    /** Entrega sin fichero (desde el asistente o el MCP): se registra el nombre de quien recoge. */
    @Transactional
    public StopDetail deliverWithoutFile(UUID stopId, String receiverName, EventSource source) {
        StopDetail stop = routes.getStop(stopId);
        if ("DELIVERED".equals(stop.status())) {
            return stop;
        }
        proofs.save(new ProofOfDelivery(stopId, ProofOfDelivery.Type.CODE, null, null, receiverName, null,
                CurrentUser.actorName()));
        return routes.markDelivered(stopId, source);
    }

    @Transactional
    public StopDetail fail(UUID stopId, FailReason reason, String note, boolean retryLater, GeoPoint position,
                           EventSource source) {
        String text = reason.label() + (note == null || note.isBlank() ? "" : ": " + note.trim());
        return routes.markFailed(stopId, text, retryLater, source, positionOrLatest(stopId, position));
    }

    @Transactional
    public RouteDetail skip(UUID stopId, GeoPoint position, EventSource source) {
        return routes.skip(stopId, source, positionOrLatest(stopId, position));
    }

    private GeoPoint positionOrLatest(UUID stopId, GeoPoint position) {
        if (position != null) {
            return position;
        }
        UUID driverId = routes.driverIdOfStop(stopId);
        return tracking.latestPoint(driverId).orElse(null);
    }

    public DirectionsResponse directions(UUID routeId, UUID stopId) {
        RouteDetail route = routes.get(routeId);
        GeoPoint from = tracking.latestPoint(route.driverId()).orElse(null);
        RouteGeometry geometry = routes.directions(routeId, stopId, from);
        StopDetail target = stopId != null ? routes.getStop(stopId)
                : routes.nextStop(routeId).orElseThrow(() -> new BusinessException("no-pending-stops",
                "No quedan paradas pendientes"));
        List<Step> steps = geometry.legs().stream().flatMap(l -> l.steps().stream())
                .map(s -> new Step(s.instruction(), s.distanceMeters(), s.durationSeconds())).toList();
        GeoPoint dest = target.location();
        String gmaps = "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=%s,%s"
                .formatted(dest.lat(), dest.lon());
        String waze = "https://waze.com/ul?ll=%s,%s&navigate=yes".formatted(dest.lat(), dest.lon());
        return new DirectionsResponse(target.id(), target.addressLine(), geometry.distanceMeters(),
                geometry.durationSeconds(), steps, geometry.points(), gmaps, waze, geometry.source());
    }

    // ---------- Pruebas de entrega ----------

    public ProofResponse proof(UUID stopId) {
        routes.getStop(stopId); // comprueba el acceso
        ProofOfDelivery p = proofs.findByStopId(stopId).orElseThrow(() -> new NotFoundException("Prueba de entrega", stopId));
        return new ProofResponse(p.getId(), p.getStopId(), p.getType().name(), p.getReceiverName(), p.getContentType(),
                p.getCreatedAt(), p.getFileKey() != null);
    }

    public ProofFile proofFile(UUID stopId) {
        routes.getStop(stopId);
        ProofOfDelivery p = proofs.findByStopId(stopId).filter(x -> x.getFileKey() != null)
                .orElseThrow(() -> new NotFoundException("Fichero de entrega", stopId));
        return new ProofFile(storage.load(p.getFileKey()), p.getContentType());
    }

    public record ProofFile(InputStream content, String contentType) {
    }

    // ---------- Incidencias ----------

    @Transactional
    public IncidentResponse reportIncident(Incident.Type type, String description, GeoPoint position,
                                           EventSource source) {
        UUID driverId = CurrentUser.driverId();
        UUID routeId = routes.currentRouteId(driverId).orElse(null);
        GeoPoint where = position != null ? position : tracking.latestPoint(driverId).orElse(null);
        Incident incident = incidents.save(new Incident(driverId, routeId, type, description,
                where == null ? null : Geometries.point(where), source));
        events.publishEvent(new IncidentReportedEvent(incident.getId(), driverId, routeId, type.name(), description,
                where == null ? null : where.lat(), where == null ? null : where.lon(), incident.getCreatedAt(),
                source));
        return toResponse(incident);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> incidents(boolean onlyOpen) {
        Instant start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return (onlyOpen ? incidents.findByResolvedAtIsNullOrderByCreatedAtDesc()
                : incidents.findByCreatedAtAfterOrderByCreatedAtDesc(start))
                .stream().map(DeliveryService::toResponse).toList();
    }

    @Transactional
    public IncidentResponse resolve(UUID incidentId) {
        Incident incident = incidents.findById(incidentId).orElseThrow(() -> new NotFoundException("Incidencia", incidentId));
        incident.resolve(CurrentUser.actorName());
        return toResponse(incident);
    }

    // ---------- Dashboard ----------

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Instant start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        DayStats stats = routes.dayStats();
        return new DashboardResponse(stats.routes(), stats.inProgress(), stats.completed(),
                parcels.statusCounts(start), stats.failedStops(), stats.deliveredStops(),
                stats.averageSecondsPerStop(), incidents.findByResolvedAtIsNullOrderByCreatedAtDesc().size());
    }

    private static void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_PROOF_BYTES) {
            throw new IllegalArgumentException("El fichero no puede superar 5 MB");
        }
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Tipo de fichero no permitido (PNG, JPEG o WebP)");
        }
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private static IncidentResponse toResponse(Incident i) {
        return new IncidentResponse(i.getId(), i.getDriverId(), i.getRouteId(), i.getType().name(), i.getDescription(),
                Geometries.toGeoPoint(i.getLocation()), i.getSource().name(), i.getCreatedAt(), i.getResolvedAt(),
                i.getResolvedBy());
    }
}
