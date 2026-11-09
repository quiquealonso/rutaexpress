package com.rutaexpress.routing;

import com.rutaexpress.shared.events.DomainEvents.LoadClosedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteDeviationDetectedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteOptimizationFailedEvent;
import com.rutaexpress.shared.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacciona a eventos de otros módulos. @ApplicationModuleListener = asíncrono, tras el commit y en su propia
 * transacción: cerrar la carga responde al momento (202) y la ruta se calcula en el pool de optimización.
 */
@Component
class RoutingListeners {

    private static final Logger log = LoggerFactory.getLogger(RoutingListeners.class);

    private final RouteService routes;
    private final ApplicationEventPublisher events;

    RoutingListeners(RouteService routes, ApplicationEventPublisher events) {
        this.routes = routes;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(LoadClosedEvent event) {
        try {
            routes.planForLoad(event.loadId());
        } catch (RuntimeException e) {
            log.error("No se pudo optimizar la ruta de la carga {}", event.loadId(), e);
            events.publishEvent(new RouteOptimizationFailedEvent(event.loadId(), event.driverId(), e.getMessage()));
        }
    }

    @ApplicationModuleListener
    void on(RouteDeviationDetectedEvent event) {
        log.info("Desvío de {} m en la ruta {}: se recalcula", Math.round(event.distanceMeters()), event.routeId());
        routes.reoptimizeIfDue(event.routeId(), new GeoPoint(event.lat(), event.lon()), "DEVIATION");
    }
}
