package com.rutaexpress.routing;

import com.rutaexpress.shared.geo.GeoPoint;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record ParcelBrief(UUID id, String trackingCode, String recipientName, String recipientPhone,
                              String notes) {
    }

    public record StopDetail(UUID id, UUID routeId, int sequence, String status, Instant eta, Instant plannedEta,
                             String addressLine, GeoPoint location, List<ParcelBrief> parcels, LocalTime windowStart,
                             LocalTime windowEnd, String failReason, int attempts, Instant completedAt,
                             long legDistanceMeters, long legDurationSeconds) {

        public String recipientNames() {
            return String.join(", ", parcels.stream().map(ParcelBrief::recipientName).distinct().toList());
        }
    }

    public record RouteDetail(UUID id, UUID loadId, UUID driverId, UUID depotId, GeoPoint depotLocation,
                              String status, int version, String algorithm, long totalDistanceMeters,
                              long totalDurationSeconds, Instant estimatedEnd, Instant startedAt,
                              Instant completedAt, List<GeoPoint> geometry, List<StopDetail> stops) {
    }

    public record RouteListItem(UUID id, UUID driverId, String driverName, String status, int version,
                                String algorithm, int totalStops, int delivered, int failed, int pending,
                                long totalDistanceMeters, Instant estimatedEnd, Instant startedAt) {
    }

    public record RouteSummary(UUID routeId, String status, int totalStops, int delivered, int failed, int pending,
                               int parcelsPending, long remainingDistanceMeters, long remainingDurationSeconds,
                               Instant estimatedEnd) {
    }

    public record RouteVersionResponse(UUID id, int version, String algorithm, String reason,
                                       long totalDistanceMeters, long totalDurationSeconds, long computeMillis,
                                       String matrixSource, Instant createdAt, List<UUID> stopOrder,
                                       List<GeoPoint> geometry) {
    }

    public record AlgorithmComparison(String algorithm, long totalDurationSeconds, long computeMillis,
                                      long savedSecondsVsScanOrder, double savedPercentVsScanOrder) {
    }

    public record ComparisonResponse(UUID routeId, int stops, String matrixSource, long scanOrderDurationSeconds,
                                     List<AlgorithmComparison> results) {
    }

    public record DayStats(int routes, int inProgress, int completed, int deliveredStops, int failedStops,
                           long averageSecondsPerStop) {
    }

    /** ETA de un paquete para el seguimiento público. */
    public record ParcelEta(UUID routeId, UUID driverId, String stopStatus, Instant eta, int stopsBefore,
                            String routeStatus) {
    }
}
