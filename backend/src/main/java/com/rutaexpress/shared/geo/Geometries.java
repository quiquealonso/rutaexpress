package com.rutaexpress.shared.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.List;

/** Conversión entre los tipos JTS (x = longitud, y = latitud, SRID 4326) y los DTOs. */
public final class Geometries {

    public static final int WGS84 = 4326;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

    private Geometries() {
    }

    public static Point point(GeoPoint p) {
        return p == null ? null : point(p.lat(), p.lon());
    }

    public static Point point(double lat, double lon) {
        return FACTORY.createPoint(new Coordinate(lon, lat));
    }

    public static GeoPoint toGeoPoint(Point p) {
        return p == null ? null : new GeoPoint(p.getY(), p.getX());
    }

    public static LineString lineString(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        return FACTORY.createLineString(points.stream().map(p -> new Coordinate(p.lon(), p.lat()))
                .toArray(Coordinate[]::new));
    }

    public static List<GeoPoint> toGeoPoints(LineString line) {
        List<GeoPoint> result = new ArrayList<>();
        if (line != null) {
            for (Coordinate c : line.getCoordinates()) {
                result.add(new GeoPoint(c.y, c.x));
            }
        }
        return result;
    }

    /** Polígono a partir de una lista de vértices; se cierra automáticamente si hace falta. */
    public static Polygon polygon(List<GeoPoint> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("Un polígono necesita al menos 3 vértices");
        }
        List<Coordinate> coords = new ArrayList<>(vertices.stream()
                .map(p -> new Coordinate(p.lon(), p.lat())).toList());
        if (!coords.getFirst().equals2D(coords.getLast())) {
            coords.add(new Coordinate(coords.getFirst()));
        }
        return FACTORY.createPolygon(coords.toArray(Coordinate[]::new));
    }

    public static List<GeoPoint> toGeoPoints(Polygon polygon) {
        List<GeoPoint> result = new ArrayList<>();
        if (polygon != null) {
            Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
            for (int i = 0; i < coords.length - 1; i++) {
                result.add(new GeoPoint(coords[i].y, coords[i].x));
            }
        }
        return result;
    }

    /** Distancia aproximada en metros de un punto a una polilínea (suficiente para detectar desvíos). */
    public static double distanceToLineMeters(GeoPoint p, List<GeoPoint> line) {
        if (line == null || line.isEmpty()) {
            return Double.MAX_VALUE;
        }
        if (line.size() == 1) {
            return p.distanceTo(line.getFirst());
        }
        double cosLat = Math.cos(Math.toRadians(p.lat()));
        double min = Double.MAX_VALUE;
        for (int i = 0; i < line.size() - 1; i++) {
            GeoPoint a = line.get(i);
            GeoPoint b = line.get(i + 1);
            // Proyección equirectangular local centrada en p (en metros)
            double ax = (a.lon() - p.lon()) * cosLat * 111_320, ay = (a.lat() - p.lat()) * 110_540;
            double bx = (b.lon() - p.lon()) * cosLat * 111_320, by = (b.lat() - p.lat()) * 110_540;
            double dx = bx - ax, dy = by - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            double cx = ax + t * dx, cy = ay + t * dy;
            min = Math.min(min, Math.sqrt(cx * cx + cy * cy));
        }
        return min;
    }
}
