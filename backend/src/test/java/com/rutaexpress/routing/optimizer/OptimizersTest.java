package com.rutaexpress.routing.optimizer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizersTest {

    private final NearestNeighborOptimizer nearest = new NearestNeighborOptimizer();
    private final TwoOptOptimizer twoOpt = new TwoOptOptimizer(nearest);
    private final OrToolsOptimizer orTools = new OrToolsOptimizer();

    /** Puntos aleatorios en un plano; coste = distancia × 1.0, con un recargo asimétrico opcional. */
    private static long[][] randomMatrix(int n, long seed, boolean asymmetric) {
        Random random = new Random(seed);
        double[][] pts = new double[n][2];
        for (int i = 0; i < n; i++) {
            pts[i][0] = random.nextDouble() * 10_000;
            pts[i][1] = random.nextDouble() * 10_000;
        }
        long[][] m = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double d = Math.hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]);
                    // Asimetría: ir "hacia el norte" cuesta un 30 % más (calles de sentido único)
                    double factor = asymmetric && pts[j][1] > pts[i][1] ? 1.3 : 1.0;
                    m[i][j] = Math.round(d * factor);
                }
            }
        }
        return m;
    }

    private static OptimizationProblem problem(long[][] m, boolean returnToDepot) {
        List<Integer> nodes = new ArrayList<>();
        int last = returnToDepot ? m.length - 1 : m.length;
        for (int i = 1; i < last; i++) {
            nodes.add(i);
        }
        return new OptimizationProblem(m, 0, returnToDepot ? m.length - 1 : null, nodes, null, null,
                Duration.ofSeconds(2));
    }

    private static void assertIsPermutation(List<Integer> order, OptimizationProblem p) {
        assertThat(order).hasSize(p.nodes().size());
        assertThat(new HashSet<>(order)).containsExactlyInAnyOrderElementsOf(p.nodes());
    }

    @Test
    @DisplayName("Vecino más cercano: elige siempre la parada más próxima")
    void nearestNeighborOnLine() {
        // Puntos en línea: 0 — 1 — 2 — 3, desordenados en la lista de nodos
        long[][] m = new long[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m[i][j] = Math.abs(i - j) * 100L;
            }
        }
        OptimizationProblem p = OptimizationProblem.of(m, 0, null, List.of(3, 1, 2));
        assertThat(nearest.solve(p)).containsExactly(1, 2, 3);
        assertThat(p.tourCost(List.of(1, 2, 3))).isEqualTo(300);
    }

    @ParameterizedTest(name = "{0} paradas")
    @ValueSource(ints = {20, 50, 120})
    @DisplayName("2-opt + Or-opt nunca empeora la solución del vecino más cercano")
    void twoOptImprovesNearestNeighbor(int stops) {
        long[][] m = randomMatrix(stops + 2, stops, true);
        OptimizationProblem p = problem(m, true);
        List<Integer> nn = nearest.solve(p);
        List<Integer> improved = twoOpt.solve(p);
        assertIsPermutation(improved, p);
        assertThat(p.tourCost(improved)).isLessThanOrEqualTo(p.tourCost(nn));
    }

    @Test
    @DisplayName("2-opt deshace un cruce evidente")
    void twoOptRemovesCrossing() {
        // Cuadrado: 0(0,0) 1(0,1) 2(1,1) 3(1,0); el orden 1,3,2 cruza las diagonales
        double[][] pts = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        long[][] m = new long[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m[i][j] = Math.round(Math.hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]) * 1000);
            }
        }
        OptimizationProblem p = new OptimizationProblem(m, 0, 0, List.of(1, 2, 3), null, null, Duration.ofSeconds(1));
        List<Integer> order = twoOpt.solve(p);
        assertThat(p.tourCost(order)).isEqualTo(4000);
    }

    @Test
    @DisplayName("OR-Tools encuentra una solución válida y al menos tan buena como el vecino más cercano")
    void orToolsSolves() {
        long[][] m = randomMatrix(42, 7, true);
        OptimizationProblem p = problem(m, true);
        List<Integer> order = orTools.solve(p);
        assertIsPermutation(order, p);
        assertThat(p.tourCost(order)).isLessThanOrEqualTo(p.tourCost(nearest.solve(p)));
    }

    @Test
    @DisplayName("OR-Tools con ruta abierta (sin volver al almacén)")
    void orToolsOpenRoute() {
        long[][] m = randomMatrix(15, 3, false);
        OptimizationProblem p = problem(m, false);
        List<Integer> order = orTools.solve(p);
        assertIsPermutation(order, p);
    }

    @Test
    @DisplayName("OR-Tools respeta una franja horaria temprana")
    void orToolsTimeWindow() {
        // 0 almacén; 1 lejos pero con franja temprana; 2 y 3 cerca
        long[][] m = {
                {0, 1000, 100, 150},
                {1000, 0, 1000, 1000},
                {100, 1000, 0, 60},
                {150, 1000, 60, 0}};
        long[][] windows = new long[4][];
        windows[1] = new long[]{0, 1100};
        OptimizationProblem p = new OptimizationProblem(m, 0, null, List.of(1, 2, 3), new long[4], windows,
                Duration.ofSeconds(1));
        assertThat(orTools.solve(p).getFirst()).isEqualTo(1);
    }

    @Test
    @DisplayName("El registro cae a 2-opt si el algoritmo pedido no existe")
    void registryFallback() {
        OptimizerRegistry registry = new OptimizerRegistry(List.of(nearest, twoOpt), new SimpleMeterRegistry());
        long[][] m = randomMatrix(12, 1, false);
        OptimizerRegistry.Result r = registry.optimize(problem(m, true), "NO_EXISTE");
        assertThat(r.algorithm()).isEqualTo(TwoOptOptimizer.NAME);
        assertThat(registry.compareAll(problem(m, true))).hasSize(2);
    }
}
