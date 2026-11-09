package com.rutaexpress.scanning;

import com.rutaexpress.shared.AuditableEntity;
import com.rutaexpress.shared.error.BusinessException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Sesión de carga: el repartidor escanea los paquetes al cargar la furgoneta. */
@Entity
@Table(name = "load_session")
public class Load extends AuditableEntity {

    public enum Status {
        OPEN, CLOSED
    }

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "depot_id", nullable = false)
    private UUID depotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Paquetes que se esperaba cargar al abrir la sesión (para el contador "escaneados / esperados"). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "load_expected_parcel", joinColumns = @JoinColumn(name = "load_id"))
    @Column(name = "parcel_id")
    private Set<UUID> expectedParcelIds = new LinkedHashSet<>();

    @Version
    private long version;

    protected Load() {
    }

    public Load(UUID driverId, UUID vehicleId, UUID depotId, Set<UUID> expectedParcelIds) {
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.depotId = depotId;
        this.expectedParcelIds.addAll(expectedParcelIds);
    }

    public void ensureOpen() {
        if (status != Status.OPEN) {
            throw new BusinessException("load-closed", "La sesión de carga ya está cerrada");
        }
    }

    public void close() {
        ensureOpen();
        this.status = Status.CLOSED;
        this.closedAt = Instant.now();
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getDepotId() {
        return depotId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Set<UUID> getExpectedParcelIds() {
        return expectedParcelIds;
    }
}
