package com.rutaexpress.geo;

import com.rutaexpress.geo.GeoModels.DirectionStep;
import com.rutaexpress.geo.GeoModels.DistanceMatrix;
import com.rutaexpress.geo.GeoModels.RouteGeometry;
import com.rutaexpress.geo.GeoModels.RouteLeg;
import com.rutaexpress.shared.geo.GeoPoint;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Acceso a tiempos reales por carretera. Primero se mira la caché de pares (distance_cache); si falta alguno,
 * se pide la matriz completa a OSRM y se guarda. Si OSRM no está disponible y el respaldo está activado,
 * se estima con distancia haversine × factor de rodeo a una velocidad media urbana.
 */
@Service
public class RoutingEngineService {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngineService.class);

    private final OsrmClient osrm;
    private final DistanceCacheRepository cache;
    private final GeoProperties properties;
    private final MeterRegistry metrics;

    RoutingEngineService(OsrmClient osrm, DistanceCacheRepository cache, GeoProperties properties,
                         MeterRegistry metrics) {
        this.osrm = osrm;
        this.cache = cache;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Transactional
    public DistanceMatrix matrix(List<GeoPoint> points) {
        int n = points.size();
        List<String> keys = points.stream().map(RoutingEngineService::key).toList();
        Map<String, DistanceCache> cached = new HashMap<>();
        Set<String> distinctKeys = new LinkedHashSet<>(keys);
        for (DistanceCache d : cache.findAllBetween(distinctKeys)) {
            cached.put(d.getOriginKey() + "|" + d.getDestinationKey(), d);
        }
        long[][] seconds = new long[n][n];
        long[][] meters = new long[n][n];
        boolean complete = true;
        for (int i = 0; i < n && complete; i++) {
            for (int j = 0; j < n; j++) {
                if (keys.get(i).equals(keys.get(j))) {
                    continue;
                }
                DistanceCache d = cached.get(keys.get(i) + "|" + keys.get(j));
                if (d == null) {
                    complete = false;
                    break;
                }
                seconds[i][j] = d.getSeconds();
                meters[i][j] = d.getMeters();
            }
        }
        if (complete) {
            metrics.counter("rutaexpress.matrix.requests", "source", "cache").increment();
            return new DistanceMatrix(seconds, meters, "CACHE");
        }
        DistanceMatrix fresh = computeMatrix(points);
        if ("OSRM".equals(fresh.source())) {
            List<DistanceCache> toSave = new ArrayList<>();
            Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    String pair = keys.get(i) + "|" + keys.get(j);
                    if (i != j && !keys.get(i).equals(keys.get(j)) && !cached.containsKey(pair) && seen.add(pair)) {
                        toSave.add(new DistanceCache(keys.get(i), keys.get(j), fresh.seconds()[i][j],
                                fresh.meters()[i][j]));
                    }
                }
            }
            cache.saveAll(toSave);
        }
        return fresh;
    }

    private DistanceMatrix computeMatrix(List<GeoPoint> points) {
        try {
            DistanceMatrix m = metrics.timer("rutaexpress.osrm.table").recordCallable(() -> osrm.table(points));
            metrics.counter("rutaexpress.matrix.requests", "source", "osrm").increment();
            return m;
        } catch (Exception e) {
            if (!properties.fallbackEnabled()) {
                throw new IllegalStateException("No se pudo calcular la matriz de tiempos: " + e.getMessage(), e);
            }
            log.warn("OSRM no disponible ({}); se usa la estimación en línea recta", e.getMessage());
            metrics.counter("rutaexpress.matrix.requests", "source", "fallback").increment();
            return haversineMatrix(points);
        }
    }

    public RouteGeometry route(List<GeoPoint> points, boolean withSteps) {
        if (points.size() < 2) {
            return new RouteGeometry(List.copyOf(points), List.of(), 0, 0, "NONE");
        }
        try {
            return osrm.route(points, withSteps);
        } catch (Exception e) {
            if (!properties.fallbackEnabled()) {
                throw new IllegalStateException("No se pudo calcular la geometría de la ruta: " + e.getMessage(), e);
            }
            log.warn("OSRM no disponible ({}); geometría en línea recta", e.getMessage());
            return straightLineRoute(points);
        }
    }

    DistanceMatrix haversineMatrix(List<GeoPoint> points) {
        int n = points.size();
        long[][] seconds = new long[n][n];
        long[][] meters = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double m = points.get(i).distanceTo(points.get(j)) * properties.fallbackDetourFactor();
                    meters[i][j] = Math.round(m);
                    seconds[i][j] = Math.round(m / (properties.fallbackSpeedKmh() / 3.6));
                }
            }
        }
        return new DistanceMatrix(seconds, meters, "HAVERSINE");
    }

    private RouteGeometry straightLineRoute(List<GeoPoint> points) {
        List<RouteLeg> legs = new ArrayList<>();
        long totalS = 0, totalM = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            double m = points.get(i).distanceTo(points.get(i + 1)) * properties.fallbackDetourFactor();
            long s = Math.round(m / (properties.fallbackSpeedKmh() / 3.6));
            legs.add(new RouteLeg(s, Math.round(m), List.of(
                    new DirectionStep("Dirígete a la siguiente parada", "", Math.round(m), s, points.get(i)),
                    new DirectionStep("Has llegado a tu destino", "", 0, 0, points.get(i + 1)))));
            totalS += s;
            totalM += Math.round(m);
        }
        return new RouteGeometry(List.copyOf(points), legs, totalS, totalM, "STRAIGHT_LINE");
    }

    static String key(GeoPoint p) {
        return String.format(Locale.ROOT, "%.5f,%.5f", p.lat(), p.lon());
    }

    @Scheduled(cron = "0 0 4 * * SUN")
    @Transactional
    public void purgeOldDistances() {
        // Las obras y los cambios de sentido existen: los tiempos se refrescan cada 30 días
        int deleted = cache.deleteOlderThan(Instant.now().minus(Duration.ofDays(30)));
        log.info("Entradas de distance_cache eliminadas: {}", deleted);
    }
}
