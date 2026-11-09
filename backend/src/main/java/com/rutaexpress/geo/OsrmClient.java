package com.rutaexpress.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.rutaexpress.geo.GeoModels.DirectionStep;
import com.rutaexpress.geo.GeoModels.DistanceMatrix;
import com.rutaexpress.geo.GeoModels.RouteGeometry;
import com.rutaexpress.geo.GeoModels.RouteLeg;
import com.rutaexpress.shared.geo.GeoPoint;
import com.rutaexpress.shared.geo.Polyline;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Cliente del servidor OSRM propio: servicio table (matriz) y route (geometría e indicaciones). */
@Component
class OsrmClient {

    private final RestClient client;

    OsrmClient(RestClient.Builder builder, GeoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        this.client = builder.clone().baseUrl(properties.osrmUrl()).requestFactory(factory).build();
    }

    DistanceMatrix table(List<GeoPoint> points) {
        JsonNode body = client.get()
                .uri("/table/v1/driving/{coords}?annotations=duration,distance", coordinates(points))
                .retrieve()
                .body(JsonNode.class);
        ensureOk(body);
        int n = points.size();
        long[][] seconds = new long[n][n];
        long[][] meters = new long[n][n];
        JsonNode durations = body.path("durations");
        JsonNode distances = body.path("distances");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                JsonNode d = durations.path(i).path(j);
                JsonNode m = distances.path(i).path(j);
                // null = no hay ruta entre esos puntos: coste prohibitivo
                seconds[i][j] = d.isNull() || d.isMissingNode() ? Integer.MAX_VALUE / 4 : Math.round(d.asDouble());
                meters[i][j] = m.isNull() || m.isMissingNode() ? Integer.MAX_VALUE / 4 : Math.round(m.asDouble());
            }
        }
        return new DistanceMatrix(seconds, meters, "OSRM");
    }

    RouteGeometry route(List<GeoPoint> points, boolean withSteps) {
        JsonNode body = client.get()
                .uri("/route/v1/driving/{coords}?overview=full&geometries=polyline&steps={steps}",
                        coordinates(points), withSteps)
                .retrieve()
                .body(JsonNode.class);
        ensureOk(body);
        JsonNode route = body.path("routes").path(0);
        List<RouteLeg> legs = new ArrayList<>();
        for (JsonNode leg : route.path("legs")) {
            List<DirectionStep> steps = new ArrayList<>();
            for (JsonNode step : leg.path("steps")) {
                JsonNode maneuver = step.path("maneuver");
                JsonNode loc = maneuver.path("location");
                steps.add(new DirectionStep(
                        instruction(maneuver.path("type").asText(), maneuver.path("modifier").asText(""),
                                step.path("name").asText("")),
                        step.path("name").asText(""),
                        Math.round(step.path("distance").asDouble()),
                        Math.round(step.path("duration").asDouble()),
                        new GeoPoint(loc.path(1).asDouble(), loc.path(0).asDouble())));
            }
            legs.add(new RouteLeg(Math.round(leg.path("duration").asDouble()),
                    Math.round(leg.path("distance").asDouble()), steps));
        }
        return new RouteGeometry(Polyline.decode(route.path("geometry").asText("")), legs,
                Math.round(route.path("duration").asDouble()), Math.round(route.path("distance").asDouble()),
                "OSRM");
    }

    private static void ensureOk(JsonNode body) {
        if (body == null || !"Ok".equals(body.path("code").asText())) {
            throw new IllegalStateException("OSRM respondió con error: "
                    + (body == null ? "sin cuerpo" : body.path("message").asText(body.path("code").asText())));
        }
    }

    private static String coordinates(List<GeoPoint> points) {
        return points.stream()
                .map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p.lon(), p.lat()))
                .collect(Collectors.joining(";"));
    }

    /** Traduce la maniobra de OSRM a una indicación corta en castellano, apta para leer en voz alta. */
    static String instruction(String type, String modifier, String street) {
        String onto = street.isBlank() ? "" : " por " + street;
        String dir = switch (modifier) {
            case "left" -> "a la izquierda";
            case "right" -> "a la derecha";
            case "slight left" -> "ligeramente a la izquierda";
            case "slight right" -> "ligeramente a la derecha";
            case "sharp left" -> "fuerte a la izquierda";
            case "sharp right" -> "fuerte a la derecha";
            case "uturn" -> "cambio de sentido";
            case "straight" -> "recto";
            default -> "";
        };
        return switch (type) {
            case "depart" -> "Sal" + onto;
            case "arrive" -> "Has llegado a tu destino";
            case "turn", "end of road" -> ("uturn".equals(modifier) ? "Haz un cambio de sentido" : "Gira " + dir) + onto;
            case "continue", "new name" -> "Continúa " + (dir.isEmpty() ? "recto" : dir) + onto;
            case "merge" -> "Incorpórate" + onto;
            case "on ramp" -> "Toma la entrada" + onto;
            case "off ramp" -> "Toma la salida" + onto;
            case "fork" -> "En la bifurcación, mantente " + dir + onto;
            case "roundabout", "rotary" -> "En la rotonda, toma la salida" + onto;
            case "exit roundabout", "exit rotary" -> "Sal de la rotonda" + onto;
            default -> ("Sigue " + dir).trim() + onto;
        };
    }
}
