package com.rutaexpress.delivery;

import com.rutaexpress.delivery.DeliveryDtos.DashboardResponse;
import com.rutaexpress.delivery.DeliveryDtos.DirectionsResponse;
import com.rutaexpress.delivery.DeliveryDtos.FailRequest;
import com.rutaexpress.delivery.DeliveryDtos.IncidentRequest;
import com.rutaexpress.delivery.DeliveryDtos.IncidentResponse;
import com.rutaexpress.delivery.DeliveryDtos.PositionRequest;
import com.rutaexpress.delivery.DeliveryDtos.ProofResponse;
import com.rutaexpress.routing.RoutingDtos.RouteDetail;
import com.rutaexpress.routing.RoutingDtos.StopDetail;
import com.rutaexpress.shared.events.EventSource;
import com.rutaexpress.shared.geo.GeoPoint;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Ejecución del reparto")
@RestController
class DeliveryController {

    private final DeliveryService delivery;

    DeliveryController(DeliveryService delivery) {
        this.delivery = delivery;
    }

    @PostMapping("/api/routes/{id}/start")
    RouteDetail start(@PathVariable UUID id) {
        return delivery.startRoute(id);
    }

    /** Recalcula desde la posición actual (la del cuerpo o, si no viene, la última GPS conocida). */
    @PostMapping("/api/routes/{id}/reoptimize")
    RouteDetail reoptimize(@PathVariable UUID id, @Valid @RequestBody(required = false) PositionRequest body) {
        return delivery.reoptimize(id, body == null ? null : body.position(), EventSource.API);
    }

    @GetMapping("/api/routes/{id}/directions")
    DirectionsResponse directions(@PathVariable UUID id, @RequestParam(required = false) UUID stopId) {
        return delivery.directions(id, stopId);
    }

    /** Entrega con prueba (firma o foto, multipart). */
    @PostMapping(path = "/api/stops/{id}/deliver", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    StopDetail deliver(@PathVariable UUID id,
                       @RequestParam ProofOfDelivery.Type proofType,
                       @RequestParam(required = false) String receiverName,
                       @RequestParam(required = false) Double lat,
                       @RequestParam(required = false) Double lon,
                       @RequestPart(name = "file", required = false) MultipartFile file) {
        GeoPoint position = lat != null && lon != null ? new GeoPoint(lat, lon) : null;
        return delivery.deliver(id, proofType, trim(receiverName), file, position, EventSource.API);
    }

    @PostMapping("/api/stops/{id}/fail")
    StopDetail fail(@PathVariable UUID id, @Valid @RequestBody FailRequest request) {
        return delivery.fail(id, request.reason(), request.note(), request.retryLater(), request.position(),
                EventSource.API);
    }

    @PostMapping("/api/stops/{id}/skip")
    RouteDetail skip(@PathVariable UUID id, @Valid @RequestBody(required = false) PositionRequest body) {
        return delivery.skip(id, body == null ? null : body.position(), EventSource.API);
    }

    @GetMapping("/api/stops/{id}/proof")
    ProofResponse proof(@PathVariable UUID id) {
        return delivery.proof(id);
    }

    @GetMapping("/api/stops/{id}/proof/file")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    ResponseEntity<InputStreamResource> proofFile(@PathVariable UUID id) {
        DeliveryService.ProofFile file = delivery.proofFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(file.content()));
    }

    @PostMapping("/api/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DRIVER')")
    IncidentResponse report(@Valid @RequestBody IncidentRequest request) {
        return delivery.reportIncident(request.type(), request.description(), request.position(), EventSource.API);
    }

    @GetMapping("/api/incidents")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    List<IncidentResponse> incidents(@RequestParam(defaultValue = "false") boolean open) {
        return delivery.incidents(open);
    }

    @PostMapping("/api/incidents/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    IncidentResponse resolve(@PathVariable UUID id) {
        return delivery.resolve(id);
    }

    @GetMapping("/api/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    DashboardResponse dashboard() {
        return delivery.dashboard();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
