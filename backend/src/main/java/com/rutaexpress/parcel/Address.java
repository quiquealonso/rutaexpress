package com.rutaexpress.parcel;

import com.rutaexpress.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

/** Dirección compartida por todos los paquetes del mismo destino (clave: hash de la dirección normalizada). */
@Entity
@Table(name = "address")
public class Address extends AuditableEntity {

    @Column(nullable = false)
    private String street;

    private String number;

    @Column(name = "extra")
    private String extra;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country = "España";

    @Column(name = "normalized_hash", nullable = false, unique = true)
    private String normalizedHash;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    /** Confianza de la geocodificación (0..1). */
    @Column(name = "geocode_quality")
    private Double geocodeQuality;

    @Column(name = "geocode_provider")
    private String geocodeProvider;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    protected Address() {
    }

    public Address(String street, String number, String extra, String postalCode, String city, String country,
                   String normalizedHash) {
        this.street = street;
        this.number = number;
        this.extra = extra;
        this.postalCode = postalCode;
        this.city = city;
        this.country = country == null ? this.country : country;
        this.normalizedHash = normalizedHash;
    }

    public void geocoded(Point location, Double quality, String provider, boolean needsReview) {
        this.location = location;
        this.geocodeQuality = quality;
        this.geocodeProvider = provider;
        this.needsReview = needsReview;
    }

    public String oneLine() {
        StringBuilder sb = new StringBuilder(street);
        if (number != null && !number.isBlank()) {
            sb.append(' ').append(number);
        }
        if (extra != null && !extra.isBlank()) {
            sb.append(", ").append(extra);
        }
        sb.append(", ").append(postalCode).append(' ').append(city);
        return sb.toString();
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getExtra() {
        return extra;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public Point getLocation() {
        return location;
    }

    public Double getGeocodeQuality() {
        return geocodeQuality;
    }

    public String getGeocodeProvider() {
        return geocodeProvider;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }
}
