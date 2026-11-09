package com.rutaexpress.catalog;

import com.rutaexpress.shared.geo.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record DepotRequest(@NotBlank @Size(max = 120) String name,
                               @NotBlank @Size(max = 255) String address,
                               @NotNull @Valid GeoPoint location) {
    }

    public record DepotResponse(UUID id, String name, String address, GeoPoint location) {
    }

    public record VehicleRequest(@NotBlank @Size(max = 15) String plate,
                                 @NotNull @DecimalMin("1") BigDecimal capacityKg,
                                 @NotNull @DecimalMin("0.1") BigDecimal capacityM3,
                                 @Min(1) int capacityParcels,
                                 @NotNull UUID depotId,
                                 Boolean active) {
    }

    public record VehicleResponse(UUID id, String plate, BigDecimal capacityKg, BigDecimal capacityM3,
                                  int capacityParcels, boolean active, UUID depotId, String depotName) {
    }

    public record ZoneRequest(@NotBlank @Size(max = 120) String name,
                              @NotNull UUID depotId,
                              @NotNull @Size(min = 3) List<@Valid GeoPoint> area,
                              @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {
    }

    public record ZoneResponse(UUID id, String name, UUID depotId, String depotName, List<GeoPoint> area,
                               String color) {
    }

    public record CreateDriverRequest(@NotBlank @Size(max = 120) String name,
                                      @Size(max = 30) String phone,
                                      @Size(max = 30) String licenseNumber,
                                      @NotNull UUID depotId,
                                      UUID zoneId,
                                      UUID defaultVehicleId,
                                      @NotBlank @Email String email,
                                      @NotBlank @Size(min = 10, max = 100) String password) {
    }

    public record UpdateDriverRequest(@NotBlank @Size(max = 120) String name,
                                      @Size(max = 30) String phone,
                                      @Size(max = 30) String licenseNumber,
                                      @NotNull UUID depotId,
                                      UUID zoneId,
                                      UUID defaultVehicleId,
                                      Driver.Status status) {
    }

    public record DriverResponse(UUID id, String name, String phone, String licenseNumber, String status,
                                 UUID userId, UUID depotId, String depotName, UUID zoneId, String zoneName,
                                 UUID defaultVehicleId, String defaultVehiclePlate) {
    }

    /** Vista mínima que otros módulos necesitan de un repartidor. */
    public record DriverSummary(UUID id, String name, String phone, UUID depotId, UUID zoneId,
                                UUID defaultVehicleId) {
    }

    public record DepotSummary(UUID id, String name, GeoPoint location) {
    }
}
