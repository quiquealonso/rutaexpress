package com.rutaexpress.parcel;

import com.rutaexpress.shared.AuditableEntity;
import com.rutaexpress.shared.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "parcel")
public class Parcel extends AuditableEntity {

    @Column(name = "tracking_code", nullable = false, unique = true, updatable = false)
    private String trackingCode;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParcelStatus status = ParcelStatus.REGISTERED;

    /** Franja horaria opcional de entrega. */
    @Column(name = "window_start")
    private LocalTime windowStart;

    @Column(name = "window_end")
    private LocalTime windowEnd;

    @Column(length = 500)
    private String notes;

    @Column(name = "depot_id", nullable = false)
    private UUID depotId;

    /** Asignación manual a un repartidor; si es null, se asigna por la zona de la dirección. */
    @Column(name = "assigned_driver_id")
    private UUID assignedDriverId;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id")
    private Address address;

    @Version
    private long version;

    protected Parcel() {
    }

    public Parcel(String trackingCode, BigDecimal weightKg, UUID depotId, Recipient recipient, Address address) {
        this.trackingCode = trackingCode;
        this.weightKg = weightKg;
        this.depotId = depotId;
        this.recipient = recipient;
        this.address = address;
    }

    public void transitionTo(ParcelStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException("invalid-parcel-status",
                    "El paquete " + trackingCode + " no puede pasar de " + status + " a " + target);
        }
        if (target == ParcelStatus.FAILED_ATTEMPT && status != ParcelStatus.FAILED_ATTEMPT) {
            deliveryAttempts++;
        }
        this.status = target;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public Integer getLengthCm() {
        return lengthCm;
    }

    public Integer getWidthCm() {
        return widthCm;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setDimensions(Integer lengthCm, Integer widthCm, Integer heightCm) {
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
    }

    public ParcelStatus getStatus() {
        return status;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindow(LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("La franja horaria debe terminar después de empezar");
        }
        this.windowStart = start;
        this.windowEnd = end;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getDepotId() {
        return depotId;
    }

    public void setDepotId(UUID depotId) {
        this.depotId = depotId;
    }

    public UUID getAssignedDriverId() {
        return assignedDriverId;
    }

    public void setAssignedDriverId(UUID assignedDriverId) {
        this.assignedDriverId = assignedDriverId;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public Recipient getRecipient() {
        return recipient;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
