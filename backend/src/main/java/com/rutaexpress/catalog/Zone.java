package com.rutaexpress.catalog;

import com.rutaexpress.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "zone")
public class Zone extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "geography(Polygon,4326)")
    private Polygon area;

    @Column(nullable = false)
    private String color = "#1976d2";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id")
    private Depot depot;

    protected Zone() {
    }

    public Zone(String name, Polygon area, String color, Depot depot) {
        this.name = name;
        this.area = area;
        this.color = color == null ? this.color : color;
        this.depot = depot;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Polygon getArea() {
        return area;
    }

    public void setArea(Polygon area) {
        this.area = area;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Depot getDepot() {
        return depot;
    }

    public void setDepot(Depot depot) {
        this.depot = depot;
    }
}
