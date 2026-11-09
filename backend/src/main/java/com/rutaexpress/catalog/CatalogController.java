package com.rutaexpress.catalog;

import com.rutaexpress.catalog.CatalogDtos.CreateDriverRequest;
import com.rutaexpress.catalog.CatalogDtos.DepotRequest;
import com.rutaexpress.catalog.CatalogDtos.DepotResponse;
import com.rutaexpress.catalog.CatalogDtos.DriverResponse;
import com.rutaexpress.catalog.CatalogDtos.UpdateDriverRequest;
import com.rutaexpress.catalog.CatalogDtos.VehicleRequest;
import com.rutaexpress.catalog.CatalogDtos.VehicleResponse;
import com.rutaexpress.catalog.CatalogDtos.ZoneRequest;
import com.rutaexpress.catalog.CatalogDtos.ZoneResponse;
import com.rutaexpress.shared.security.CurrentUser;
import com.rutaexpress.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Catálogo")
@RestController
@RequestMapping("/api")
class CatalogController {

    private static final String STAFF = "hasAnyRole('ADMIN','DISPATCHER')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/depots")
    @PreAuthorize(STAFF)
    List<DepotResponse> depots() {
        return catalog.listDepots();
    }

    @GetMapping("/depots/{id}")
    @PreAuthorize(STAFF)
    DepotResponse depot(@PathVariable UUID id) {
        return catalog.getDepot(id);
    }

    @PostMapping("/depots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ADMIN)
    DepotResponse createDepot(@Valid @RequestBody DepotRequest request) {
        return catalog.createDepot(request);
    }

    @PutMapping("/depots/{id}")
    @PreAuthorize(ADMIN)
    DepotResponse updateDepot(@PathVariable UUID id, @Valid @RequestBody DepotRequest request) {
        return catalog.updateDepot(id, request);
    }

    @DeleteMapping("/depots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ADMIN)
    void deleteDepot(@PathVariable UUID id) {
        catalog.deleteDepot(id);
    }

    @GetMapping("/vehicles")
    @PreAuthorize(STAFF)
    PageResponse<VehicleResponse> vehicles(@PageableDefault(size = 50, sort = "plate") Pageable pageable) {
        return PageResponse.of(catalog.listVehicles(pageable));
    }

    @GetMapping("/vehicles/{id}")
    @PreAuthorize(STAFF)
    VehicleResponse vehicle(@PathVariable UUID id) {
        return catalog.getVehicle(id);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STAFF)
    VehicleResponse createVehicle(@Valid @RequestBody VehicleRequest request) {
        return catalog.createVehicle(request);
    }

    @PutMapping("/vehicles/{id}")
    @PreAuthorize(STAFF)
    VehicleResponse updateVehicle(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return catalog.updateVehicle(id, request);
    }

    @DeleteMapping("/vehicles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(STAFF)
    void deleteVehicle(@PathVariable UUID id) {
        catalog.deleteVehicle(id);
    }

    @GetMapping("/zones")
    @PreAuthorize(STAFF)
    List<ZoneResponse> zones(@RequestParam(required = false) UUID depotId) {
        return catalog.listZones(depotId);
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STAFF)
    ZoneResponse createZone(@Valid @RequestBody ZoneRequest request) {
        return catalog.createZone(request);
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize(STAFF)
    ZoneResponse updateZone(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return catalog.updateZone(id, request);
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(STAFF)
    void deleteZone(@PathVariable UUID id) {
        catalog.deleteZone(id);
    }

    @GetMapping("/drivers")
    @PreAuthorize(STAFF)
    PageResponse<DriverResponse> drivers(@PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return PageResponse.of(catalog.listDrivers(pageable));
    }

    /** Perfil del propio repartidor (lo usa la PWA). */
    @GetMapping("/drivers/me")
    @PreAuthorize("hasRole('DRIVER')")
    DriverResponse me() {
        return catalog.getDriver(CurrentUser.driverId());
    }

    @GetMapping("/drivers/{id}")
    @PreAuthorize(STAFF)
    DriverResponse driver(@PathVariable UUID id) {
        return catalog.getDriver(id);
    }

    @PostMapping("/drivers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STAFF)
    DriverResponse createDriver(@Valid @RequestBody CreateDriverRequest request) {
        return catalog.createDriver(request);
    }

    @PutMapping("/drivers/{id}")
    @PreAuthorize(STAFF)
    DriverResponse updateDriver(@PathVariable UUID id, @Valid @RequestBody UpdateDriverRequest request) {
        return catalog.updateDriver(id, request);
    }

    @DeleteMapping("/drivers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(STAFF)
    void deactivateDriver(@PathVariable UUID id) {
        catalog.deactivateDriver(id);
    }
}
