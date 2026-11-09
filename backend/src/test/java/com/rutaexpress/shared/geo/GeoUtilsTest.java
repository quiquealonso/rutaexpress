package com.rutaexpress.shared.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    @Test
    void polylineRoundTrip() {
        List<GeoPoint> points = List.of(new GeoPoint(39.46990, -0.37630), new GeoPoint(39.47060, -0.37200),
                new GeoPoint(39.48000, -0.35700));
        List<GeoPoint> decoded = Polyline.decode(Polyline.encode(points));
        assertThat(decoded).hasSize(3);
        for (int i = 0; i < points.size(); i++) {
            assertThat(decoded.get(i).lat()).isCloseTo(points.get(i).lat(), within(1e-5));
            assertThat(decoded.get(i).lon()).isCloseTo(points.get(i).lon(), within(1e-5));
        }
    }

    @Test
    void decodesGoogleReferenceExample() {
        // Ejemplo de la documentación del algoritmo de polilíneas
        List<GeoPoint> p = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(p).hasSize(3);
        assertThat(p.get(0).lat()).isCloseTo(38.5, within(1e-5));
        assertThat(p.get(0).lon()).isCloseTo(-120.2, within(1e-5));
        assertThat(p.get(2).lat()).isCloseTo(43.252, within(1e-5));
    }

    @Test
    void haversineValenciaMadrid() {
        double d = new GeoPoint(39.4699, -0.3763).distanceTo(new GeoPoint(40.4168, -3.7038));
        assertThat(d).isBetween(300_000.0, 310_000.0);
    }

    @Test
    void distanceToLine() {
        List<GeoPoint> line = List.of(new GeoPoint(39.47, -0.38), new GeoPoint(39.47, -0.36));
        // Un punto sobre la línea
        assertThat(Geometries.distanceToLineMeters(new GeoPoint(39.47, -0.37), line)).isLessThan(1);
        // ~0.001° de latitud ≈ 110 m al norte
        assertThat(Geometries.distanceToLineMeters(new GeoPoint(39.471, -0.37), line)).isBetween(100.0, 120.0);
    }

    @Test
    void polygonClosesAutomatically() {
        var polygon = Geometries.polygon(List.of(new GeoPoint(0.0, 0.0), new GeoPoint(0.0, 1.0), new GeoPoint(1.0, 1.0)));
        assertThat(polygon.getExteriorRing().getNumPoints()).isEqualTo(4);
        assertThat(Geometries.toGeoPoints(polygon)).hasSize(3);
    }
}
