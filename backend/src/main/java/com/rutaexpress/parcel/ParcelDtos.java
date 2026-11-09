package com.rutaexpress.parcel;

import com.rutaexpress.shared.geo.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ParcelDtos {

    private ParcelDtos() {
    }

    public record RecipientDto(@NotBlank @Size(max = 120) String name,
                               @Size(max = 30) String phone,
                               @Email @Size(max = 120) String email) {
    }

    public record AddressDto(@NotBlank @Size(max = 200) String street,
                             @Size(max = 20) String number,
                             @Size(max = 120) String extra,
                             @NotBlank @Pattern(regexp = "^[0-9]{5}$", message = "Código postal de 5 dígitos")
                             String postalCode,
                             @NotBlank @Size(max = 120) String city,
                             @Size(max = 60) String country,
                             /* Si se indica, no se geocodifica (p. ej. el cliente ya la tiene) */
                             @Valid GeoPoint location) {
    }

    public record CreateParcelRequest(@Pattern(regexp = "^[A-Z0-9]{6,20}$") String trackingCode,
                                      @NotNull @Valid RecipientDto recipient,
                                      @NotNull @Valid AddressDto address,
                                      @NotNull @DecimalMin("0.01") BigDecimal weightKg,
                                      @Min(1) @Max(400) Integer lengthCm,
                                      @Min(1) @Max(400) Integer widthCm,
                                      @Min(1) @Max(400) Integer heightCm,
                                      LocalTime windowStart,
                                      LocalTime windowEnd,
                                      @Size(max = 500) String notes,
                                      @NotNull UUID depotId,
                                      UUID assignedDriverId) {
    }

    public record UpdateParcelRequest(@NotNull @Valid RecipientDto recipient,
                                      @NotNull @Valid AddressDto address,
                                      @NotNull @DecimalMin("0.01") BigDecimal weightKg,
                                      Integer lengthCm,
                                      Integer widthCm,
                                      Integer heightCm,
                                      LocalTime windowStart,
                                      LocalTime windowEnd,
                                      @Size(max = 500) String notes,
                                      UUID assignedDriverId) {
    }

    public record AddressResponse(UUID id, String street, String number, String extra, String postalCode,
                                  String city, String country, GeoPoint location, Double geocodeQuality,
                                  String geocodeProvider, boolean needsReview, String oneLine) {
    }

    public record ParcelResponse(UUID id, String trackingCode, String status, BigDecimal weightKg, Integer lengthCm,
                                 Integer widthCm, Integer heightCm, LocalTime windowStart, LocalTime windowEnd,
                                 String notes, UUID depotId, UUID assignedDriverId, int deliveryAttempts,
                                 RecipientDto recipient, AddressResponse address, Instant createdAt,
                                 Instant updatedAt) {
    }

    public record ParcelListItem(UUID id, String trackingCode, String status, String recipientName,
                                 String addressLine, String city, boolean needsReview, BigDecimal weightKg,
                                 UUID assignedDriverId, Instant createdAt) {
    }

    public record ParcelEventResponse(UUID id, String type, Instant occurredAt, String actor, String source,
                                      Map<String, Object> data) {
    }

    public record ParcelFilter(String status, UUID depotId, String q, Boolean needsReview, UUID assignedDriverId) {
    }

    public record ImportError(int row, String message) {
    }

    public record ImportResult(int created, int failed, int needsReview, List<ImportError> errors) {
    }

    public record LabelsRequest(@NotNull @Size(min = 1, max = 500) List<UUID> parcelIds) {
    }

    /** Lo que otros módulos (scanning, routing, delivery) necesitan de un paquete. */
    public record ParcelInfo(UUID id, String trackingCode, String status, UUID depotId, UUID assignedDriverId,
                             UUID addressId, String addressLine, GeoPoint location, boolean addressNeedsReview,
                             String recipientName, String recipientPhone, String notes, LocalTime windowStart,
                             LocalTime windowEnd, BigDecimal weightKg) {
    }

    /** Vista pública del seguimiento: sin datos personales. */
    public record PublicParcelView(UUID id, String trackingCode, String status, String city, String postalCode,
                                   List<PublicEvent> events) {
    }

    public record PublicEvent(String type, Instant occurredAt) {
    }

    public record StatusCountResponse(String status, long total) {
    }
}
