package com.rutaexpress.parcel;

import com.rutaexpress.shared.events.DomainEvents.ParcelsLoadedEvent;
import com.rutaexpress.shared.events.DomainEvents.RouteStartedEvent;
import com.rutaexpress.shared.events.DomainEvents.StopStatusChangedEvent;
import com.rutaexpress.shared.events.EventSource;
import org.springframework.modulith.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mantiene el estado de los paquetes a partir de los eventos de otros módulos. Así scanning, routing y delivery
 * no tienen que conocer el modelo de paquetes para actualizarlo.
 */
@Component
class ParcelLifecycleListener {

    private final ParcelService parcels;

    ParcelLifecycleListener(ParcelService parcels) {
        this.parcels = parcels;
    }

    @ApplicationModuleListener
    void on(ParcelsLoadedEvent event) {
        parcels.changeStatus(event.parcelIds(), ParcelStatus.LOADED, event.actor(), EventSource.API,
                Map.of("loadId", event.loadId().toString(), "driverId", event.driverId().toString()));
    }

    @ApplicationModuleListener
    void on(RouteStartedEvent event) {
        parcels.changeStatus(event.parcelIds(), ParcelStatus.OUT_FOR_DELIVERY, event.actor(), EventSource.API,
                Map.of("routeId", event.routeId().toString()));
    }

    @ApplicationModuleListener
    void on(StopStatusChangedEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("routeId", event.routeId().toString());
        data.put("stopId", event.stopId().toString());
        if (event.reason() != null) {
            data.put("reason", event.reason());
        }
        switch (event.status()) {
            case "DELIVERED" -> parcels.changeStatus(event.parcelIds(), ParcelStatus.DELIVERED, event.actor(),
                    event.source(), data);
            case "FAILED" -> parcels.changeStatus(event.parcelIds(), ParcelStatus.FAILED_ATTEMPT, event.actor(),
                    event.source(), data);
            case "SKIPPED" -> parcels.recordEvent(event.parcelIds(), "STOP_SKIPPED", event.actor(), event.source(),
                    data);
            case "PENDING" -> parcels.changeStatus(event.parcelIds(), ParcelStatus.OUT_FOR_DELIVERY, event.actor(),
                    event.source(), data);
            default -> {
            }
        }
    }
}
