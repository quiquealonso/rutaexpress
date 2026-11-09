package com.rutaexpress.geo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Tiempo y distancia por carretera entre dos puntos (claves = coordenadas redondeadas a ~1 m). */
@Entity
@Table(name = "distance_cache")
@IdClass(DistanceCache.Key.class)
class DistanceCache {

    @Id
    @Column(name = "origin_key")
    private String originKey;

    @Id
    @Column(name = "destination_key")
    private String destinationKey;

    @Column(nullable = false)
    private long seconds;

    @Column(nullable = false)
    private long meters;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DistanceCache() {
    }

    DistanceCache(String originKey, String destinationKey, long seconds, long meters) {
        this.originKey = originKey;
        this.destinationKey = destinationKey;
        this.seconds = seconds;
        this.meters = meters;
    }

    String getOriginKey() {
        return originKey;
    }

    String getDestinationKey() {
        return destinationKey;
    }

    long getSeconds() {
        return seconds;
    }

    long getMeters() {
        return meters;
    }

    static class Key implements Serializable {
        private String originKey;
        private String destinationKey;

        Key() {
        }

        Key(String originKey, String destinationKey) {
            this.originKey = originKey;
            this.destinationKey = destinationKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(originKey, k.originKey)
                    && Objects.equals(destinationKey, k.destinationKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(originKey, destinationKey);
        }
    }
}
