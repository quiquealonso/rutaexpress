package com.rutaexpress.tracking;

import com.rutaexpress.shared.geo.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TrackingDtos {

    private TrackingDtos() {
    }

    public record PositionSample(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                                 @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
                                 @PositiveOrZero Double speedKmh,
                                 Double heading,
                                 @PositiveOrZero Double accuracyMeters,
                                 @NotNull Instant recordedAt) {
    }

    public record PositionBatch(@NotNull @Size(min = 1, max = 200) List<@Valid PositionSample> positions) {
    }

    public record PositionResponse(UUID driverId, GeoPoint location, Double speedKmh, Double heading,
                                   Double accuracyMeters, Instant recordedAt) {
    }

    public record LiveDriver(UUID driverId, String driverName, GeoPoint location, Double speedKmh,
                             Instant recordedAt, UUID routeId) {
    }

    /** Seguimiento público: sin datos personales; la ETA se da como franja de ±15 minutos. */
    public record PublicTracking(String trackingCode, String status, String statusLabel, String city,
                                 Instant etaFrom, Instant etaTo, Integer stopsBefore, Instant lastUpdate,
                                 List<PublicTrackingEvent> events) {
    }

    public record PublicTrackingEvent(String type, String label, Instant at) {
    }

    /** Mensaje que se envía por WebSocket al panel y a la app. */
    public record RealtimeMessage(String type, Object payload, Instant at) {
    }
}
