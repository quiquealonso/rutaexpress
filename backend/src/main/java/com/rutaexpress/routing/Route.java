package com.rutaexpress.routing;

import com.rutaexpress.shared.AuditableEntity;
import com.rutaexpress.shared.error.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Ruta de una carga (1–1 con Load). Cada recálculo crea una RouteVersion nueva; las paradas se conservan. */
@Entity
@Table(name = "route")
public class Route extends AuditableEntity {

    public enum Status {
        PLANNED, IN_PROGRESS, COMPLETED, CANCELLED
    }

    @Column(name = "load_id", nullable = false, unique = true)
    private UUID loadId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "depot_id", nullable = false)
    private UUID depotId;

    @Column(name = "depot_location", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point depotLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PLANNED;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(nullable = false)
    private String algorithm;

    @Column(name = "return_to_depot", nullable = false)
    private boolean returnToDepot;

    @Column(name = "total_distance_m", nullable = false)
    private long totalDistanceMeters;

    @Column(name = "total_duration_s", nullable = false)
    private long totalDurationSeconds;

    @Column(columnDefinition = "geography(LineString,4326)")
    private LineString geometry;

    @Column(name = "estimated_end")
    private Instant estimatedEnd;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_reoptimized_at")
    private Instant lastReoptimizedAt;

    /** Set (no List): con JOIN FETCH de las paradas y sus paquetes, una List duplicaría las paradas. */
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private Set<Stop> stops = new LinkedHashSet<>();

    /** Bloqueo optimista: conductor, panel y MCP pueden tocar la ruta a la vez. */
    @Version
    @Column(name = "lock_version")
    private long lockVersion;

    protected Route() {
    }

    public Route(UUID loadId, UUID driverId, UUID depotId, Point depotLocation, boolean returnToDepot,
                 String algorithm) {
        this.loadId = loadId;
        this.driverId = driverId;
        this.depotId = depotId;
        this.depotLocation = depotLocation;
        this.returnToDepot = returnToDepot;
        this.algorithm = algorithm;
    }

    public void addStop(Stop stop) {
        stops.add(stop);
    }

    public void start(Instant now) {
        if (status == Status.PLANNED) {
            status = Status.IN_PROGRESS;
            startedAt = now;
        } else if (status != Status.IN_PROGRESS) {
            throw new BusinessException("route-not-active", "La ruta ya no está activa");
        }
    }

    public void ensureActive() {
        if (status != Status.PLANNED && status != Status.IN_PROGRESS) {
            throw new BusinessException("route-not-active", "La ruta ya no está activa (" + status + ")");
        }
    }

    public boolean isActive() {
        return status == Status.PLANNED || status == Status.IN_PROGRESS;
    }

    public void complete(Instant now) {
        status = Status.COMPLETED;
        completedAt = now;
    }

    public void applyVersion(int version, String algorithm, long distanceMeters, long durationSeconds,
                             LineString geometry, Instant estimatedEnd, Instant now) {
        this.currentVersion = version;
        this.algorithm = algorithm;
        this.totalDistanceMeters = distanceMeters;
        this.totalDurationSeconds = durationSeconds;
        this.geometry = geometry;
        this.estimatedEnd = estimatedEnd;
        this.lastReoptimizedAt = now;
    }

    public void setEstimatedEnd(Instant estimatedEnd) {
        this.estimatedEnd = estimatedEnd;
    }

    public UUID getLoadId() {
        return loadId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getDepotId() {
        return depotId;
    }

    public Point getDepotLocation() {
        return depotLocation;
    }

    public Status getStatus() {
        return status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public boolean isReturnToDepot() {
        return returnToDepot;
    }

    public long getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public long getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public LineString getGeometry() {
        return geometry;
    }

    public Instant getEstimatedEnd() {
        return estimatedEnd;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getLastReoptimizedAt() {
        return lastReoptimizedAt;
    }

    public Set<Stop> getStops() {
        return stops;
    }
}
