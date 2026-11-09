package com.rutaexpress.routing;

import com.rutaexpress.catalog.CatalogDtos.DepotSummary;
import com.rutaexpress.catalog.CatalogDtos.DriverSummary;
import com.rutaexpress.catalog.CatalogService;
import com.rutaexpress.geo.GeoModels.DistanceMatrix;
import com.rutaexpress.geo.GeoModels.RouteGeometry;
import com.rutaexpress.geo.RoutingEngineService;
import com.rutaexpress.parcel.ParcelDtos.ParcelInfo;
import com.rutaexpress.parcel.ParcelService;
import com.rutaexpress.routing.RoutingDtos.AlgorithmComparison;
import com.rutaexpress.routing.RoutingDtos.ComparisonResponse;
import com.rutaexpress.routing.RoutingDtos.ParcelBrief;
import com.rutaexpress.routing.RoutingDtos.ParcelEta;
import com.rutaexpress.routing.RoutingDtos.RouteDetail;
import com.rutaexpress.routing.RoutingDtos.RouteListItem;
import com.rutaexpress.routing.RoutingDtos.RouteSummary;
import com.rutaexpress.routing.RoutingDtos.RouteVersionResponse;
import com.rutaexpress.routing.RoutingDtos.StopDetail;
import com.rutaexpress.routing.optimizer.OptimizationProblem;
import com.rutaexpress.routing.optimizer.OptimizerRegistry;
import com.rutaexpress.scanning.ScanningDtos.LoadInfo;
import com.rutaexpress.scanning.ScanningService;
import com.rutaexpress.shared.error.BusinessException;
import com.rutaexpress.shared.error.NotFoundException;
import com.rutaexpress.shared.events.DomainEvents.RouteCompletedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteStartedEvent;
import com.rutaexpress.shared.events.DomainEvents.StopStatusChangedEvent;
import com.rutaexpress.shared.events.EventSource;
import com.rutaexpress.shared.geo.GeoPoint;
import com.rutaexpress.shared.geo.Geometries;
import com.rutaexpress.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** API pública del módulo routing: planificación, consultas y cambios de estado de las paradas. */
@Service
@Transactional(readOnly = true)
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);
    private static final Set<Route.Status> ACTIVE = EnumSet.of(Route.Status.PLANNED, Route.Status.IN_PROGRESS);

    private final RouteRepository routes;
    private final StopRepository stops;
    private final RouteVersionRepository versions;
    private final RoutePlanner planner;
    private final ScanningService scanning;
    private final ParcelService parcels;
    private final CatalogService catalog;
    private final RoutingEngineService engine;
    private final OptimizerRegistry optimizers;
    private final RoutingProperties properties;
    private final ApplicationEventPublisher events;

    RouteService(RouteRepository routes, StopRepository stops, RouteVersionRepository versions, RoutePlanner planner,
                 ScanningService scanning, ParcelService parcels, CatalogService catalog,
                 RoutingEngineService engine, OptimizerRegistry optimizers, RoutingProperties properties,
                 ApplicationEventPublisher events) {
        this.routes = routes;
        this.stops = stops;
        this.versions = versions;
        this.planner = planner;
        this.scanning = scanning;
        this.parcels = parcels;
        this.catalog = catalog;
        this.engine = engine;
        this.optimizers = optimizers;
        this.properties = properties;
        this.events = events;
    }

    // ---------- Planificación ----------

    /** Crea la ruta de una carga cerrada: agrupa por dirección, optimiza y guarda la versión 1. */
    @Transactional
    public Optional<UUID> planForLoad(UUID loadId) {
        if (routes.existsByLoadId(loadId)) {
            log.info("La carga {} ya tiene ruta; se ignora", loadId);
            return routes.findByLoadId(loadId).map(Route::getId);
        }
        LoadInfo load = scanning.requireLoad(loadId);
        DepotSummary depot = catalog.requireDepot(load.depotId());
        List<ParcelInfo> loaded = parcels.findInfo(scanning.loadedParcelIds(loadId));

        Route route = new Route(loadId, load.driverId(), load.depotId(), Geometries.point(depot.location()),
                properties.returnToDepot(), properties.algorithm());
        // Agrupar: los paquetes con la misma dirección forman una sola parada (se respeta el orden de escaneo)
        Map<UUID, List<ParcelInfo>> byAddress = new LinkedHashMap<>();
        List<String> withoutLocation = new ArrayList<>();
        for (ParcelInfo p : loaded) {
            if (p.location() == null) {
                withoutLocation.add(p.trackingCode());
                continue;
            }
            byAddress.computeIfAbsent(p.addressId(), k -> new ArrayList<>()).add(p);
        }
        if (!withoutLocation.isEmpty()) {
            log.warn("Paquetes sin coordenadas excluidos de la ruta {}: {}", loadId, withoutLocation);
        }
        if (byAddress.isEmpty()) {
            throw new BusinessException("no-locatable-parcels", "Ningún paquete de la carga tiene dirección geocodificada");
        }
        int seq = 1;
        for (List<ParcelInfo> group : byAddress.values()) {
            ParcelInfo first = group.getFirst();
            Stop stop = new Stop(route, seq++, first.addressId(), first.addressLine(), Geometries.point(first.location()),
                    planner.serviceSecondsFor(group.size()));
            group.forEach(p -> stop.getParcelIds().add(p.id()));
            stop.setWindow(
                    group.stream().map(ParcelInfo::windowStart).reduce(null, RoutePlanner::tightestStart),
                    group.stream().map(ParcelInfo::windowEnd).reduce(null, RoutePlanner::tightestEnd));
            route.addStop(stop);
        }
        routes.save(route);
        planner.plan(route, depot.location(), "INITIAL");
        return Optional.of(route.getId());
    }

    /** Recalcula desde la posición actual. Solo reordena las paradas abiertas; las cerradas no se tocan. */
    @Transactional
    public RouteDetail reoptimize(UUID routeId, GeoPoint currentPosition, String reason) {
        Route route = detailedRoute(routeId);
        CurrentUser.checkDriverAccess(route.getDriverId());
        route.ensureActive();
        planner.plan(route, startPointFor(route, currentPosition), reason);
        return toDetail(route);
    }

    /** Recálculo automático (desvío detectado): respeta un intervalo mínimo para no recalcular en bucle. */
    @Transactional
    public void reoptimizeIfDue(UUID routeId, GeoPoint position, String reason) {
        Route route = detailedRoute(routeId);
        if (!route.isActive()) {
            return;
        }
        Instant last = route.getLastReoptimizedAt();
        if (last != null && last.plus(properties.minReoptimizeInterval()).isAfter(Instant.now())) {
            return;
        }
        planner.plan(route, position, reason);
    }

    private GeoPoint startPointFor(Route route, GeoPoint currentPosition) {
        if (currentPosition != null) {
            return currentPosition;
        }
        // Sin GPS: desde la última parada cerrada o, si no hay, desde el almacén
        return route.getStops().stream().filter(s -> !s.getStatus().isOpen() && s.getCompletedAt() != null)
                .max(Comparator.comparing(Stop::getCompletedAt))
                .map(s -> Geometries.toGeoPoint(s.getLocation()))
                .orElse(Geometries.toGeoPoint(route.getDepotLocation()));
    }

    /** Comparativa de algoritmos sobre las paradas abiertas, frente al orden de escaneo. */
    public ComparisonResponse compare(UUID routeId) {
        Route route = detailedRoute(routeId);
        List<Stop> open = route.getStops().stream().filter(s -> s.getStatus().isOpen())
                .sorted(Comparator.comparingInt(Stop::getSequence)).toList();
        // Referencia: visitar las paradas en el orden en que se escanearon los paquetes
        List<Stop> scanOrder = open.stream().sorted(Comparator.comparingInt(Stop::getScanOrder)).toList();
        List<GeoPoint> points = new ArrayList<>();
        points.add(startPointFor(route, null));
        open.forEach(s -> points.add(Geometries.toGeoPoint(s.getLocation())));
        Integer end = null;
        if (route.isReturnToDepot()) {
            end = points.size();
            points.add(Geometries.toGeoPoint(route.getDepotLocation()));
        }
        DistanceMatrix matrix = engine.matrix(points);
        List<Integer> nodes = new ArrayList<>();
        for (int i = 1; i <= open.size(); i++) {
            nodes.add(i);
        }
        OptimizationProblem problem = new OptimizationProblem(matrix.seconds(), 0, end, nodes, null, null,
                properties.timeLimit());
        List<Integer> scanNodes = scanOrder.stream().map(s -> open.indexOf(s) + 1).toList();
        long scanCost = problem.tourCost(scanNodes);
        List<AlgorithmComparison> results = optimizers.compareAll(problem).stream()
                .map(r -> new AlgorithmComparison(r.algorithm(), r.costSeconds(), r.computeMillis(),
                        scanCost - r.costSeconds(),
                        scanCost == 0 ? 0 : Math.round(1000.0 * (scanCost - r.costSeconds()) / scanCost) / 10.0))
                .toList();
        return new ComparisonResponse(routeId, open.size(), matrix.source(), scanCost, results);
    }

    // ---------- Cambios de estado de las paradas ----------

    @Transactional
    public RouteDetail start(UUID routeId) {
        Route route = detailedRoute(routeId);
        CurrentUser.checkDriverAccess(route.getDriverId());
        boolean wasPlanned = route.getStatus() == Route.Status.PLANNED;
        route.start(Instant.now());
        if (wasPlanned) {
            planner.refreshEtas(route, Instant.now());
            catalog.markOnRoute(route.getDriverId(), true);
            events.publishEvent(new RouteStartedEvent(route.getId(), route.getDriverId(), allParcelIds(route),
                    CurrentUser.actorName()));
        }
        return toDetail(route);
    }

    @Transactional
    public StopDetail markDelivered(UUID stopId, EventSource source) {
        Stop stop = openStopForCurrentUser(stopId);
        Instant now = Instant.now();
        stop.getRoute().start(now);
        stop.markDelivered(now);
        afterStopClosed(stop, source, null, now);
        return toStopDetail(stop, parcelBriefs(stop.getParcelIds()));
    }

    @Transactional
    public StopDetail markFailed(UUID stopId, String reason, boolean retryLater, EventSource source,
                                 GeoPoint position) {
        Stop stop = openStopForCurrentUser(stopId);
        Instant now = Instant.now();
        Route route = stop.getRoute();
        route.start(now);
        if (retryLater) {
            // Ausente pero se reintenta: la parada pasa al final y se recalcula el resto
            stop.defer(reason, true);
            publishStopChange(stop, source, reason);
            planner.plan(route, position != null ? position : Geometries.toGeoPoint(stop.getLocation()),
                    "RETRY_LATER");
        } else {
            stop.markFailed(reason, now);
            afterStopClosed(stop, source, reason, now);
        }
        return toStopDetail(stop, parcelBriefs(stop.getParcelIds()));
    }

    @Transactional
    public RouteDetail skip(UUID stopId, EventSource source, GeoPoint position) {
        Stop stop = openStopForCurrentUser(stopId);
        stop.defer("Saltada por el repartidor", false);
        Route route = stop.getRoute();
        route.start(Instant.now());
        publishStopChange(stop, source, "SKIPPED");
        planner.plan(route, startPointFor(route, position), "SKIP");
        return toDetail(route);
    }

    private void afterStopClosed(Stop stop, EventSource source, String reason, Instant now) {
        Route route = stop.getRoute();
        publishStopChange(stop, source, reason);
        boolean anyOpen = route.getStops().stream().anyMatch(s -> s.getStatus().isOpen());
        if (anyOpen) {
            planner.refreshEtas(route, now);
        } else {
            route.complete(now);
            catalog.markOnRoute(route.getDriverId(), false);
            int delivered = (int) route.getStops().stream().filter(s -> s.getStatus() == Stop.Status.DELIVERED).count();
            events.publishEvent(new RouteCompletedEvent(route.getId(), route.getDriverId(), delivered,
                    route.getStops().size() - delivered));
        }
    }

    private void publishStopChange(Stop stop, EventSource source, String reason) {
        events.publishEvent(new StopStatusChangedEvent(stop.getRoute().getId(), stop.getId(),
                stop.getRoute().getDriverId(), stop.getStatus().name(), reason, List.copyOf(stop.getParcelIds()),
                source, CurrentUser.actorName()));
    }

    private Stop openStopForCurrentUser(UUID stopId) {
        Stop stop = stops.findDetailedById(stopId).orElseThrow(() -> new NotFoundException("Parada", stopId));
        CurrentUser.checkDriverAccess(stop.getRoute().getDriverId());
        stop.getRoute().ensureActive();
        // Carga las paradas hermanas para poder recalcular ETA y detectar el fin de ruta
        detailedRoute(stop.getRoute().getId());
        return stop;
    }

    // ---------- Consultas ----------

    public RouteDetail get(UUID routeId) {
        Route route = detailedRoute(routeId);
        CurrentUser.checkDriverAccess(route.getDriverId());
        return toDetail(route);
    }

    /** Ruta activa del repartidor o, si no hay, la última de hoy. */
    public Optional<RouteDetail> currentForDriver(UUID driverId) {
        return currentRouteId(driverId).map(id -> toDetail(detailedRoute(id)));
    }

    public Optional<UUID> currentRouteId(UUID driverId) {
        Optional<Route> active = routes.findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(driverId, ACTIVE);
        if (active.isPresent()) {
            return active.map(Route::getId);
        }
        return routes.findFirstByDriverIdOrderByCreatedAtDesc(driverId)
                .filter(r -> isToday(r.getCreatedAt()))
                .map(Route::getId);
    }

    public Optional<StopDetail> nextStop(UUID routeId) {
        Route route = detailedRoute(routeId);
        CurrentUser.checkDriverAccess(route.getDriverId());
        return route.getStops().stream().filter(s -> s.getStatus().isOpen())
                .min(Comparator.comparingInt(Stop::getSequence))
                .map(s -> toStopDetail(s, parcelBriefs(s.getParcelIds())));
    }

    public List<StopDetail> pendingStops(UUID routeId, int limit) {
        RouteDetail detail = get(routeId);
        return detail.stops().stream().filter(s -> "PENDING".equals(s.status()) || "SKIPPED".equals(s.status()))
                .limit(limit <= 0 ? Integer.MAX_VALUE : limit).toList();
    }

    public UUID driverIdOfStop(UUID stopId) {
        Stop stop = stops.findDetailedById(stopId).orElseThrow(() -> new NotFoundException("Parada", stopId));
        CurrentUser.checkDriverAccess(stop.getRoute().getDriverId());
        return stop.getRoute().getDriverId();
    }

    public StopDetail getStop(UUID stopId) {
        Stop stop = stops.findDetailedById(stopId).orElseThrow(() -> new NotFoundException("Parada", stopId));
        CurrentUser.checkDriverAccess(stop.getRoute().getDriverId());
        return toStopDetail(stop, parcelBriefs(stop.getParcelIds()));
    }

    public RouteSummary summary(UUID routeId) {
        Route route = detailedRoute(routeId);
        CurrentUser.checkDriverAccess(route.getDriverId());
        int delivered = 0, failed = 0, pending = 0, parcelsPending = 0;
        long remainingDistance = 0, remainingDuration = 0;
        for (Stop s : route.getStops()) {
            switch (s.getStatus()) {
                case DELIVERED -> delivered++;
                case FAILED -> failed++;
                default -> {
                    pending++;
                    parcelsPending += s.getParcelIds().size();
                    remainingDistance += s.getLegDistanceMeters();
                    remainingDuration += s.getLegDurationSeconds() + s.getServiceSeconds();
                }
            }
        }
        return new RouteSummary(routeId, route.getStatus().name(), route.getStops().size(), delivered, failed, pending,
                parcelsPending, remainingDistance, remainingDuration, route.getEstimatedEnd());
    }

    /** Indicaciones paso a paso desde una posición hasta una parada (la siguiente si no se indica). */
    public RouteGeometry directions(UUID routeId, UUID stopId, GeoPoint from) {
        Route route = detailedRoute(routeId);
        CurrentUser.checkDriverAccess(route.getDriverId());
        Stop target = stopId == null
                ? route.getStops().stream().filter(s -> s.getStatus().isOpen())
                .min(Comparator.comparingInt(Stop::getSequence))
                .orElseThrow(() -> new BusinessException("no-pending-stops", "No quedan paradas pendientes"))
                : route.getStops().stream().filter(s -> s.getId().equals(stopId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Parada", stopId));
        GeoPoint origin = from != null ? from : startPointFor(route, null);
        return engine.route(List.of(origin, Geometries.toGeoPoint(target.getLocation())), true);
    }

    public List<RouteVersionResponse> versions(UUID routeId) {
        Route route = routes.findById(routeId).orElseThrow(() -> new NotFoundException("Ruta", routeId));
        CurrentUser.checkDriverAccess(route.getDriverId());
        return versions.findByRouteIdOrderByVersionAsc(routeId).stream()
                .map(v -> new RouteVersionResponse(v.getId(), v.getVersion(), v.getAlgorithm(), v.getReason(),
                        v.getTotalDistanceMeters(), v.getTotalDurationSeconds(), v.getComputeMillis(),
                        v.getMatrixSource(), v.getCreatedAt(), v.getStopOrder(),
                        Geometries.toGeoPoints(v.getGeometry())))
                .toList();
    }

    public List<RouteListItem> listToday() {
        Instant start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Route> list = new ArrayList<>(routes.findByCreatedAtAfterOrderByCreatedAtDesc(start));
        // También las activas de días anteriores (repartos que no se cerraron)
        routes.findByStatusIn(ACTIVE).stream().filter(r -> !list.contains(r)).forEach(list::add);
        Map<UUID, DriverSummary> drivers = catalog.findDrivers(list.stream().map(Route::getDriverId).toList());
        return list.stream().map(r -> {
            Route d = detailedRoute(r.getId());
            int delivered = 0, failed = 0, pending = 0;
            for (Stop s : d.getStops()) {
                switch (s.getStatus()) {
                    case DELIVERED -> delivered++;
                    case FAILED -> failed++;
                    default -> pending++;
                }
            }
            DriverSummary driver = drivers.get(r.getDriverId());
            return new RouteListItem(r.getId(), r.getDriverId(), driver == null ? "?" : driver.name(),
                    r.getStatus().name(), r.getCurrentVersion(), r.getAlgorithm(), d.getStops().size(), delivered,
                    failed, pending, r.getTotalDistanceMeters(), r.getEstimatedEnd(), r.getStartedAt());
        }).toList();
    }

    /** Indicadores del día para el dashboard. El tiempo medio por parada sale de las entregas reales. */
    public RoutingDtos.DayStats dayStats() {
        Instant start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Route> today = routes.findByCreatedAtAfterOrderByCreatedAtDesc(start);
        int inProgress = 0, completed = 0, delivered = 0, failed = 0;
        long totalGap = 0, gaps = 0;
        for (Route r : today) {
            if (r.getStatus() == Route.Status.IN_PROGRESS) inProgress++;
            if (r.getStatus() == Route.Status.COMPLETED) completed++;
            List<Instant> times = new ArrayList<>();
            if (r.getStartedAt() != null) {
                times.add(r.getStartedAt());
            }
            for (Stop s : detailedRoute(r.getId()).getStops()) {
                if (s.getStatus() == Stop.Status.DELIVERED) delivered++;
                if (s.getStatus() == Stop.Status.FAILED) failed++;
                if (s.getCompletedAt() != null) {
                    times.add(s.getCompletedAt());
                }
            }
            times.sort(Comparator.naturalOrder());
            for (int i = 1; i < times.size(); i++) {
                totalGap += java.time.Duration.between(times.get(i - 1), times.get(i)).toSeconds();
                gaps++;
            }
        }
        return new RoutingDtos.DayStats(today.size(), inProgress, completed, delivered, failed,
                gaps == 0 ? 0 : totalGap / gaps);
    }

    /** Geometría y estado de la ruta activa (para detectar desvíos sin cargar todo el detalle). */
    public Optional<List<GeoPoint>> activeGeometry(UUID routeId) {
        return routes.findById(routeId).filter(Route::isActive)
                .map(r -> Geometries.toGeoPoints(r.getGeometry()));
    }

    /** ETA de un paquete en una ruta activa: cuántas paradas faltan antes de la suya. */
    public Optional<ParcelEta> etaForParcel(UUID parcelId) {
        List<Stop> found = stops.findByParcelInRoutes(parcelId,
                EnumSet.of(Route.Status.PLANNED, Route.Status.IN_PROGRESS, Route.Status.COMPLETED));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Stop stop = found.getFirst();
        Route route = detailedRoute(stop.getRoute().getId());
        int before = (int) route.getStops().stream()
                .filter(s -> s.getStatus().isOpen() && s.getSequence() < stop.getSequence()).count();
        return Optional.of(new ParcelEta(route.getId(), route.getDriverId(), stop.getStatus().name(),
                stop.getStatus().isOpen() ? stop.getEta() : stop.getCompletedAt(), before, route.getStatus().name()));
    }

    // ---------- Mapeo ----------

    private Route detailedRoute(UUID routeId) {
        return routes.findDetailedById(routeId).orElseThrow(() -> new NotFoundException("Ruta", routeId));
    }

    private static List<UUID> allParcelIds(Route route) {
        return route.getStops().stream().flatMap(s -> s.getParcelIds().stream()).toList();
    }

    private Map<UUID, ParcelBrief> parcelBriefs(Collection<UUID> ids) {
        return parcels.findInfo(ids).stream()
                .map(p -> new ParcelBrief(p.id(), p.trackingCode(), p.recipientName(), p.recipientPhone(), p.notes()))
                .collect(Collectors.toMap(ParcelBrief::id, Function.identity()));
    }

    private RouteDetail toDetail(Route route) {
        Map<UUID, ParcelBrief> briefs = parcelBriefs(allParcelIds(route));
        List<StopDetail> stopDetails = route.getStops().stream()
                .sorted(Comparator.comparingInt(Stop::getSequence))
                .map(s -> toStopDetail(s, briefs))
                .toList();
        return new RouteDetail(route.getId(), route.getLoadId(), route.getDriverId(), route.getDepotId(),
                Geometries.toGeoPoint(route.getDepotLocation()), route.getStatus().name(), route.getCurrentVersion(),
                route.getAlgorithm(), route.getTotalDistanceMeters(), route.getTotalDurationSeconds(),
                route.getEstimatedEnd(), route.getStartedAt(), route.getCompletedAt(),
                Geometries.toGeoPoints(route.getGeometry()), stopDetails);
    }

    private static StopDetail toStopDetail(Stop s, Map<UUID, ParcelBrief> briefs) {
        List<ParcelBrief> list = s.getParcelIds().stream().map(briefs::get).filter(java.util.Objects::nonNull).toList();
        return new StopDetail(s.getId(), s.getRoute().getId(), s.getSequence(), s.getStatus().name(), s.getEta(),
                s.getPlannedEta(), s.getAddressLine(), Geometries.toGeoPoint(s.getLocation()), list,
                s.getWindowStart(), s.getWindowEnd(), s.getFailReason(), s.getAttempts(), s.getCompletedAt(),
                s.getLegDistanceMeters(), s.getLegDurationSeconds());
    }

    private static boolean isToday(Instant instant) {
        return instant != null && instant.atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now());
    }
}
