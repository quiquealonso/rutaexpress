package com.rutaexpress.scanning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ScanningDtos {

    private ScanningDtos() {
    }

    /** driverId solo lo indica tráfico; un repartidor siempre abre su propia carga. */
    public record OpenLoadRequest(UUID driverId, UUID vehicleId) {
    }

    public record ScanRequest(@NotBlank @Size(max = 64) String code,
                              @NotNull UUID clientScanId,
                              Instant scannedAt) {
    }

    public record ScanBatchRequest(@NotNull @Size(min = 1, max = 500) List<@Valid ScanRequest> scans) {
    }

    public record ScanResponse(UUID id, UUID clientScanId, String code, String result, UUID parcelId,
                               String message, Instant scannedAt, int scannedCount, int expectedCount) {
    }

    public record MissingParcel(UUID parcelId, String trackingCode, String addressLine) {
    }

    public record LoadSummary(UUID id, UUID driverId, UUID vehicleId, UUID depotId, String status, Instant openedAt,
                              Instant closedAt, int expectedCount, int scannedCount, int duplicateCount,
                              int unknownCount, int notExpectedCount, List<MissingParcel> missing,
                              List<ScanResponse> recentScans) {
    }

    /** Lo que routing necesita de una carga cerrada. */
    public record LoadInfo(UUID id, UUID driverId, UUID vehicleId, UUID depotId, String status, Instant closedAt) {
    }
}
