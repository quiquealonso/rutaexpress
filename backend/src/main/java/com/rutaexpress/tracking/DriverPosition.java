package com.rutaexpress.tracking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/** Posición GPS. La tabla está particionada por día (ver V1 y PartitionMaintenanceJob). */
@Entity
@Table(name = "driver_position")
public class DriverPosition {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    private Double heading;

    @Column(name = "accuracy_m")
    private Double accuracyMeters;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected DriverPosition() {
    }

    public DriverPosition(UUID driverId, Point location, Double speedKmh, Double heading, Double accuracyMeters,
                          Instant recordedAt) {
        this.driverId = driverId;
        this.location = location;
        this.speedKmh = speedKmh;
        this.heading = heading;
        this.accuracyMeters = accuracyMeters;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public Point getLocation() {
        return location;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public Double getHeading() {
        return heading;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
