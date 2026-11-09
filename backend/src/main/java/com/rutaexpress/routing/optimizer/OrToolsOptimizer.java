package com.rutaexpress.routing.optimizer;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingDimension;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Google OR-Tools: primera solución por "arco más barato" y mejora con búsqueda local guiada, con límite de
 * tiempo. Si hay franjas horarias se añade una dimensión de tiempo con límites superiores blandos (llegar tarde
 * penaliza pero no hace el problema imposible).
 */
@Component
public class OrToolsOptimizer implements RouteOptimizer {

    public static final String NAME = "OR_TOOLS";
    private static final Logger log = LoggerFactory.getLogger(OrToolsOptimizer.class);
    private static final long LATE_PENALTY_PER_SECOND = 50;
    private static volatile boolean nativeLoaded;

    @Override
    public String name() {
        return NAME;
    }

    private static synchronized void ensureNativeLoaded() {
        if (!nativeLoaded) {
            Loader.loadNativeLibraries();
            nativeLoaded = true;
        }
    }

    @Override
    public List<Integer> solve(OptimizationProblem p) {
        if (p.nodes().size() < 2) {
            return new ArrayList<>(p.nodes());
        }
        ensureNativeLoaded();

        // Se construye un subproblema compacto: 0 = inicio, 1..n = paradas, n+1 = final (real o ficticio)
        List<Integer> nodes = p.nodes();
        int n = nodes.size();
        int size = n + 2;
        int[] original = new int[size];
        original[0] = p.start();
        for (int i = 0; i < n; i++) {
            original[i + 1] = nodes.get(i);
        }
        boolean openRoute = p.end() == null;
        original[n + 1] = openRoute ? -1 : p.end();

        long[][] c = new long[size][size];
        long[] service = new long[size];
        for (int i = 0; i < size; i++) {
            if (original[i] >= 0) {
                service[i] = p.serviceSeconds()[original[i]];
            }
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    continue;
                }
                if (original[j] == -1) {
                    c[i][j] = 0; // llegar al final ficticio no cuesta: ruta abierta
                } else if (original[i] == -1) {
                    c[i][j] = Integer.MAX_VALUE / 4;
                } else {
                    c[i][j] = p.cost()[original[i]][original[j]];
                }
            }
        }

        RoutingIndexManager manager = new RoutingIndexManager(size, 1, new int[]{0}, new int[]{n + 1});
        RoutingModel routing = new RoutingModel(manager);
        int arcCost = routing.registerTransitCallback((long fromIndex, long toIndex) ->
                c[manager.indexToNode(fromIndex)][manager.indexToNode(toIndex)]);
        routing.setArcCostEvaluatorOfAllVehicles(arcCost);

        if (p.hasTimeWindows()) {
            int timeCallback = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                int from = manager.indexToNode(fromIndex);
                return c[from][manager.indexToNode(toIndex)] + service[from];
            });
            long horizon = 24 * 3600;
            routing.addDimension(timeCallback, 3600, horizon, true, "Time");
            RoutingDimension time = routing.getMutableDimension("Time");
            for (int i = 1; i <= n; i++) {
                long[] w = p.windows()[original[i]];
                if (w != null) {
                    long index = manager.nodeToIndex(i);
                    time.cumulVar(index).setRange(Math.max(0, w[0]), horizon);
                    time.setCumulVarSoftUpperBound(index, Math.max(0, w[1]), LATE_PENALTY_PER_SECOND);
                }
            }
        }

        RoutingSearchParameters params = main.defaultRoutingSearchParameters().toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(com.google.protobuf.Duration.newBuilder()
                        .setSeconds(p.timeLimit().toSeconds())
                        .setNanos(p.timeLimit().toNanosPart())
                        .build())
                .build();
        Assignment solution = routing.solveWithParameters(params);
        if (solution == null) {
            throw new IllegalStateException("OR-Tools no encontró solución (estado " + routing.status() + ")");
        }
        List<Integer> order = new ArrayList<>(n);
        long index = routing.start(0);
        while (!routing.isEnd(index)) {
            int node = manager.indexToNode(index);
            if (node != 0) {
                order.add(original[node]);
            }
            index = solution.value(routing.nextVar(index));
        }
        log.debug("OR-Tools: {} paradas, coste {}", n, solution.objectiveValue());
        return order;
    }
}
