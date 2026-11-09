package com.rutaexpress.tracking;

import com.rutaexpress.shared.error.NotFoundException;
import com.rutaexpress.shared.security.CurrentUser;
import com.rutaexpress.tracking.TrackingDtos.LiveDriver;
import com.rutaexpress.tracking.TrackingDtos.PositionBatch;
import com.rutaexpress.tracking.TrackingDtos.PositionResponse;
import com.rutaexpress.tracking.TrackingDtos.PublicTracking;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Seguimiento")
@RestController
class TrackingController {

    private final TrackingService tracking;
    private final PublicTrackingService publicTracking;

    TrackingController(TrackingService tracking, PublicTrackingService publicTracking) {
        this.tracking = tracking;
        this.publicTracking = publicTracking;
    }

    /** Posiciones GPS en lote (la PWA las acumula y envía cada pocos segundos). */
    @PostMapping("/api/tracking/positions")
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Integer> positions(@Valid @RequestBody PositionBatch batch) {
        return Map.of("accepted", tracking.record(CurrentUser.driverId(), batch.positions()));
    }

    @GetMapping("/api/tracking/drivers")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    List<LiveDriver> live() {
        return tracking.liveDrivers();
    }

    @GetMapping("/api/tracking/drivers/{driverId}/trail")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    List<PositionResponse> trail(@PathVariable UUID driverId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                 Instant since) {
        return tracking.trail(driverId, since != null ? since : Instant.now().minus(Duration.ofHours(12)));
    }

    @GetMapping("/api/tracking/drivers/{driverId}/latest")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    ResponseEntity<PositionResponse> latest(@PathVariable UUID driverId) {
        return ResponseEntity.of(tracking.latestPosition(driverId));
    }

    /** Seguimiento público para el destinatario. */
    @GetMapping("/public/track/{trackingCode}")
    PublicTracking track(@PathVariable String trackingCode) {
        if (!trackingCode.matches("^[A-Za-z0-9]{6,20}$")) {
            throw new IllegalArgumentException("Código de seguimiento no válido");
        }
        PublicTracking result = publicTracking.track(trackingCode);
        if (result == null) {
            throw new NotFoundException("Paquete", trackingCode);
        }
        return result;
    }
}
