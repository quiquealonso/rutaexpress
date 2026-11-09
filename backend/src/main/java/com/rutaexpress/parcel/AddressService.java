package com.rutaexpress.parcel;

import com.rutaexpress.geo.GeoModels.AddressQuery;
import com.rutaexpress.geo.GeoModels.GeocodeResult;
import com.rutaexpress.geo.GeocodingService;
import com.rutaexpress.parcel.ParcelDtos.AddressDto;
import com.rutaexpress.shared.geo.Geometries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Normaliza, deduplica y geocodifica direcciones. Diez paquetes al mismo portal comparten Address. */
@Service
class AddressService {

    private final AddressRepository addresses;
    private final GeocodingService geocoding;

    AddressService(AddressRepository addresses, GeocodingService geocoding) {
        this.addresses = addresses;
        this.geocoding = geocoding;
    }

    @Transactional
    public Address findOrCreate(AddressDto dto) {
        AddressQuery query = toQuery(dto);
        String extra = dto.extra() == null ? "" : dto.extra().trim().toLowerCase();
        String hash = sha256(query.normalized() + "|" + extra);
        return addresses.findByNormalizedHash(hash).map(existing -> {
            if (dto.location() != null) {
                existing.geocoded(Geometries.point(dto.location()), 1.0, "MANUAL", false);
            }
            return existing;
        }).orElseGet(() -> {
            Address address = new Address(dto.street().trim(), blankToNull(dto.number()), blankToNull(dto.extra()),
                    dto.postalCode().trim(), dto.city().trim(), blankToNull(dto.country()), hash);
            if (dto.location() != null) {
                address.geocoded(Geometries.point(dto.location()), 1.0, "MANUAL", false);
            } else {
                GeocodeResult result = geocoding.geocode(query);
                address.geocoded(result == null ? null : Geometries.point(result.location()),
                        result == null ? null : result.confidence(),
                        result == null ? null : result.provider(),
                        geocoding.needsReview(result));
            }
            return addresses.save(address);
        });
    }

    private static AddressQuery toQuery(AddressDto dto) {
        return new AddressQuery(dto.street(), dto.number(), dto.postalCode(), dto.city(),
                dto.country() == null ? "España" : dto.country());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
