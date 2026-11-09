package com.rutaexpress.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param osrmUrl          servidor OSRM propio (Docker), p. ej. http://osrm:5000
 * @param nominatimUrl     Nominatim propio o el público
 * @param nominatimPublic  si es el público, se respeta su límite de 1 petición/s
 * @param userAgent        identificación obligatoria para el Nominatim público
 * @param fallbackEnabled  si OSRM no responde, se estima con distancia en línea recta (útil en desarrollo)
 * @param fallbackSpeedKmh velocidad media urbana para la estimación de respaldo
 * @param fallbackDetourFactor factor de rodeo: la carretera nunca es una línea recta
 */
@ConfigurationProperties(prefix = "rutaexpress.geo")
public record GeoProperties(
        String osrmUrl,
        String nominatimUrl,
        boolean nominatimPublic,
        String userAgent,
        String countryCodes,
        boolean fallbackEnabled,
        double fallbackSpeedKmh,
        double fallbackDetourFactor,
        double minGeocodeConfidence,
        Duration timeout) {

    public GeoProperties {
        osrmUrl = osrmUrl == null ? "http://localhost:5000" : osrmUrl;
        nominatimUrl = nominatimUrl == null ? "https://nominatim.openstreetmap.org" : nominatimUrl;
        userAgent = userAgent == null ? "RutaExpress/0.1" : userAgent;
        countryCodes = countryCodes == null ? "es" : countryCodes;
        fallbackSpeedKmh = fallbackSpeedKmh <= 0 ? 25 : fallbackSpeedKmh;
        fallbackDetourFactor = fallbackDetourFactor <= 0 ? 1.35 : fallbackDetourFactor;
        minGeocodeConfidence = minGeocodeConfidence <= 0 ? 0.5 : minGeocodeConfidence;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }
}
