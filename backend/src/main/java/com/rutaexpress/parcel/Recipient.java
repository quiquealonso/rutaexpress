package com.rutaexpress.parcel;

import com.rutaexpress.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Datos personales mínimos del destinatario; se anonimizan tras el periodo de retención (RGPD). */
@Entity
@Table(name = "recipient")
public class Recipient extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    private String phone;

    private String email;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    protected Recipient() {
    }

    public Recipient(String name, String phone, String email) {
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public void anonymize(Instant now) {
        this.name = "Anonimizado";
        this.phone = null;
        this.email = null;
        this.anonymizedAt = now;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
