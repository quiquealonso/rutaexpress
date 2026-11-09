package com.rutaexpress.tracking;

import com.rutaexpress.shared.events.DomainEvents.IncidentReportedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteCompletedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteOptimizationFailedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteOptimizedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteStartedEvent;
import com.rutaexpress.shared.events.DomainEvents.StopStatusChangedEvent;
import com.rutaexpress.tracking.TrackingDtos.RealtimeMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Reenvía los eventos de dominio por WebSocket, solo cuando la transacción se ha confirmado:
 * al panel (/topic/dispatch) y al repartidor afectado (/topic/drivers/{id}).
 */
@Component
class RealtimeBroadcaster {

    private final SimpMessagingTemplate messaging;

    RealtimeBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RouteOptimizedEvent e) {
        send("ROUTE_OPTIMIZED", e, e.driverId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RouteOptimizationFailedEvent e) {
        send("ROUTE_OPTIMIZATION_FAILED", e, e.driverId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RouteStartedEvent e) {
        send("ROUTE_STARTED", new RouteRef(e.routeId(), e.driverId()), e.driverId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RouteCompletedEvent e) {
        send("ROUTE_COMPLETED", e, e.driverId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(StopStatusChangedEvent e) {
        send("STOP_STATUS_CHANGED", e, e.driverId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(IncidentReportedEvent e) {
        send("INCIDENT_REPORTED", e, e.driverId());
    }

    private void send(String type, Object payload, UUID driverId) {
        RealtimeMessage message = new RealtimeMessage(type, payload, Instant.now());
        messaging.convertAndSend("/topic/dispatch", message);
        if (driverId != null) {
            messaging.convertAndSend("/topic/drivers/" + driverId, message);
        }
    }

    record RouteRef(UUID routeId, UUID driverId) {
    }
}
