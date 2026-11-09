package com.rutaexpress.shared.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/** TTLs por caché cuando se usa Redis (perfil docker/prod). En desarrollo se usa la caché simple en memoria. */
@Configuration
public class CacheConfig {

    public static final String GEOCODE = "geocode";
    public static final String DISTANCE_MATRIX = "distanceMatrix";
    public static final String PUBLIC_TRACKING = "publicTracking";

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheCustomizer() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return builder -> builder
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(GEOCODE, base.entryTtl(Duration.ofDays(30)))
                .withCacheConfiguration(DISTANCE_MATRIX, base.entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration(PUBLIC_TRACKING, base.entryTtl(Duration.ofSeconds(20)));
    }
}
