package com.rutaexpress.delivery;

import com.rutaexpress.shared.geo.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    public enum FailReason {
        ABSENT("Destinatario ausente"),
        WRONG_ADDRESS("Dirección incorrecta"),
        REFUSED("Rechazado por el destinatario"),
        DAMAGED("Paquete dañado"),
        INACCESSIBLE("Acceso imposible"),
        OTHER("Otro motivo");

        private final String label;

        FailReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record FailRequest(@NotNull FailReason reason, @Size(max = 300) String note, boolean retryLater,
                              @Valid GeoPoint position) {
    }

    public record PositionRequest(@Valid GeoPoint position) {
    }

    public record IncidentRequest(@NotNull Incident.Type type, @NotBlank @Size(max = 1000) String description,
                                  @Valid GeoPoint position) {
    }

    public record IncidentResponse(UUID id, UUID driverId, UUID routeId, String type, String description,
                                   GeoPoint location, String source, Instant createdAt, Instant resolvedAt,
                                   String resolvedBy) {
    }

    public record ProofResponse(UUID id, UUID stopId, String type, String receiverName, String contentType,
                                Instant createdAt, boolean hasFile) {
    }

    public record DirectionsResponse(UUID stopId, String addressLine, long distanceMeters, long durationSeconds,
                                     List<Step> steps, List<GeoPoint> geometry, String googleMapsUrl, String wazeUrl,
                                     String source) {
    }

    public record Step(String instruction, long distanceMeters, long durationSeconds) {
    }

    public record DashboardResponse(int routesToday, int routesInProgress, int routesCompleted,
                                    List<com.rutaexpress.parcel.ParcelDtos.StatusCountResponse> parcelsByStatus,
                                    int failedStopsToday, int deliveredStopsToday, long averageSecondsPerStop,
                                    long openIncidents) {
    }
}
