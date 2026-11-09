package com.rutaexpress.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param algorithm             NEAREST_NEIGHBOR, TWO_OPT u OR_TOOLS
 * @param timeLimit             tiempo máximo de cálculo de OR-Tools / 2-opt
 * @param returnToDepot         si la ruta termina en el almacén
 * @param baseServiceSeconds    tiempo fijo por parada (aparcar, llamar...)
 * @param perParcelSeconds      tiempo extra por cada paquete adicional de la parada
 * @param minReoptimizeInterval  evita recalcular en bucle por desvíos sucesivos
 */
@ConfigurationProperties(prefix = "rutaexpress.routing")
public record RoutingProperties(String algorithm, Duration timeLimit, boolean returnToDepot,
                                long baseServiceSeconds, long perParcelSeconds,
                                Duration minReoptimizeInterval) {

    public RoutingProperties {
        algorithm = algorithm == null ? "OR_TOOLS" : algorithm;
        timeLimit = timeLimit == null ? Duration.ofSeconds(3) : timeLimit;
        baseServiceSeconds = baseServiceSeconds <= 0 ? 120 : baseServiceSeconds;
        perParcelSeconds = perParcelSeconds <= 0 ? 30 : perParcelSeconds;
        minReoptimizeInterval = minReoptimizeInterval == null ? Duration.ofMinutes(5) : minReoptimizeInterval;
    }
}
