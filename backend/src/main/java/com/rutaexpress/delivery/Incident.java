package com.rutaexpress.delivery;

import com.rutaexpress.shared.events.EventSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class Incident {

    public enum Type {
        BREAKDOWN, ACCIDENT, DELAY, TRAFFIC, ROAD_CLOSED, DAMAGED_PARCEL, OTHER
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "route_id")
    private UUID routeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    protected Incident() {
    }

    public Incident(UUID driverId, UUID routeId, Type type, String description, Point location, EventSource source) {
        this.driverId = driverId;
        this.routeId = routeId;
        this.type = type;
        this.description = description;
        this.location = location;
        this.source = source;
    }

    public void resolve(String by) {
        this.resolvedAt = Instant.now();
        this.resolvedBy = by;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Point getLocation() {
        return location;
    }

    public EventSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }
}
