package com.rutaexpress.scanning;

import com.rutaexpress.scanning.ScanningDtos.LoadInfo;
import com.rutaexpress.scanning.ScanningDtos.LoadSummary;
import com.rutaexpress.scanning.ScanningDtos.OpenLoadRequest;
import com.rutaexpress.scanning.ScanningDtos.ScanBatchRequest;
import com.rutaexpress.scanning.ScanningDtos.ScanRequest;
import com.rutaexpress.scanning.ScanningDtos.ScanResponse;
import com.rutaexpress.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Carga y escaneo")
@RestController
@RequestMapping("/api/loads")
class LoadController {

    private final ScanningService scanning;

    LoadController(ScanningService scanning) {
        this.scanning = scanning;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LoadSummary open(@RequestBody(required = false) OpenLoadRequest request) {
        return scanning.open(request == null ? new OpenLoadRequest(null, null) : request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    List<LoadInfo> today() {
        return scanning.listToday();
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('DRIVER')")
    ResponseEntity<LoadSummary> current() {
        return ResponseEntity.of(scanning.current(CurrentUser.driverId()));
    }

    @GetMapping("/{id}")
    LoadSummary get(@PathVariable UUID id) {
        return scanning.get(id);
    }

    /** Registra un escaneo. Idempotente por clientScanId: reenviarlo devuelve el mismo resultado. */
    @PostMapping("/{id}/scans")
    ScanResponse scan(@PathVariable UUID id, @Valid @RequestBody ScanRequest request) {
        return scanning.scan(id, request);
    }

    /** Sincronización de los escaneos guardados sin conexión. */
    @PostMapping("/{id}/scans/batch")
    List<ScanResponse> scanBatch(@PathVariable UUID id, @Valid @RequestBody ScanBatchRequest request) {
        return scanning.scanBatch(id, request.scans());
    }

    /** Cierra la carga y lanza la optimización: 202 Accepted, la ruta llega por WebSocket. */
    @PostMapping("/{id}/close")
    @ResponseStatus(HttpStatus.ACCEPTED)
    LoadSummary close(@PathVariable UUID id) {
        return scanning.close(id);
    }
}
