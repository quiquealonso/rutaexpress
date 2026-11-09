package com.rutaexpress.parcel;

import com.rutaexpress.parcel.ParcelDtos.CreateParcelRequest;
import com.rutaexpress.parcel.ParcelDtos.ImportResult;
import com.rutaexpress.parcel.ParcelDtos.LabelsRequest;
import com.rutaexpress.parcel.ParcelDtos.ParcelEventResponse;
import com.rutaexpress.parcel.ParcelDtos.ParcelFilter;
import com.rutaexpress.parcel.ParcelDtos.ParcelListItem;
import com.rutaexpress.parcel.ParcelDtos.ParcelResponse;
import com.rutaexpress.parcel.ParcelDtos.StatusCountResponse;
import com.rutaexpress.parcel.ParcelDtos.UpdateParcelRequest;
import com.rutaexpress.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Tag(name = "Paquetes")
@RestController
@RequestMapping("/api/parcels")
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
class ParcelController {

    private final ParcelService parcels;

    ParcelController(ParcelService parcels) {
        this.parcels = parcels;
    }

    @GetMapping
    PageResponse<ParcelListItem> list(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) UUID depotId,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Boolean needsReview,
                                      @RequestParam(required = false) UUID driverId,
                                      @PageableDefault(size = 25, sort = "createdAt",
                                              direction = org.springframework.data.domain.Sort.Direction.DESC)
                                      Pageable pageable) {
        return PageResponse.of(parcels.list(new ParcelFilter(status, depotId, q, needsReview, driverId), pageable));
    }

    @GetMapping("/{id}")
    ParcelResponse get(@PathVariable UUID id) {
        return parcels.get(id);
    }

    @GetMapping("/{id}/events")
    List<ParcelEventResponse> history(@PathVariable UUID id) {
        return parcels.history(id);
    }

    @GetMapping("/stats")
    List<StatusCountResponse> stats() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return parcels.statusCounts(startOfDay);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ParcelResponse create(@Valid @RequestBody CreateParcelRequest request) {
        return parcels.create(request);
    }

    @PutMapping("/{id}")
    ParcelResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateParcelRequest request) {
        return parcels.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable UUID id) {
        parcels.cancel(id);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportResult importCsv(@RequestPart("file") MultipartFile file, @RequestParam UUID depotId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El fichero está vacío");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".csv")) {
            throw new IllegalArgumentException("Solo se admiten ficheros .csv");
        }
        return parcels.importCsv(file.getInputStream(), depotId);
    }

    /** Genera la etiqueta QR en PDF de un paquete. */
    @PostMapping(path = "/{id}/label", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> label(@PathVariable UUID id) {
        return pdf(parcels.labels(List.of(id)), "etiqueta-" + id + ".pdf");
    }

    /** Varias etiquetas en un solo PDF (una por página), para imprimir en lote. */
    @PostMapping(path = "/labels", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> labels(@Valid @RequestBody LabelsRequest request) {
        return pdf(parcels.labels(request.parcelIds()), "etiquetas.pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(content);
    }
}
