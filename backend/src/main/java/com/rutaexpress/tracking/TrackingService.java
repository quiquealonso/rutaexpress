package com.rutaexpress.tracking;

import com.rutaexpress.catalog.CatalogDtos.DriverSummary;
import com.rutaexpress.catalog.CatalogService;
import com.rutaexpress.routing.RouteService;
import com.rutaexpress.shared.events.DomainEvents.RouteCompletedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteDeviationDetectedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteOptimizedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteStartedEvent;
import com.rutaexpress.shared.geo.GeoPoint;
import com.rutaexpress.shared.geo.Geometries;
import com.rutaexpress.tracking.TrackingDtos.LiveDriver;
import com.rutaexpress.tracking.TrackingDtos.PositionResponse;
import com.rutaexpress.tracking.TrackingDtos.PositionSample;
import com.rutaexpress.tracking.TrackingDtos.RealtimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Posiciones GPS: se guardan en lote, se emiten por WebSocket al panel y se comparan con la ruta prevista
 * para detectar desvíos (varias posiciones seguidas lejos de la polilínea).
 */
@Service
public class TrackingService {

    private static final Duration LATEST_MAX_AGE = Duration.ofHours(12);

    private final DriverPositionRepository positions;
    private final RouteService routes;
    private final CatalogService catalog;
    private final SimpMessagingTemplate messaging;
    private final ApplicationEventPublisher events;
    private final double deviationMeters;
    private final int deviationSamples;

    /** Caché en memoria de la última posición y del estado de desvío de cada repartidor. */
    private final Map<UUID, PositionResponse> latest = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> offRouteCount = new ConcurrentHashMap<>();
    private final Map<UUID, RouteShape> routeShapes = new ConcurrentHashMap<>();

    private record RouteShape(UUID routeId, List<GeoPoint> line) {
    }

    TrackingService(DriverPositionRepository positions, RouteService routes, CatalogService catalog,
                    SimpMessagingTemplate messaging, ApplicationEventPublisher events,
                    @Value("${rutaexpress.tracking.deviation-meters:350}") double deviationMeters,
                    @Value("${rutaexpress.tracking.deviation-samples:3}") int deviationSamples) {
        this.positions = positions;
        this.routes = routes;
        this.catalog = catalog;
        this.messaging = messaging;
        this.events = events;
        this.deviationMeters = deviationMeters;
        this.deviationSamples = deviationSamples;
    }

    @Transactional
    public int record(UUID driverId, List<PositionSample> samples) {
        Instant now = Instant.now();
        List<DriverPosition> entities = samples.stream()
                // Se descartan posiciones con fecha futura (reloj del móvil desajustado) o muy antiguas
                .filter(s -> !s.recordedAt().isAfter(now.plusSeconds(60))
                        && s.recordedAt().isAfter(now.minus(Duration.ofDays(2))))
                .sorted(Comparator.comparing(PositionSample::recordedAt))
                .map(s -> new DriverPosition(driverId, Geometries.point(s.lat(), s.lon()), s.speedKmh(), s.heading(),
                        s.accuracyMeters(), s.recordedAt()))
                .toList();
        if (entities.isEmpty()) {
            return 0;
        }
        positions.saveAll(entities);
        DriverPosition last = entities.getLast();
        PositionResponse response = toResponse(last);
        PositionResponse previous = latest.get(driverId);
        if (previous == null || !previous.recordedAt().isAfter(last.getRecordedAt())) {
            latest.put(driverId, response);
            messaging.convertAndSend("/topic/positions", new RealtimeMessage("POSITION", response, now));
        }
        checkDeviation(driverId, entities);
        return entities.size();
    }

    private void checkDeviation(UUID driverId, List<DriverPosition> batch) {
        RouteShape shape = routeShapes.computeIfAbsent(driverId, id -> routes.currentRouteId(id)
                .flatMap(routeId -> routes.activeGeometry(routeId).map(line -> new RouteShape(routeId, line)))
                .orElse(new RouteShape(null, List.of())));
        if (shape.routeId() == null || shape.line().isEmpty()) {
            return;
        }
        for (DriverPosition p : batch) {
            // Con poca precisión GPS no se decide nada
            if (p.getAccuracyMeters() != null && p.getAccuracyMeters() > 100) {
                continue;
            }
            GeoPoint point = Geometries.toGeoPoint(p.getLocation());
            double distance = Geometries.distanceToLineMeters(point, shape.line());
            if (distance > deviationMeters) {
                int count = offRouteCount.merge(driverId, 1, Integer::sum);
                if (count >= deviationSamples) {
                    offRouteCount.put(driverId, 0);
                    events.publishEvent(new RouteDeviationDetectedEvent(shape.routeId(), driverId, point.lat(),
                            point.lon(), distance));
                }
            } else {
                offRouteCount.put(driverId, 0);
            }
        }
    }

    /** Al recalcular, iniciar o terminar una ruta se invalida la geometría en caché. */
    @TransactionalEventListener(fallbackExecution = true)
    void on(RouteOptimizedEvent event) {
        invalidate(event.driverId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RouteStartedEvent event) {
        invalidate(event.driverId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RouteCompletedEvent event) {
        invalidate(event.driverId());
    }

    private void invalidate(UUID driverId) {
        routeShapes.remove(driverId);
        offRouteCount.remove(driverId);
    }

    @Transactional(readOnly = true)
    public Optional<PositionResponse> latestPosition(UUID driverId) {
        PositionResponse cached = latest.get(driverId);
        if (cached != null && cached.recordedAt().isAfter(Instant.now().minus(LATEST_MAX_AGE))) {
            return Optional.of(cached);
        }
        return positions.findLatest(driverId, Instant.now().minus(LATEST_MAX_AGE)).map(TrackingService::toResponse);
    }

    public Optional<GeoPoint> latestPoint(UUID driverId) {
        return latestPosition(driverId).map(PositionResponse::location);
    }

    /** Velocidad actual: la app solo muestra el chat a pantalla completa con el vehículo parado. */
    public boolean isStationary(UUID driverId) {
        return latestPosition(driverId)
                .map(p -> p.speedKmh() == null || p.speedKmh() < 3)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<LiveDriver> liveDrivers() {
        List<DriverPosition> all = positions.findLatestOfAll(Instant.now().minus(LATEST_MAX_AGE));
        Map<UUID, DriverSummary> drivers = catalog.findDrivers(all.stream().map(DriverPosition::getDriverId)
                .collect(Collectors.toSet()));
        return all.stream().map(p -> {
            DriverSummary d = drivers.get(p.getDriverId());
            return new LiveDriver(p.getDriverId(), d == null ? "?" : d.name(),
                    Geometries.toGeoPoint(p.getLocation()), p.getSpeedKmh(), p.getRecordedAt(),
                    routes.currentRouteId(p.getDriverId()).orElse(null));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> trail(UUID driverId, Instant since) {
        return positions.findTrail(driverId, since).stream().map(TrackingService::toResponse).toList();
    }

    private static PositionResponse toResponse(DriverPosition p) {
        return new PositionResponse(p.getDriverId(), Geometries.toGeoPoint(p.getLocation()), p.getSpeedKmh(),
                p.getHeading(), p.getAccuracyMeters(), p.getRecordedAt());
    }
}
