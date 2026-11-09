package com.rutaexpress.routing;

import com.rutaexpress.geo.GeoModels.DistanceMatrix;
import com.rutaexpress.geo.GeoModels.RouteGeometry;
import com.rutaexpress.geo.GeoModels.RouteLeg;
import com.rutaexpress.geo.RoutingEngineService;
import com.rutaexpress.routing.optimizer.OptimizationProblem;
import com.rutaexpress.routing.optimizer.OptimizerRegistry;
import com.rutaexpress.shared.events.DomainEvents.RouteOptimizedEvent;
import com.rutaexpress.shared.geo.GeoPoint;
import com.rutaexpress.shared.geo.Geometries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Calcula (o recalcula) el orden de las paradas abiertas de una ruta:
 * matriz de tiempos → optimización → geometría y ETA → nueva RouteVersion.
 * Las paradas ya cerradas no se tocan; las saltadas se dejan para el final.
 */
@Component
class RoutePlanner {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanner.class);

    private final RoutingEngineService engine;
    private final OptimizerRegistry optimizers;
    private final RouteVersionRepository versions;
    private final RoutingProperties properties;
    private final ApplicationEventPublisher events;

    RoutePlanner(RoutingEngineService engine, OptimizerRegistry optimizers, RouteVersionRepository versions,
                 RoutingProperties properties, ApplicationEventPublisher events) {
        this.engine = engine;
        this.optimizers = optimizers;
        this.versions = versions;
        this.properties = properties;
        this.events = events;
    }

    /**
     * @param start  punto de salida: el almacén al planificar, la posición GPS actual al recalcular
     * @param reason INITIAL, REOPTIMIZE, SKIP, RETRY_LATER, DEVIATION, URGENT_PARCEL...
     */
    void plan(Route route, GeoPoint start, String reason) {
        Instant now = Instant.now();
        List<Stop> closed = route.getStops().stream().filter(s -> !s.getStatus().isOpen())
                .sorted(Comparator.comparing(Stop::getCompletedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Stop> pending = route.getStops().stream().filter(s -> s.getStatus() == Stop.Status.PENDING)
                .sorted(Comparator.comparingInt(Stop::getSequence)).toList();
        List<Stop> deferred = route.getStops().stream().filter(s -> s.getStatus() == Stop.Status.SKIPPED)
                .sorted(Comparator.comparingInt(Stop::getSequence)).toList();

        GeoPoint depot = Geometries.toGeoPoint(route.getDepotLocation());
        List<Stop> open = new ArrayList<>(pending);
        open.addAll(deferred);

        // Nodos de la matriz: 0 = salida, 1..n = paradas abiertas, n+1 = almacén (si se vuelve)
        List<GeoPoint> points = new ArrayList<>();
        points.add(start);
        open.forEach(s -> points.add(Geometries.toGeoPoint(s.getLocation())));
        Integer endNode = null;
        if (route.isReturnToDepot()) {
            endNode = points.size();
            points.add(depot);
        }

        List<Stop> ordered = new ArrayList<>();
        String algorithm = route.getAlgorithm();
        long computeMillis = 0;
        String matrixSource = "NONE";
        if (!open.isEmpty()) {
            DistanceMatrix matrix = engine.matrix(points);
            matrixSource = matrix.source();
            long[] service = new long[points.size()];
            long[][] windows = new long[points.size()][];
            for (int i = 0; i < open.size(); i++) {
                service[i + 1] = open.get(i).getServiceSeconds();
                windows[i + 1] = window(open.get(i), now);
            }
            // 1) Paradas pendientes desde la salida
            List<Integer> pendingNodes = range(1, pending.size());
            OptimizerRegistry.Result first = optimizers.optimize(new OptimizationProblem(matrix.seconds(), 0,
                    deferred.isEmpty() ? endNode : null, pendingNodes, service, windows,
                    properties.timeLimit()), properties.algorithm());
            algorithm = first.algorithm();
            computeMillis += first.computeMillis();
            first.order().forEach(i -> ordered.add(open.get(i - 1)));
            // 2) Las saltadas, al final, partiendo de la última pendiente
            if (!deferred.isEmpty()) {
                int from = first.order().isEmpty() ? 0 : first.order().getLast();
                List<Integer> deferredNodes = range(pending.size() + 1, deferred.size());
                OptimizerRegistry.Result second = optimizers.optimize(new OptimizationProblem(matrix.seconds(), from,
                        endNode, deferredNodes, service, windows, properties.timeLimit()), properties.algorithm());
                computeMillis += second.computeMillis();
                second.order().forEach(i -> ordered.add(open.get(i - 1)));
            }
        }

        // Geometría y ETA sobre el orden final
        List<GeoPoint> visit = new ArrayList<>();
        visit.add(start);
        ordered.forEach(s -> visit.add(Geometries.toGeoPoint(s.getLocation())));
        if (route.isReturnToDepot() && !ordered.isEmpty()) {
            visit.add(depot);
        }
        RouteGeometry geometry = engine.route(visit, false);

        int sequence = 1;
        for (Stop s : closed) {
            s.plan(sequence++, s.getEta(), s.getLegDurationSeconds(), s.getLegDistanceMeters());
        }
        Instant t = now;
        for (int i = 0; i < ordered.size(); i++) {
            Stop stop = ordered.get(i);
            RouteLeg leg = i < geometry.legs().size() ? geometry.legs().get(i) : new RouteLeg(0, 0, List.of());
            t = t.plusSeconds(leg.durationSeconds());
            stop.plan(sequence++, t, leg.durationSeconds(), leg.distanceMeters());
            long[] w = window(stop, now);
            if (w != null && t.isBefore(now.plusSeconds(w[0]))) {
                t = now.plusSeconds(w[0]); // llega antes de la franja: espera
            }
            t = t.plusSeconds(stop.getServiceSeconds());
        }
        if (route.isReturnToDepot() && geometry.legs().size() > ordered.size()) {
            t = t.plusSeconds(geometry.legs().getLast().durationSeconds());
        }

        int version = route.getCurrentVersion() + 1;
        List<UUID> order = new ArrayList<>(closed.stream().map(Stop::getId).toList());
        order.addAll(ordered.stream().map(Stop::getId).toList());
        long distance = closed.stream().mapToLong(Stop::getLegDistanceMeters).sum() + geometry.distanceMeters();
        long duration = Duration.between(now, t).toSeconds();
        var line = Geometries.lineString(geometry.points());
        route.applyVersion(version, algorithm, distance, duration, line, t, now);
        versions.save(new RouteVersion(route, version, algorithm, reason, order, distance, duration, computeMillis,
                matrixSource, line));
        log.info("Ruta {} v{} ({}): {} paradas abiertas, {} s de cálculo, matriz {}", route.getId(), version,
                algorithm, ordered.size(), computeMillis, matrixSource);
        events.publishEvent(new RouteOptimizedEvent(route.getId(), route.getLoadId(), route.getDriverId(), version,
                algorithm, reason));
    }

    /** Recalcula solo las ETA a partir de ahora con los tiempos de tramo ya conocidos (sin llamar a OSRM). */
    void refreshEtas(Route route, Instant from) {
        Instant t = from;
        Instant now = Instant.now();
        List<Stop> open = route.getStops().stream().filter(s -> s.getStatus().isOpen())
                .sorted(Comparator.comparingInt(Stop::getSequence)).toList();
        for (Stop stop : open) {
            t = t.plusSeconds(stop.getLegDurationSeconds());
            stop.setEta(t);
            long[] w = window(stop, now);
            if (w != null && t.isBefore(now.plusSeconds(w[0]))) {
                t = now.plusSeconds(w[0]);
            }
            t = t.plusSeconds(stop.getServiceSeconds());
        }
        route.setEstimatedEnd(t);
    }

    long serviceSecondsFor(int parcels) {
        return properties.baseServiceSeconds() + Math.max(0, parcels - 1) * properties.perParcelSeconds();
    }

    /** Franja de la parada en segundos desde ahora, o null si no tiene. */
    private static long[] window(Stop stop, Instant now) {
        if (stop.getWindowStart() == null || stop.getWindowEnd() == null) {
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = now.atZone(zone).toLocalDate();
        long start = Duration.between(now, today.atTime(stop.getWindowStart()).atZone(zone)).toSeconds();
        long end = Duration.between(now, today.atTime(stop.getWindowEnd()).atZone(zone)).toSeconds();
        return new long[]{Math.max(0, start), Math.max(0, end)};
    }

    private static List<Integer> range(int from, int count) {
        List<Integer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(from + i);
        }
        return list;
    }

    static LocalTime tightestStart(LocalTime a, LocalTime b) {
        return a == null ? b : (b == null ? a : (a.isAfter(b) ? a : b));
    }

    static LocalTime tightestEnd(LocalTime a, LocalTime b) {
        return a == null ? b : (b == null ? a : (a.isBefore(b) ? a : b));
    }
}
