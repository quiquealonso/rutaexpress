package com.rutaexpress.scanning;

import com.rutaexpress.catalog.CatalogDtos.DriverSummary;
import com.rutaexpress.catalog.CatalogService;
import com.rutaexpress.parcel.ParcelDtos.ParcelInfo;
import com.rutaexpress.parcel.ParcelService;
import com.rutaexpress.scanning.ScanningDtos.LoadInfo;
import com.rutaexpress.scanning.ScanningDtos.LoadSummary;
import com.rutaexpress.scanning.ScanningDtos.MissingParcel;
import com.rutaexpress.scanning.ScanningDtos.OpenLoadRequest;
import com.rutaexpress.scanning.ScanningDtos.ScanRequest;
import com.rutaexpress.scanning.ScanningDtos.ScanResponse;
import com.rutaexpress.shared.error.BusinessException;
import com.rutaexpress.shared.error.NotFoundException;
import com.rutaexpress.shared.events.DomainEvents.LoadClosedEvent;
import com.rutaexpress.shared.events.DomainEvents.ParcelsLoadedEvent;
import com.rutaexpress.shared.security.AuthenticatedUser;
import com.rutaexpress.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ScanningService {

    private final LoadRepository loads;
    private final ScanRepository scans;
    private final ParcelService parcels;
    private final CatalogService catalog;
    private final ApplicationEventPublisher events;

    ScanningService(LoadRepository loads, ScanRepository scans, ParcelService parcels, CatalogService catalog,
                    ApplicationEventPublisher events) {
        this.loads = loads;
        this.scans = scans;
        this.parcels = parcels;
        this.catalog = catalog;
        this.events = events;
    }

    /** Abre una sesión de carga. Si el repartidor ya tiene una abierta, se devuelve esa (idempotente). */
    @Transactional
    public LoadSummary open(OpenLoadRequest request) {
        AuthenticatedUser user = CurrentUser.get();
        UUID driverId = user.isDriver() ? user.driverId() : request.driverId();
        if (driverId == null) {
            throw new IllegalArgumentException("Indica el repartidor de la carga");
        }
        Optional<Load> open = loads.findFirstByDriverIdAndStatusOrderByOpenedAtDesc(driverId, Load.Status.OPEN);
        if (open.isPresent()) {
            return summary(open.get());
        }
        DriverSummary driver = catalog.requireDriver(driverId);
        UUID vehicleId = request.vehicleId() != null ? request.vehicleId() : driver.defaultVehicleId();
        if (vehicleId != null && !catalog.vehicleExists(vehicleId)) {
            throw new NotFoundException("Vehículo", vehicleId);
        }
        List<UUID> expected = parcels.findLoadableFor(driver.depotId(), driverId,
                catalog.zoneAreaOfDriver(driverId));
        Load load = loads.save(new Load(driverId, vehicleId, driver.depotId(), new LinkedHashSet<>(expected)));
        return summary(load);
    }

    @Transactional
    public ScanResponse scan(UUID loadId, ScanRequest request) {
        Optional<Scan> previous = scans.findByClientScanId(request.clientScanId());
        if (previous.isPresent()) {
            Scan s = previous.get();
            if (!s.getLoad().getId().equals(loadId)) {
                throw new BusinessException("scan-id-conflict", "Ese identificador de escaneo ya se usó en otra carga");
            }
            return toResponse(s, s.getLoad());
        }
        Load load = loads.findWithExpectedById(loadId).orElseThrow(() -> new NotFoundException("Carga", loadId));
        CurrentUser.checkDriverAccess(load.getDriverId());
        load.ensureOpen();

        String code = request.code().trim().toUpperCase(Locale.ROOT);
        Instant scannedAt = request.scannedAt() != null ? request.scannedAt() : Instant.now();
        Optional<ParcelInfo> parcel = parcels.findInfoByTrackingCode(code);
        Scan.Result result;
        UUID parcelId = parcel.map(ParcelInfo::id).orElse(null);
        if (parcel.isEmpty()) {
            result = Scan.Result.UNKNOWN;
        } else if (scans.existsByLoadIdAndParcelIdAndResult(loadId, parcelId, Scan.Result.OK)) {
            result = Scan.Result.DUPLICATE;
        } else if (isLoadable(load, parcel.get())) {
            result = Scan.Result.OK;
        } else {
            result = Scan.Result.NOT_EXPECTED;
        }
        Scan scan = scans.save(new Scan(load, request.clientScanId(), code, parcelId, result, scannedAt));
        return toResponse(scan, load);
    }

    /**
     * Un paquete esperado siempre se acepta. Uno no esperado se acepta si es del mismo almacén, está pendiente
     * y no está asignado a otro repartidor (por ejemplo, un paquete urgente que tráfico le da en mano).
     */
    private boolean isLoadable(Load load, ParcelInfo parcel) {
        if (load.getExpectedParcelIds().contains(parcel.id())) {
            return true;
        }
        boolean pending = "REGISTERED".equals(parcel.status()) || "FAILED_ATTEMPT".equals(parcel.status());
        boolean sameDepot = load.getDepotId().equals(parcel.depotId());
        boolean notOthers = parcel.assignedDriverId() == null || parcel.assignedDriverId().equals(load.getDriverId());
        return pending && sameDepot && notOthers;
    }

    @Transactional
    public List<ScanResponse> scanBatch(UUID loadId, List<ScanRequest> requests) {
        return requests.stream().map(r -> scan(loadId, r)).toList();
    }

    /** Cierra la carga y dispara la optimización (asíncrona: el resultado llega por WebSocket). */
    @Transactional
    public LoadSummary close(UUID loadId) {
        Load load = loads.findWithExpectedById(loadId).orElseThrow(() -> new NotFoundException("Carga", loadId));
        CurrentUser.checkDriverAccess(load.getDriverId());
        List<UUID> loaded = scans.findLoadedParcelIds(loadId);
        if (loaded.isEmpty()) {
            throw new BusinessException("empty-load", "No se puede cerrar una carga sin paquetes escaneados");
        }
        load.close();
        events.publishEvent(new ParcelsLoadedEvent(loadId, load.getDriverId(), loaded, CurrentUser.actorName()));
        events.publishEvent(new LoadClosedEvent(loadId, load.getDriverId(), load.getDepotId()));
        return summary(load);
    }

    public LoadSummary get(UUID loadId) {
        Load load = loads.findWithExpectedById(loadId).orElseThrow(() -> new NotFoundException("Carga", loadId));
        CurrentUser.checkDriverAccess(load.getDriverId());
        return summary(load);
    }

    public Optional<LoadSummary> current(UUID driverId) {
        return loads.findFirstByDriverIdOrderByOpenedAtDesc(driverId)
                .filter(l -> l.getStatus() == Load.Status.OPEN || isToday(l.getOpenedAt()))
                .map(l -> summary(loads.findWithExpectedById(l.getId()).orElseThrow()));
    }

    public List<LoadInfo> listToday() {
        Instant start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return loads.findByOpenedAtAfterOrderByOpenedAtDesc(start).stream().map(ScanningService::toInfo).toList();
    }

    // ---------- API para otros módulos ----------

    public LoadInfo requireLoad(UUID loadId) {
        return loads.findById(loadId).map(ScanningService::toInfo)
                .orElseThrow(() -> new NotFoundException("Carga", loadId));
    }

    public List<UUID> loadedParcelIds(UUID loadId) {
        return scans.findLoadedParcelIds(loadId);
    }

    // ---------- Internos ----------

    private LoadSummary summary(Load load) {
        List<Scan> all = scans.findByLoadIdOrderByScannedAtDesc(load.getId());
        Map<Scan.Result, Long> counts = all.stream().collect(Collectors.groupingBy(Scan::getResult,
                Collectors.counting()));
        Set<UUID> loaded = all.stream().filter(s -> s.getResult() == Scan.Result.OK).map(Scan::getParcelId)
                .collect(Collectors.toSet());
        Set<UUID> missingIds = load.getExpectedParcelIds().stream().filter(id -> !loaded.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, ParcelInfo> info = parcels.findInfo(missingIds).stream()
                .collect(Collectors.toMap(ParcelInfo::id, Function.identity()));
        List<MissingParcel> missing = missingIds.stream().map(info::get).filter(java.util.Objects::nonNull)
                .map(p -> new MissingParcel(p.id(), p.trackingCode(), p.addressLine())).toList();
        int expected = expectedCount(load, loaded);
        List<ScanResponse> recent = all.stream().limit(20)
                .map(s -> toResponse(s, s.getResult(), loaded.size(), expected)).toList();
        return new LoadSummary(load.getId(), load.getDriverId(), load.getVehicleId(), load.getDepotId(),
                load.getStatus().name(), load.getOpenedAt(), load.getClosedAt(), expected, loaded.size(),
                counts.getOrDefault(Scan.Result.DUPLICATE, 0L).intValue(),
                counts.getOrDefault(Scan.Result.UNKNOWN, 0L).intValue(),
                counts.getOrDefault(Scan.Result.NOT_EXPECTED, 0L).intValue(),
                missing, recent);
    }

    /** Esperados = los previstos al abrir + los extra aceptados durante la carga. */
    private static int expectedCount(Load load, Set<UUID> loaded) {
        Set<UUID> all = new LinkedHashSet<>(load.getExpectedParcelIds());
        all.addAll(loaded);
        return all.size();
    }

    private ScanResponse toResponse(Scan scan, Load load) {
        Set<UUID> loaded = Set.copyOf(scans.findLoadedParcelIds(load.getId()));
        Load withExpected = loads.findWithExpectedById(load.getId()).orElse(load);
        return toResponse(scan, scan.getResult(), loaded.size(), expectedCount(withExpected, loaded));
    }

    private static ScanResponse toResponse(Scan s, Scan.Result result, int scanned, int expected) {
        String message = switch (result) {
            case OK -> "Paquete cargado";
            case DUPLICATE -> "Este paquete ya estaba escaneado";
            case UNKNOWN -> "Código desconocido";
            case NOT_EXPECTED -> "Este paquete no es de tu reparto";
        };
        return new ScanResponse(s.getId(), s.getClientScanId(), s.getCode(), result.name(), s.getParcelId(), message,
                s.getScannedAt(), scanned, expected);
    }

    private static LoadInfo toInfo(Load l) {
        return new LoadInfo(l.getId(), l.getDriverId(), l.getVehicleId(), l.getDepotId(), l.getStatus().name(),
                l.getClosedAt());
    }

    private static boolean isToday(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now());
    }
}
