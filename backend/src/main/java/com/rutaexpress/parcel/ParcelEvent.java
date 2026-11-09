package com.rutaexpress.parcel;

import com.rutaexpress.shared.events.EventSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Historial inmutable de lo que le pasa a un paquete. */
@Entity
@Immutable
@Table(name = "parcel_event")
public class ParcelEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

    @Column(nullable = false)
    private String type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor")
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSource source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    protected ParcelEvent() {
    }

    public ParcelEvent(Parcel parcel, String type, String actor, EventSource source, Map<String, Object> data) {
        this.parcel = parcel;
        this.type = type;
        this.actor = actor;
        this.source = source;
        this.data = data;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public EventSource getSource() {
        return source;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
