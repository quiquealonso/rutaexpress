package com.rutaexpress.routing.optimizer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Construye la ruta yendo siempre a la parada más cercana (en tiempo) no visitada. O(n²). */
@Component
public class NearestNeighborOptimizer implements RouteOptimizer {

    public static final String NAME = "NEAREST_NEIGHBOR";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Integer> solve(OptimizationProblem p) {
        Set<Integer> remaining = new LinkedHashSet<>(p.nodes());
        List<Integer> order = new ArrayList<>(remaining.size());
        int current = p.start();
        while (!remaining.isEmpty()) {
            int best = -1;
            long bestCost = Long.MAX_VALUE;
            for (int candidate : remaining) {
                long c = p.cost()[current][candidate];
                if (c < bestCost) {
                    bestCost = c;
                    best = candidate;
                }
            }
            order.add(best);
            remaining.remove(best);
            current = best;
        }
        return order;
    }
}
