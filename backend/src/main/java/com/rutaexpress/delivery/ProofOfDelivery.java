package com.rutaexpress.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proof_of_delivery")
public class ProofOfDelivery {

    public enum Type {
        SIGNATURE, PHOTO, CODE
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "stop_id", nullable = false, unique = true)
    private UUID stopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** Clave del fichero en el almacenamiento (MinIO/S3); null para la entrega con código. */
    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ProofOfDelivery() {
    }

    public ProofOfDelivery(UUID stopId, Type type, String fileKey, String contentType, String receiverName,
                           Point location, String createdBy) {
        this.stopId = stopId;
        this.type = type;
        this.fileKey = fileKey;
        this.contentType = contentType;
        this.receiverName = receiverName;
        this.location = location;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStopId() {
        return stopId;
    }

    public Type getType() {
        return type;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public Point getLocation() {
        return location;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
