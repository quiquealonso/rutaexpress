package com.rutaexpress.routing;

import com.rutaexpress.shared.error.BusinessException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Una parada agrupa todos los paquetes que van a la misma dirección. */
@Entity
@Table(name = "stop")
public class Stop {

    public enum Status {
        PENDING, DELIVERED, FAILED, SKIPPED;

        /** Una parada saltada sigue pendiente: se deja para el final. */
        public boolean isOpen() {
            return this == PENDING || this == SKIPPED;
        }
    }

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(nullable = false)
    private int sequence;

    /** Posición según el orden de escaneo: la referencia para medir lo que ahorra la optimización. */
    @Column(name = "scan_order", nullable = false)
    private int scanOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "stop_parcel", joinColumns = @JoinColumn(name = "stop_id"))
    @Column(name = "parcel_id")
    private Set<UUID> parcelIds = new LinkedHashSet<>();

    @Column(name = "service_seconds", nullable = false)
    private long serviceSeconds;

    @Column(name = "window_start")
    private LocalTime windowStart;

    @Column(name = "window_end")
    private LocalTime windowEnd;

    @Column
    private Instant eta;

    /** ETA de la primera planificación, para comparar lo planificado con lo real. */
    @Column(name = "planned_eta")
    private Instant plannedEta;

    @Column(name = "leg_duration_s", nullable = false)
    private long legDurationSeconds;

    @Column(name = "leg_distance_m", nullable = false)
    private long legDistanceMeters;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "lock_version")
    private long lockVersion;

    protected Stop() {
    }

    public Stop(Route route, int sequence, UUID addressId, String addressLine, Point location, long serviceSeconds) {
        this.route = route;
        this.sequence = sequence;
        this.scanOrder = sequence;
        this.addressId = addressId;
        this.addressLine = addressLine;
        this.location = location;
        this.serviceSeconds = serviceSeconds;
    }

    public void markDelivered(Instant now) {
        ensureOpen();
        status = Status.DELIVERED;
        completedAt = now;
        attempts++;
    }

    public void markFailed(String reason, Instant now) {
        ensureOpen();
        status = Status.FAILED;
        failReason = reason;
        completedAt = now;
        attempts++;
    }

    /** Ausencia con reintento o "saltar": queda pendiente al final de la ruta. */
    public void defer(String reason, boolean countsAsAttempt) {
        ensureOpen();
        status = Status.SKIPPED;
        failReason = reason;
        if (countsAsAttempt) {
            attempts++;
        }
    }

    private void ensureOpen() {
        if (!status.isOpen()) {
            throw new BusinessException("stop-closed", "La parada ya está cerrada (" + status + ")");
        }
    }

    public void plan(int sequence, Instant eta, long legDurationSeconds, long legDistanceMeters) {
        this.sequence = sequence;
        this.eta = eta;
        if (this.plannedEta == null) {
            this.plannedEta = eta;
        }
        this.legDurationSeconds = legDurationSeconds;
        this.legDistanceMeters = legDistanceMeters;
    }

    public void setEta(Instant eta) {
        this.eta = eta;
    }

    public void setWindow(LocalTime start, LocalTime end) {
        this.windowStart = start;
        this.windowEnd = end;
    }

    public UUID getId() {
        return id;
    }

    public Route getRoute() {
        return route;
    }

    public int getSequence() {
        return sequence;
    }

    public int getScanOrder() {
        return scanOrder;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public Point getLocation() {
        return location;
    }

    public Set<UUID> getParcelIds() {
        return parcelIds;
    }

    public long getServiceSeconds() {
        return serviceSeconds;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public Instant getEta() {
        return eta;
    }

    public Instant getPlannedEta() {
        return plannedEta;
    }

    public long getLegDurationSeconds() {
        return legDurationSeconds;
    }

    public long getLegDistanceMeters() {
        return legDistanceMeters;
    }

    public String getFailReason() {
        return failReason;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
