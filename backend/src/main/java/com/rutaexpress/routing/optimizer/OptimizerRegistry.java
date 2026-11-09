package com.rutaexpress.routing.optimizer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Elige el algoritmo configurado y, si falla o no termina, usa 2-opt como respaldo. */
@Component
public class OptimizerRegistry {

    private static final Logger log = LoggerFactory.getLogger(OptimizerRegistry.class);

    private final Map<String, RouteOptimizer> optimizers = new LinkedHashMap<>();
    private final MeterRegistry metrics;

    public OptimizerRegistry(List<RouteOptimizer> optimizers, MeterRegistry metrics) {
        optimizers.forEach(o -> this.optimizers.put(o.name(), o));
        this.metrics = metrics;
    }

    public record Result(List<Integer> order, String algorithm, long costSeconds, long computeMillis,
                         boolean fallback) {
    }

    public Result optimize(OptimizationProblem problem, String preferred) {
        RouteOptimizer optimizer = optimizers.getOrDefault(preferred, optimizers.get(TwoOptOptimizer.NAME));
        try {
            return run(optimizer, problem, false);
        } catch (RuntimeException | LinkageError e) {
            log.warn("El optimizador {} falló ({}); se usa {}", optimizer.name(), e.getMessage(), TwoOptOptimizer.NAME);
            return run(optimizers.get(TwoOptOptimizer.NAME), problem, true);
        }
    }

    /** Ejecuta todos los algoritmos sobre el mismo problema (comparativa para la memoria del proyecto). */
    public List<Result> compareAll(OptimizationProblem problem) {
        List<Result> results = new ArrayList<>();
        for (RouteOptimizer o : optimizers.values()) {
            try {
                results.add(run(o, problem, false));
            } catch (RuntimeException | LinkageError e) {
                log.warn("Comparativa: {} falló: {}", o.name(), e.getMessage());
            }
        }
        return results;
    }

    public List<String> names() {
        return List.copyOf(optimizers.keySet());
    }

    private Result run(RouteOptimizer optimizer, OptimizationProblem problem, boolean fallback) {
        long t0 = System.nanoTime();
        List<Integer> order = optimizer.solve(problem);
        long elapsed = System.nanoTime() - t0;
        Timer.builder("rutaexpress.optimization.time")
                .tag("algorithm", optimizer.name())
                .register(metrics)
                .record(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS);
        return new Result(order, optimizer.name(), problem.tourCost(order), elapsed / 1_000_000, fallback);
    }
}
