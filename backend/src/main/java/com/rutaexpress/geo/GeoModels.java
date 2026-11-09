package com.rutaexpress.geo;

import com.rutaexpress.shared.geo.GeoPoint;

import java.util.List;

/** Tipos públicos del módulo geo. */
public final class GeoModels {

    private GeoModels() {
    }

    public record AddressQuery(String street, String number, String postalCode, String city, String country) {

        public String normalized() {
            return String.join(" ", nz(street), nz(number), nz(postalCode), nz(city), nz(country))
                    .toLowerCase()
                    .replaceAll("[,.;]", " ")
                    .replaceAll("\\bc/\\s*", "calle ")
                    .replaceAll("\\bavda?\\b", "avenida")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        public String freeText() {
            return String.join(", ",
                    (nz(street) + " " + nz(number)).trim(), nz(postalCode) + " " + nz(city), nz(country))
                    .replaceAll("\\s+", " ").trim();
        }

        private static String nz(String s) {
            return s == null ? "" : s.trim();
        }
    }

    /** @param confidence 0..1; por debajo del umbral, la dirección se marca para revisión. */
    public record GeocodeResult(GeoPoint location, double confidence, String displayName, String provider) {
    }

    /**
     * Matriz N×N de tiempos (s) y distancias (m) por carretera. Asimétrica: por las direcciones prohibidas,
     * ir de A a B no cuesta lo mismo que de B a A.
     */
    public record DistanceMatrix(long[][] seconds, long[][] meters, String source) {

        public int size() {
            return seconds.length;
        }
    }

    public record DirectionStep(String instruction, String streetName, long distanceMeters, long durationSeconds,
                                GeoPoint location) {
    }

    public record RouteLeg(long durationSeconds, long distanceMeters, List<DirectionStep> steps) {
    }

    public record RouteGeometry(List<GeoPoint> points, List<RouteLeg> legs, long durationSeconds,
                                long distanceMeters, String source) {
    }
}
