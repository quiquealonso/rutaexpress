package com.rutaexpress.parcel;

import com.rutaexpress.parcel.ParcelDtos.AddressDto;
import com.rutaexpress.parcel.ParcelDtos.CreateParcelRequest;
import com.rutaexpress.parcel.ParcelDtos.RecipientDto;
import com.rutaexpress.shared.geo.GeoPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lee un CSV de paquetes (separador ";" o ","; cabecera obligatoria). Columnas reconocidas:
 * nombre, telefono, email, calle, numero, extra, cp, ciudad, peso, largo, ancho, alto,
 * franja_inicio, franja_fin, notas, codigo, lat, lon.
 */
final class CsvParcelParser {

    record Row(int number, CreateParcelRequest request, String error) {
    }

    private CsvParcelParser() {
    }

    static List<Row> parse(InputStream in, UUID depotId) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            headerLine = headerLine.replace("﻿", "");
            char sep = headerLine.chars().filter(c -> c == ';').count() >= headerLine.chars().filter(c -> c == ',').count()
                    ? ';' : ',';
            List<String> header = split(headerLine, sep).stream()
                    .map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
            String line;
            int number = 1;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = split(line, sep);
                Map<String, String> m = new HashMap<>();
                for (int i = 0; i < header.size() && i < values.size(); i++) {
                    m.put(header.get(i), values.get(i).trim());
                }
                try {
                    rows.add(new Row(number, toRequest(m, depotId), null));
                } catch (RuntimeException e) {
                    rows.add(new Row(number, null, e.getMessage()));
                }
            }
        }
        return rows;
    }

    private static CreateParcelRequest toRequest(Map<String, String> m, UUID depotId) {
        String name = required(m, "nombre");
        String street = required(m, "calle");
        String cp = required(m, "cp");
        String city = required(m, "ciudad");
        BigDecimal weight = new BigDecimal(required(m, "peso").replace(',', '.'));
        GeoPoint location = null;
        if (notBlank(m.get("lat")) && notBlank(m.get("lon"))) {
            location = new GeoPoint(Double.parseDouble(m.get("lat").replace(',', '.')),
                    Double.parseDouble(m.get("lon").replace(',', '.')));
        }
        return new CreateParcelRequest(
                notBlank(m.get("codigo")) ? m.get("codigo").toUpperCase(Locale.ROOT) : null,
                new RecipientDto(name, blank(m.get("telefono")), blank(m.get("email"))),
                new AddressDto(street, blank(m.get("numero")), blank(m.get("extra")), cp, city, "España", location),
                weight,
                integer(m.get("largo")), integer(m.get("ancho")), integer(m.get("alto")),
                time(m.get("franja_inicio")), time(m.get("franja_fin")),
                blank(m.get("notas")),
                depotId,
                null);
    }

    /** Separa respetando comillas dobles (con "" como comilla escapada). */
    static List<String> split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == sep && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static String required(Map<String, String> m, String key) {
        String v = m.get(key);
        if (!notBlank(v)) {
            throw new IllegalArgumentException("Falta la columna obligatoria '" + key + "'");
        }
        return v;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blank(String s) {
        return notBlank(s) ? s : null;
    }

    private static Integer integer(String s) {
        return notBlank(s) ? Integer.valueOf(s) : null;
    }

    private static LocalTime time(String s) {
        return notBlank(s) ? LocalTime.parse(s.length() == 4 ? "0" + s : s) : null;
    }
}
