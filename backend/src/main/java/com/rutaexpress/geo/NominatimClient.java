package com.rutaexpress.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.rutaexpress.geo.GeoModels.AddressQuery;
import com.rutaexpress.geo.GeoModels.GeocodeResult;
import com.rutaexpress.shared.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/** Cliente de Nominatim. Con el servicio público se respeta su política: 1 petición/s y User-Agent propio. */
@Component
class NominatimClient {

    private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);

    private final RestClient client;
    private final GeoProperties properties;
    private final Object rateLock = new Object();
    private long lastRequestAt;

    NominatimClient(RestClient.Builder builder, GeoProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        this.client = builder.clone()
                .baseUrl(properties.nominatimUrl())
                .requestFactory(factory)
                .defaultHeader("User-Agent", properties.userAgent())
                .defaultHeader("Accept-Language", "es")
                .build();
    }

    Optional<GeocodeResult> geocode(AddressQuery query) {
        throttle();
        try {
            JsonNode results = client.get()
                    .uri(u -> u.path("/search")
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("addressdetails", 0)
                            .queryParam("countrycodes", properties.countryCodes())
                            .queryParam("q", query.freeText())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (results == null || !results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode best = results.get(0);
            GeoPoint point = new GeoPoint(best.path("lat").asDouble(), best.path("lon").asDouble());
            return Optional.of(new GeocodeResult(point, confidence(best), best.path("display_name").asText(null),
                    "NOMINATIM"));
        } catch (RestClientException e) {
            log.warn("Nominatim no disponible para '{}': {}", query.freeText(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Nominatim no da una confianza explícita: se estima por el tipo de resultado.
     * Un portal (house) es exacto; una calle o un barrio, aproximado.
     */
    private static double confidence(JsonNode result) {
        String type = result.path("addresstype").asText(result.path("type").asText(""));
        double base = switch (type) {
            case "house", "building", "residential_building", "apartments" -> 0.95;
            case "road", "street", "pedestrian", "residential" -> 0.65;
            case "neighbourhood", "suburb", "quarter" -> 0.4;
            case "postcode" -> 0.35;
            case "city", "town", "village" -> 0.2;
            default -> 0.5;
        };
        double importance = result.path("importance").asDouble(0.5);
        return Math.min(1.0, base * 0.85 + importance * 0.15);
    }

    private void throttle() {
        if (!properties.nominatimPublic()) {
            return;
        }
        synchronized (rateLock) {
            long wait = lastRequestAt + 1100 - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }
}
