package com.rutaexpress.mcp;

import com.rutaexpress.delivery.DeliveryDtos.DirectionsResponse;
import com.rutaexpress.delivery.DeliveryDtos.FailReason;
import com.rutaexpress.delivery.DeliveryService;
import com.rutaexpress.delivery.Incident;
import com.rutaexpress.parcel.ParcelDtos.ParcelInfo;
import com.rutaexpress.parcel.ParcelService;
import com.rutaexpress.routing.RouteService;
import com.rutaexpress.routing.RoutingDtos.RouteDetail;
import com.rutaexpress.routing.RoutingDtos.RouteSummary;
import com.rutaexpress.routing.RoutingDtos.StopDetail;
import com.rutaexpress.shared.error.BusinessException;
import com.rutaexpress.shared.security.CurrentUser;
import com.rutaexpress.tracking.TrackingService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Herramientas del conductor. Las usan tanto el servidor MCP (clientes externos) como el asistente de la PWA.
 * Reutilizan los servicios de aplicación, con sus validaciones y su auditoría (origen MCP o ASSISTANT).
 * Las respuestas son cortas y en castellano, aptas para leer en voz alta.
 */
@Component
public class DriverTools {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final RouteService routes;
    private final DeliveryService delivery;
    private final ParcelService parcels;
    private final TrackingService tracking;

    DriverTools(RouteService routes, DeliveryService delivery, ParcelService parcels, TrackingService tracking) {
        this.routes = routes;
        this.delivery = delivery;
        this.parcels = parcels;
        this.tracking = tracking;
    }

    public record StopView(String stopId, int order, String status, String address, String recipients,
                           int parcels, List<String> trackingCodes, String notes, String eta, Long distanceMeters,
                           String window) {
    }

    public record SummaryView(int delivered, int pending, int failed, int parcelsPending, double remainingKm,
                              String estimatedEnd, String status) {
    }

    public record DirectionsView(String address, double distanceKm, int minutes, List<String> steps,
                                 String navigationUrl) {
    }

    public record ParcelView(String trackingCode, String recipient, String address, String status, String stopId,
                             Integer stopOrder) {
    }

    public record ActionResult(boolean ok, String message, StopView nextStop) {
    }

