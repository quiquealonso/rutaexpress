package com.rutaexpress.catalog;

import com.rutaexpress.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicle")
public class Vehicle extends AuditableEntity {

    @Column(nullable = false, unique = true)
    private String plate;

    @Column(name = "capacity_kg", nullable = false)
    private BigDecimal capacityKg;

    @Column(name = "capacity_m3", nullable = false)
    private BigDecimal capacityM3;

    @Column(name = "capacity_parcels", nullable = false)
    private int capacityParcels;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id")
    private Depot depot;

    protected Vehicle() {
    }

    public Vehicle(String plate, BigDecimal capacityKg, BigDecimal capacityM3, int capacityParcels, Depot depot) {
        this.plate = plate.toUpperCase().replace(" ", "");
        this.capacityKg = capacityKg;
        this.capacityM3 = capacityM3;
        this.capacityParcels = capacityParcels;
        this.depot = depot;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate.toUpperCase().replace(" ", "");
    }

    public BigDecimal getCapacityKg() {
        return capacityKg;
    }

    public void setCapacityKg(BigDecimal capacityKg) {
        this.capacityKg = capacityKg;
    }

    public BigDecimal getCapacityM3() {
        return capacityM3;
    }

    public void setCapacityM3(BigDecimal capacityM3) {
        this.capacityM3 = capacityM3;
    }

    public int getCapacityParcels() {
        return capacityParcels;
    }

    public void setCapacityParcels(int capacityParcels) {
        this.capacityParcels = capacityParcels;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Depot getDepot() {
        return depot;
    }

    public void setDepot(Depot depot) {
        this.depot = depot;
    }
}
