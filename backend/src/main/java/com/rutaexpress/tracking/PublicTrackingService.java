package com.rutaexpress.tracking;

import com.rutaexpress.parcel.ParcelDtos.PublicParcelView;
import com.rutaexpress.parcel.ParcelService;
import com.rutaexpress.routing.RouteService;
import com.rutaexpress.routing.RoutingDtos.ParcelEta;
import com.rutaexpress.shared.config.CacheConfig;
import com.rutaexpress.tracking.TrackingDtos.PublicTracking;
import com.rutaexpress.tracking.TrackingDtos.PublicTrackingEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Seguimiento público por código: estado y hora estimada, sin exponer datos personales ni la ruta. */
@Service
class PublicTrackingService {

    private static final Duration ETA_MARGIN = Duration.ofMinutes(15);
    private static final Map<String, String> LABELS = Map.of(
            "REGISTERED", "Registrado en el almacén",
            "CREATED", "Registrado en el almacén",
            "LOADED", "Cargado en el vehículo de reparto",
            "OUT_FOR_DELIVERY", "En reparto",
            "DELIVERED", "Entregado",
            "FAILED_ATTEMPT", "Intento de entrega fallido",
            "RETURNED", "Devuelto al remitente",
            "CANCELLED", "Cancelado");

    private final ParcelService parcels;
    private final RouteService routes;

    PublicTrackingService(ParcelService parcels, RouteService routes) {
        this.parcels = parcels;
        this.routes = routes;
    }

    @Cacheable(cacheNames = CacheConfig.PUBLIC_TRACKING, key = "#trackingCode.toUpperCase()", unless = "#result == null")
    @Transactional(readOnly = true)
    public PublicTracking track(String trackingCode) {
        Optional<PublicParcelView> view = parcels.publicView(trackingCode);
        if (view.isEmpty()) {
            return null;
        }
        PublicParcelView p = view.get();
        Instant etaFrom = null, etaTo = null;
        Integer stopsBefore = null;
        if ("OUT_FOR_DELIVERY".equals(p.status()) || "LOADED".equals(p.status())) {
            Optional<ParcelEta> eta = routes.etaForParcel(p.id());
            if (eta.isPresent() && eta.get().eta() != null && !"COMPLETED".equals(eta.get().routeStatus())) {
                etaFrom = eta.get().eta().minus(ETA_MARGIN);
                etaTo = eta.get().eta().plus(ETA_MARGIN);
                stopsBefore = eta.get().stopsBefore();
            }
        }
        List<PublicTrackingEvent> events = p.events().stream()
                .map(e -> new PublicTrackingEvent(e.type(), LABELS.getOrDefault(e.type(), e.type()), e.occurredAt()))
                .toList();
        Instant lastUpdate = events.isEmpty() ? null : events.getLast().at();
        return new PublicTracking(p.trackingCode(), p.status(), LABELS.getOrDefault(p.status(), p.status()), p.city(),
                etaFrom, etaTo, stopsBefore, lastUpdate, events);
    }
}
