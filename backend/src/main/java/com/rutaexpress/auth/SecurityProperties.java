package com.rutaexpress.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "rutaexpress.security")
public record SecurityProperties(
        /* Secreto HMAC del JWT: mínimo 32 bytes, siempre desde variable de entorno en producción. */
        @NotBlank @Size(min = 32) String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration personalTokenTtl,
        int maxLoginAttempts,
        Duration lockDuration,
        List<String> corsAllowedOrigins) {

    public SecurityProperties {
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(14) : refreshTokenTtl;
        personalTokenTtl = personalTokenTtl == null ? Duration.ofDays(90) : personalTokenTtl;
        maxLoginAttempts = maxLoginAttempts <= 0 ? 5 : maxLoginAttempts;
        lockDuration = lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : corsAllowedOrigins;
    }
}
