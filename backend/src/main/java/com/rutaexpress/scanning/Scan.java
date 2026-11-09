package com.rutaexpress.scanning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan")
public class Scan {

    public enum Result {
        /** Paquete esperado, cargado. */
        OK,
        /** Ya se había escaneado en esta carga. */
        DUPLICATE,
        /** El código no corresponde a ningún paquete. */
        UNKNOWN,
        /** Existe, pero no es de este repartidor o no está pendiente de reparto. */
        NOT_EXPECTED
    }

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "load_id")
    private Load load;

    /** Id generado por el cliente: reenviar el mismo escaneo (modo sin conexión) no lo duplica. */
    @Column(name = "client_scan_id", nullable = false, unique = true)
    private UUID clientScanId;

    @Column(nullable = false)
    private String code;

    @Column(name = "parcel_id")
    private UUID parcelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Result result;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected Scan() {
    }

    public Scan(Load load, UUID clientScanId, String code, UUID parcelId, Result result, Instant scannedAt) {
        this.load = load;
        this.clientScanId = clientScanId;
        this.code = code;
        this.parcelId = parcelId;
        this.result = result;
        this.scannedAt = scannedAt;
    }

    public UUID getId() {
        return id;
    }

    public Load getLoad() {
        return load;
    }

    public UUID getClientScanId() {
        return clientScanId;
    }

    public String getCode() {
        return code;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public Result getResult() {
        return result;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }
}
