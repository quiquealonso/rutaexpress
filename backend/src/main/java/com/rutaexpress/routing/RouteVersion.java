package com.rutaexpress.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Foto inmutable de cada planificación: orden de paradas, algoritmo, coste y geometría. */
@Entity
@Immutable
@Table(name = "route_version")
public class RouteVersion {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String algorithm;

    @Column(nullable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stop_order", nullable = false, columnDefinition = "jsonb")
    private List<UUID> stopOrder;

    @Column(name = "total_distance_m", nullable = false)
    private long totalDistanceMeters;

    @Column(name = "total_duration_s", nullable = false)
    private long totalDurationSeconds;

    @Column(name = "compute_millis", nullable = false)
    private long computeMillis;

    @Column(name = "matrix_source", nullable = false)
    private String matrixSource;

    @Column(columnDefinition = "geography(LineString,4326)")
    private LineString geometry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RouteVersion() {
    }

    public RouteVersion(Route route, int version, String algorithm, String reason, List<UUID> stopOrder,
                        long totalDistanceMeters, long totalDurationSeconds, long computeMillis,
                        String matrixSource, LineString geometry) {
        this.route = route;
        this.version = version;
        this.algorithm = algorithm;
        this.reason = reason;
        this.stopOrder = List.copyOf(stopOrder);
        this.totalDistanceMeters = totalDistanceMeters;
        this.totalDurationSeconds = totalDurationSeconds;
        this.computeMillis = computeMillis;
        this.matrixSource = matrixSource;
        this.geometry = geometry;
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getReason() {
        return reason;
    }

    public List<UUID> getStopOrder() {
        return stopOrder;
    }

    public long getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public long getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public long getComputeMillis() {
        return computeMillis;
    }

    public String getMatrixSource() {
        return matrixSource;
    }

    public LineString getGeometry() {
        return geometry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
