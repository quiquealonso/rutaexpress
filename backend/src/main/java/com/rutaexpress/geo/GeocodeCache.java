package com.rutaexpress.geo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(name = "geocode_cache")
class GeocodeCache {

    /** SHA-256 de la dirección normalizada. */
    @Id
    @Column(name = "address_hash")
    private String addressHash;

    @Column(name = "normalized_address", nullable = false, length = 500)
    private String normalizedAddress;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "display_name", length = 500)
    private String displayName;

    @Column(nullable = false)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected GeocodeCache() {
    }

    GeocodeCache(String addressHash, String normalizedAddress, Point location, double confidence,
                 String displayName, String provider) {
        this.addressHash = addressHash;
        this.normalizedAddress = normalizedAddress;
        this.location = location;
        this.confidence = confidence;
        this.displayName = displayName;
        this.provider = provider;
    }

    Point getLocation() {
        return location;
    }

    double getConfidence() {
        return confidence;
    }

    String getDisplayName() {
        return displayName;
    }

    String getProvider() {
        return provider;
    }
}
