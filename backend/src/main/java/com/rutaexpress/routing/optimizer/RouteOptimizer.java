package com.rutaexpress.routing.optimizer;

import java.util.List;

/**
 * Estrategia de optimización intercambiable por configuración. Se minimiza el tiempo total del recorrido:
 * <pre>
 *   T = t(p0, p1) + Σ t(p_i, p_i+1) [+ t(p_n, p0) si se vuelve al almacén]
 * </pre>
 * donde p0 es el punto de salida y t(a, b) el tiempo por carretera de la matriz.
 */
public interface RouteOptimizer {

    /** Nombre estable (se guarda en la versión de la ruta). */
    String name();

    /** Devuelve los nodos de {@code problem.nodes()} en el orden de visita (sin el inicio ni el final). */
    List<Integer> solve(OptimizationProblem problem);
}
