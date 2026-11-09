package com.rutaexpress.shared.geo;

import java.util.ArrayList;
import java.util.List;

/** Codificación/decodificación de polilíneas (algoritmo de Google, precisión 5), el formato de OSRM. */
public final class Polyline {

    private Polyline() {
    }

    public static List<GeoPoint> decode(String encoded) {
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        long lat = 0, lon = 0;
        while (index < encoded.length()) {
            long[] r = next(encoded, index);
            lat += r[0];
            index = (int) r[1];
            r = next(encoded, index);
            lon += r[0];
            index = (int) r[1];
            points.add(new GeoPoint(lat / 1e5, lon / 1e5));
        }
        return points;
    }

    public static String encode(List<GeoPoint> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0, prevLon = 0;
        for (GeoPoint p : points) {
            long lat = Math.round(p.lat() * 1e5);
            long lon = Math.round(p.lon() * 1e5);
            write(sb, lat - prevLat);
            write(sb, lon - prevLon);
            prevLat = lat;
            prevLon = lon;
        }
        return sb.toString();
    }

    private static long[] next(String s, int index) {
        long result = 0;
        int shift = 0, b;
        do {
            b = s.charAt(index++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[]{value, index};
    }

    private static void write(StringBuilder sb, long value) {
        long v = value < 0 ? ~(value << 1) : (value << 1);
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }
}
