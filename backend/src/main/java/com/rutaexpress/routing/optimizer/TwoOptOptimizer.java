package com.rutaexpress.routing.optimizer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación propia: parte del vecino más cercano y la mejora con búsqueda local.
 * <ul>
 *   <li><b>2-opt</b>: invierte un tramo i..j si reduce el coste. Como la matriz es asimétrica, el tramo
 *   invertido se recorre en sentido contrario y hay que recalcular su coste completo.</li>
 *   <li><b>Or-opt</b>: mueve segmentos de 1 a 3 paradas consecutivas a otra posición (sin invertirlos).</li>
 * </ul>
 * Se repite hasta que ningún movimiento mejora o se agota el tiempo.
 */
@Component
public class TwoOptOptimizer implements RouteOptimizer {

    public static final String NAME = "TWO_OPT";

    private final NearestNeighborOptimizer initial;

    public TwoOptOptimizer(NearestNeighborOptimizer initial) {
        this.initial = initial;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Integer> solve(OptimizationProblem p) {
        List<Integer> tour = new ArrayList<>(initial.solve(p));
        if (tour.size() < 3) {
            return tour;
        }
        long deadline = System.nanoTime() + p.timeLimit().toNanos();
        boolean improved = true;
        while (improved && System.nanoTime() < deadline) {
            improved = twoOpt(p, tour, deadline);
            improved |= orOpt(p, tour, deadline);
        }
        return tour;
    }

    /** Una pasada completa de 2-opt con "primera mejora". */
    private boolean twoOpt(OptimizationProblem p, List<Integer> tour, long deadline) {
        long[][] c = p.cost();
        int n = tour.size();
        boolean improvedAny = false;
        for (int i = 0; i < n - 1; i++) {
            if (System.nanoTime() > deadline) {
                return improvedAny;
            }
            for (int j = i + 1; j < n; j++) {
                int before = i == 0 ? p.start() : tour.get(i - 1);
                Integer after = j == n - 1 ? p.end() : tour.get(j + 1);
                // Coste actual del tramo [before, i..j, after] frente al invertido [before, j..i, after]
                long current = c[before][tour.get(i)] + (after == null ? 0 : c[tour.get(j)][after]);
                long reversed = c[before][tour.get(j)] + (after == null ? 0 : c[tour.get(i)][after]);
                for (int k = i; k < j; k++) {
                    current += c[tour.get(k)][tour.get(k + 1)];
                    reversed += c[tour.get(k + 1)][tour.get(k)];
                }
                if (reversed < current) {
                    reverse(tour, i, j);
                    improvedAny = true;
                }
            }
        }
        return improvedAny;
    }

    /** Or-opt: recoloca segmentos de longitud 1..3 en la mejor posición. */
    private boolean orOpt(OptimizationProblem p, List<Integer> tour, long deadline) {
        boolean improvedAny = false;
        for (int len = 1; len <= 3; len++) {
            for (int i = 0; i + len <= tour.size(); i++) {
                if (System.nanoTime() > deadline) {
                    return improvedAny;
                }
                long base = p.tourCost(tour);
                List<Integer> segment = new ArrayList<>(tour.subList(i, i + len));
                List<Integer> rest = new ArrayList<>(tour);
                rest.subList(i, i + len).clear();
                long bestCost = base;
                int bestPos = -1;
                for (int pos = 0; pos <= rest.size(); pos++) {
                    if (pos == i) {
                        continue;
                    }
                    List<Integer> candidate = new ArrayList<>(rest);
                    candidate.addAll(pos, segment);
                    long cost = p.tourCost(candidate);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestPos = pos;
                    }
                }
                if (bestPos >= 0) {
                    rest.addAll(bestPos, segment);
                    tour.clear();
                    tour.addAll(rest);
                    improvedAny = true;
                }
            }
        }
        return improvedAny;
    }

    private static void reverse(List<Integer> tour, int i, int j) {
        while (i < j) {
            Integer tmp = tour.get(i);
            tour.set(i, tour.get(j));
            tour.set(j, tmp);
            i++;
            j--;
        }
    }
}