    @Tool(name = "get_next_stop", description = """
            Devuelve la siguiente parada pendiente del repartidor: dirección, destinatario, número de paquetes,
            notas, distancia y hora estimada de llegada. Úsala cuando pregunte "¿cuál es la siguiente?",
            "¿a dónde voy ahora?" o similar.""")
    public StopView getNextStop(ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            UUID routeId = currentRoute();
            return routes.nextStop(routeId).map(this::view)
                    .orElseThrow(() -> new BusinessException("no-pending-stops", "No quedan paradas pendientes"));
        });
    }

    @Tool(name = "list_pending_stops", description = "Lista las paradas pendientes en el orden previsto, con su hora estimada.")
    public List<StopView> listPendingStops(
            @ToolParam(description = "Número máximo de paradas a devolver (por defecto 5)", required = false) Integer limit,
            ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> routes.pendingStops(currentRoute(), limit == null ? 5 : Math.min(limit, 50))
                .stream().map(this::view).toList());
    }

    @Tool(name = "get_route_summary", description = """
            Resumen del reparto: paradas entregadas, pendientes y fallidas, kilómetros restantes y hora estimada
            de fin. Úsala para "¿cuánto me queda?" o "¿cómo voy?".""")
    public SummaryView getRouteSummary(ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            RouteSummary s = routes.summary(currentRoute());
            return new SummaryView(s.delivered(), s.pending(), s.failed(), s.parcelsPending(),
                    Math.round(s.remainingDistanceMeters() / 100.0) / 10.0, hour(s.estimatedEnd()), s.status());
        });
    }

    @Tool(name = "get_directions", description = """
            Indicaciones paso a paso desde la posición actual hasta una parada (la siguiente si no se indica)
            y un enlace para abrir la navegación.""")
    public DirectionsView getDirections(
            @ToolParam(description = "Id de la parada; si se omite, la siguiente pendiente", required = false) String stopId,
            ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            DirectionsResponse d = delivery.directions(currentRoute(), stopId == null ? null : UUID.fromString(stopId));
            List<String> steps = d.steps().stream().limit(12)
                    .map(s -> s.distanceMeters() > 0 ? s.instruction() + " (" + s.distanceMeters() + " m)" : s.instruction())
                    .toList();
            return new DirectionsView(d.addressLine(), Math.round(d.distanceMeters() / 100.0) / 10.0,
                    (int) Math.ceil(d.durationSeconds() / 60.0), steps, d.googleMapsUrl());
        });
    }

    @Tool(name = "find_parcel", description = """
            Busca un paquete de la ruta actual por código de seguimiento, nombre del destinatario o calle.
            Ejemplo: "¿dónde va el de García?".""")
    public List<ParcelView> findParcel(@ToolParam(description = "Texto a buscar") String query, ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            RouteDetail route = routes.get(currentRoute());
            Map<UUID, StopDetail> stopByParcel = route.stops().stream()
                    .flatMap(s -> s.parcels().stream().map(p -> Map.entry(p.id(), s)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
            List<ParcelInfo> found = parcels.searchAmong(stopByParcel.keySet(), query);
            return found.stream().limit(10).map(p -> {
                StopDetail s = stopByParcel.get(p.id());
                return new ParcelView(p.trackingCode(), p.recipientName(), p.addressLine(), p.status(),
                        s == null ? null : s.id().toString(), s == null ? null : s.sequence());
            }).toList();
        });
    }

    @Tool(name = "mark_delivered", description = """
            Marca una parada como entregada. Confirma antes con el repartidor qué parada es si hay duda.""")
    public ActionResult markDelivered(
            @ToolParam(description = "Id de la parada") String stopId,
            @ToolParam(description = "Nombre de quien recoge el paquete", required = false) String receiverName,
            ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            StopDetail stop = delivery.deliverWithoutFile(UUID.fromString(stopId), receiverName, ToolIdentity.source(ctx));
            return result("Entregado en " + stop.addressLine() + ".", stop.routeId());
        });
    }

    @Tool(name = "mark_failed", description = """
            Registra una entrega fallida: ausente (ABSENT), dirección incorrecta (WRONG_ADDRESS), rechazado (REFUSED),
            dañado (DAMAGED), acceso imposible (INACCESSIBLE) u otro (OTHER). Con retryLater=true la parada se
            deja para el final de la ruta y se recalcula.""")
    public ActionResult markFailed(
            @ToolParam(description = "Id de la parada") String stopId,
            @ToolParam(description = "Motivo: ABSENT, WRONG_ADDRESS, REFUSED, DAMAGED, INACCESSIBLE u OTHER") String reason,
            @ToolParam(description = "Reintentar más tarde en esta misma ruta", required = false) Boolean retryLater,
            ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            FailReason r = FailReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
            boolean retry = Boolean.TRUE.equals(retryLater);
            StopDetail stop = delivery.fail(UUID.fromString(stopId), r, null, retry, null, ToolIdentity.source(ctx));
            return result((retry ? "Anotado, la dejo para el final: " : "Registrado como fallida: ")
                    + r.label().toLowerCase(Locale.ROOT) + ".", stop.routeId());
        });
    }

    @Tool(name = "skip_stop", description = "Deja una parada para el final de la ruta y recalcula el orden del resto.")
    public ActionResult skipStop(@ToolParam(description = "Id de la parada") String stopId, ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            RouteDetail route = delivery.skip(UUID.fromString(stopId), null, ToolIdentity.source(ctx));
            return result("Parada aplazada al final. Ruta recalculada.", route.id());
        });
    }

    @Tool(name = "reoptimize_route", description = "Recalcula el orden de las paradas pendientes desde la posición actual.")
    public ActionResult reoptimizeRoute(ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            RouteDetail route = delivery.reoptimize(currentRoute(), null, ToolIdentity.source(ctx));
            return result("Ruta recalculada (versión " + route.version() + ").", route.id());
        });
    }

    @Tool(name = "report_incident", description = """
            Avisa al panel de una incidencia: avería (BREAKDOWN), accidente (ACCIDENT), retraso (DELAY),
            tráfico (TRAFFIC), calle cortada (ROAD_CLOSED), paquete dañado (DAMAGED_PARCEL) u otra (OTHER).""")
    public ActionResult reportIncident(
            @ToolParam(description = "Tipo: BREAKDOWN, ACCIDENT, DELAY, TRAFFIC, ROAD_CLOSED, DAMAGED_PARCEL u OTHER") String type,
            @ToolParam(description = "Descripción breve") String description,
            ToolContext ctx) {
        return ToolIdentity.run(ctx, () -> {
            Incident.Type t = Incident.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
            delivery.reportIncident(t, description, null, ToolIdentity.source(ctx));
            return new ActionResult(true, "Incidencia enviada a tráfico.", null);
        });
    }

    // ---------- Apoyo para recursos MCP y el asistente ----------

    String currentRouteJsonSummary() {
        UUID routeId = currentRoute();
        RouteSummary s = routes.summary(routeId);
        String next = routes.nextStop(routeId).map(st -> st.sequence() + ". " + st.addressLine()).orElse("ninguna");
        return """
                {"routeId":"%s","status":"%s","delivered":%d,"pending":%d,"failed":%d,"remainingKm":%.1f,"estimatedEnd":"%s","nextStop":"%s"}"""
                .formatted(routeId, s.status(), s.delivered(), s.pending(), s.failed(),
                        s.remainingDistanceMeters() / 1000.0, hour(s.estimatedEnd()), next.replace("\"", "'"));
    }

    StopView stopView(UUID stopId) {
        return view(routes.getStop(stopId));
    }

    boolean driverIsStationary() {
        return tracking.isStationary(CurrentUser.driverId());
    }

    private UUID currentRoute() {
        return routes.currentRouteId(CurrentUser.driverId())
                .orElseThrow(() -> new BusinessException("no-route", "No tienes ninguna ruta asignada hoy"));
    }

    private ActionResult result(String message, UUID routeId) {
        StopView next = routes.nextStop(routeId).map(this::view).orElse(null);
        return new ActionResult(true, next == null ? message + " No quedan más paradas." : message, next);
    }

    private StopView view(StopDetail s) {
        String notes = s.parcels().stream().map(p -> p.notes()).filter(n -> n != null && !n.isBlank())
                .distinct().collect(Collectors.joining(" / "));
        Long distance = null;
        var position = tracking.latestPoint(CurrentUser.driverId());
        if (position.isPresent() && s.location() != null) {
            distance = Math.round(position.get().distanceTo(s.location()));
        }
        String window = s.windowStart() != null && s.windowEnd() != null ? s.windowStart() + "–" + s.windowEnd() : null;
        return new StopView(s.id().toString(), s.sequence(), s.status(), s.addressLine(), s.recipientNames(),
                s.parcels().size(), s.parcels().stream().map(p -> p.trackingCode()).toList(),
                notes.isBlank() ? null : notes, hour(s.eta()), distance, window);
    }

    private static String hour(Instant instant) {
        if (instant == null) {
            return null;
        }
        Duration until = Duration.between(Instant.now(), instant);
        return HOUR.format(instant) + (until.isNegative() ? "" : " (en " + until.toMinutes() + " min)");
    }
}
