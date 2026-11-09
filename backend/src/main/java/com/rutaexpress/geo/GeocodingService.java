package com.rutaexpress.geo;

import com.rutaexpress.geo.GeoModels.AddressQuery;
import com.rutaexpress.geo.GeoModels.GeocodeResult;
import com.rutaexpress.shared.config.CacheConfig;
import com.rutaexpress.shared.geo.Geometries;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Geocodificación con dos niveles de caché: Redis (Spring Cache) y la tabla geocode_cache.
 * Solo si la dirección no está en ninguna se consulta a Nominatim.
 */
@Service
public class GeocodingService {

    private final GeocodeCacheRepository cache;
    private final NominatimClient nominatim;
    private final GeoProperties properties;

    GeocodingService(GeocodeCacheRepository cache, NominatimClient nominatim, GeoProperties properties) {
        this.cache = cache;
        this.nominatim = nominatim;
        this.properties = properties;
    }

    @Cacheable(cacheNames = CacheConfig.GEOCODE, key = "#query.normalized()", unless = "#result == null")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GeocodeResult geocode(AddressQuery query) {
        String normalized = query.normalized();
        String hash = sha256(normalized);
        Optional<GeocodeCache> cached = cache.findById(hash);
        if (cached.isPresent()) {
            GeocodeCache c = cached.get();
            return c.getLocation() == null ? null
                    : new GeocodeResult(Geometries.toGeoPoint(c.getLocation()), c.getConfidence(), c.getDisplayName(),
                    c.getProvider());
        }
        Optional<GeocodeResult> result = nominatim.geocode(query);
        result.ifPresent(r -> cache.save(new GeocodeCache(hash, normalized, Geometries.point(r.location()),
                r.confidence(), r.displayName(), r.provider())));
        return result.orElse(null);
    }

    public boolean needsReview(GeocodeResult result) {
        return result == null || result.confidence() < properties.minGeocodeConfidence();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
