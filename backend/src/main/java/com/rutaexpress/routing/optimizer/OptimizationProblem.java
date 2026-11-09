package com.rutaexpress.routing.optimizer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Problema de ruta de un vehículo (TSP asimétrico, opcionalmente con franjas horarias).
 *
 * @param cost          matriz de tiempos en segundos entre todos los nodos (asimétrica)
 * @param start         nodo de salida (almacén o posición actual del repartidor)
 * @param end           nodo de llegada (almacén) o {@code null} si la ruta termina en la última parada
 * @param nodes         nodos a visitar, en el orden original (orden de escaneo)
 * @param serviceSeconds tiempo de servicio en cada nodo (aparcar, entregar...)
 * @param windows       franja [inicio, fin] en segundos desde la salida, o {@code null} si el nodo no tiene
 * @param timeLimit     tiempo máximo de cálculo para los algoritmos que lo admiten
 */
public record OptimizationProblem(long[][] cost, int start, Integer end, List<Integer> nodes,
                                  long[] serviceSeconds, long[][] windows, Duration timeLimit) {

    public OptimizationProblem {
        Objects.requireNonNull(cost);
        nodes = List.copyOf(nodes);
        if (serviceSeconds == null) {
            serviceSeconds = new long[cost.length];
        }
        if (windows == null) {
            windows = new long[cost.length][];
        }
        if (timeLimit == null) {
            timeLimit = Duration.ofSeconds(3);
        }
    }

    public static OptimizationProblem of(long[][] cost, int start, Integer end, List<Integer> nodes) {
        return new OptimizationProblem(cost, start, end, nodes, null, null, null);
    }

    public boolean hasTimeWindows() {
        for (int node : nodes) {
            if (windows[node] != null) {
                return true;
            }
        }
        return false;
    }

    /** Coste total (segundos de conducción) de visitar los nodos en el orden dado. */
    public long tourCost(List<Integer> order) {
        long total = 0;
        int prev = start;
        for (int node : order) {
            total += cost[prev][node];
            prev = node;
        }
        if (end != null) {
            total += cost[prev][end];
        }
        return total;
    }
}
