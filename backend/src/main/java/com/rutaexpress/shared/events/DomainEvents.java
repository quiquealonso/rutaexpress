package com.rutaexpress.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Eventos de dominio que cruzan módulos. Viven en el módulo compartido para que el productor y el
 * consumidor no dependan uno del otro (por ejemplo, scanning publica LoadClosedEvent y routing lo escucha).
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** Se cierra una sesión de carga: dispara la optimización de la ruta. */
    public record LoadClosedEvent(UUID loadId, UUID driverId, UUID depotId) {
    }

    public record RouteOptimizedEvent(UUID routeId, UUID loadId, UUID driverId, int version, String algorithm,
                                      String reason) {
    }

    public record RouteOptimizationFailedEvent(UUID loadId, UUID driverId, String message) {
    }

    public record StopStatusChangedEvent(UUID routeId, UUID stopId, UUID driverId, String status, String reason,
                                         List<UUID> parcelIds, EventSource source, String actor) {
    }

    public record IncidentReportedEvent(UUID incidentId, UUID driverId, UUID routeId, String type,
                                        String description, Double lat, Double lon, Instant reportedAt,
                                        EventSource source) {
    }

    /** El repartidor se ha desviado mucho de la ruta prevista: se recalcula desde su posición. */
    public record RouteDeviationDetectedEvent(UUID routeId, UUID driverId, double lat, double lon,
                                              double distanceMeters) {
    }

    /** Los paquetes de una carga cerrada pasan a estar en la furgoneta. */
    public record ParcelsLoadedEvent(UUID loadId, UUID driverId, List<UUID> parcelIds, String actor) {
    }

    /** El repartidor inicia el reparto: los paquetes pasan a "en reparto". */
    public record RouteStartedEvent(UUID routeId, UUID driverId, List<UUID> parcelIds, String actor) {
    }

    /** Todas las paradas cerradas: fin de la ruta. */
    public record RouteCompletedEvent(UUID routeId, UUID driverId, int delivered, int failed) {
    }
}
