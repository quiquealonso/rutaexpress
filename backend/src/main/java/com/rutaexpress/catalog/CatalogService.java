package com.rutaexpress.catalog;

import com.rutaexpress.auth.Role;
import com.rutaexpress.auth.UserService;
import com.rutaexpress.catalog.CatalogDtos.CreateDriverRequest;
import com.rutaexpress.catalog.CatalogDtos.DepotRequest;
import com.rutaexpress.catalog.CatalogDtos.DepotResponse;
import com.rutaexpress.catalog.CatalogDtos.DepotSummary;
import com.rutaexpress.catalog.CatalogDtos.DriverResponse;
import com.rutaexpress.catalog.CatalogDtos.DriverSummary;
import com.rutaexpress.catalog.CatalogDtos.UpdateDriverRequest;
import com.rutaexpress.catalog.CatalogDtos.VehicleRequest;
import com.rutaexpress.catalog.CatalogDtos.VehicleResponse;
import com.rutaexpress.catalog.CatalogDtos.ZoneRequest;
import com.rutaexpress.catalog.CatalogDtos.ZoneResponse;
import com.rutaexpress.shared.error.BusinessException;
import com.rutaexpress.shared.error.NotFoundException;
import com.rutaexpress.shared.geo.Geometries;
import org.locationtech.jts.geom.Polygon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Almacenes, vehículos, repartidores y zonas. También es la API pública del módulo para el resto. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final DepotRepository depots;
    private final VehicleRepository vehicles;
    private final ZoneRepository zones;
    private final DriverRepository drivers;
    private final CatalogMapper mapper;
    private final UserService userService;

    CatalogService(DepotRepository depots, VehicleRepository vehicles, ZoneRepository zones,
                   DriverRepository drivers, CatalogMapper mapper, UserService userService) {
        this.depots = depots;
        this.vehicles = vehicles;
        this.zones = zones;
        this.drivers = drivers;
        this.mapper = mapper;
        this.userService = userService;
    }

    // ---------- Almacenes ----------

    public List<DepotResponse> listDepots() {
        return depots.findAll().stream().map(mapper::toResponse).toList();
    }

    public DepotResponse getDepot(UUID id) {
        return mapper.toResponse(depot(id));
    }

    @Transactional
    public DepotResponse createDepot(DepotRequest r) {
        return mapper.toResponse(depots.save(new Depot(r.name(), r.address(), Geometries.point(r.location()))));
    }

    @Transactional
    public DepotResponse updateDepot(UUID id, DepotRequest r) {
        Depot d = depot(id);
        d.setName(r.name());
        d.setAddress(r.address());
        d.setLocation(Geometries.point(r.location()));
        return mapper.toResponse(d);
    }

    @Transactional
    public void deleteDepot(UUID id) {
        depots.delete(depot(id));
    }

    // ---------- Vehículos ----------

    public Page<VehicleResponse> listVehicles(Pageable pageable) {
        return vehicles.findAllBy(pageable).map(mapper::toResponse);
    }

    public VehicleResponse getVehicle(UUID id) {
        return mapper.toResponse(vehicle(id));
    }

    @Transactional
    public VehicleResponse createVehicle(VehicleRequest r) {
        String plate = r.plate().toUpperCase().replace(" ", "");
        if (vehicles.existsByPlate(plate)) {
            throw new BusinessException("plate-taken", "Ya existe un vehículo con la matrícula " + plate);
        }
        Vehicle v = new Vehicle(plate, r.capacityKg(), r.capacityM3(), r.capacityParcels(), depot(r.depotId()));
        if (r.active() != null) {
            v.setActive(r.active());
        }
        return mapper.toResponse(vehicles.save(v));
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID id, VehicleRequest r) {
        Vehicle v = vehicle(id);
        v.setPlate(r.plate());
        v.setCapacityKg(r.capacityKg());
        v.setCapacityM3(r.capacityM3());
        v.setCapacityParcels(r.capacityParcels());
        v.setDepot(depot(r.depotId()));
        if (r.active() != null) {
            v.setActive(r.active());
        }
        return mapper.toResponse(v);
    }

    @Transactional
    public void deleteVehicle(UUID id) {
        vehicles.delete(vehicle(id));
    }

    // ---------- Zonas ----------

    public List<ZoneResponse> listZones(UUID depotId) {
        List<Zone> list = depotId == null
                ? zones.findAllByOrderByNameAsc()
                : zones.findByDepotIdOrderByNameAsc(depotId);
        return list.stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public ZoneResponse createZone(ZoneRequest r) {
        Zone z = new Zone(r.name(), Geometries.polygon(r.area()), r.color(), depot(r.depotId()));
        return mapper.toResponse(zones.save(z));
    }

    @Transactional
    public ZoneResponse updateZone(UUID id, ZoneRequest r) {
        Zone z = zones.findById(id).orElseThrow(() -> new NotFoundException("Zona", id));
        z.setName(r.name());
        z.setArea(Geometries.polygon(r.area()));
        if (r.color() != null) {
            z.setColor(r.color());
        }
        z.setDepot(depot(r.depotId()));
        return mapper.toResponse(z);
    }

    @Transactional
    public void deleteZone(UUID id) {
        zones.deleteById(id);
    }

    // ---------- Repartidores ----------

    public Page<DriverResponse> listDrivers(Pageable pageable) {
        return drivers.findAllBy(pageable).map(mapper::toResponse);
    }

    public DriverResponse getDriver(UUID id) {
        return mapper.toResponse(drivers.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Repartidor", id)));
    }

    /** Alta de repartidor junto con su usuario de acceso (rol DRIVER). */
    @Transactional
    public DriverResponse createDriver(CreateDriverRequest r) {
        UUID userId = userService.createUserAccount(r.email(), r.password(), Role.DRIVER);
        Driver d = new Driver(r.name(), r.phone(), r.licenseNumber(), depot(r.depotId()));
        d.setUserId(userId);
        applyAssignments(d, r.zoneId(), r.defaultVehicleId());
        return mapper.toResponse(drivers.save(d));
    }

    @Transactional
    public DriverResponse updateDriver(UUID id, UpdateDriverRequest r) {
        Driver d = drivers.findDetailedById(id).orElseThrow(() -> new NotFoundException("Repartidor", id));
        d.setName(r.name());
        d.setPhone(r.phone());
        d.setLicenseNumber(r.licenseNumber());
        d.setDepot(depot(r.depotId()));
        if (r.status() != null) {
            d.setStatus(r.status());
        }
        applyAssignments(d, r.zoneId(), r.defaultVehicleId());
        return mapper.toResponse(d);
    }

    @Transactional
    public void deactivateDriver(UUID id) {
        Driver d = drivers.findById(id).orElseThrow(() -> new NotFoundException("Repartidor", id));
        d.setStatus(Driver.Status.INACTIVE);
        if (d.getUserId() != null) {
            userService.disable(d.getUserId());
        }
    }

    @Transactional
    public void setDriverStatus(UUID driverId, Driver.Status status) {
        drivers.findById(driverId).ifPresent(d -> d.setStatus(status));
    }

    private void applyAssignments(Driver d, UUID zoneId, UUID vehicleId) {
        d.setZone(zoneId == null ? null
                : zones.findById(zoneId).orElseThrow(() -> new NotFoundException("Zona", zoneId)));
        d.setDefaultVehicle(vehicleId == null ? null : vehicle(vehicleId));
    }

    // ---------- API para otros módulos ----------

    public Optional<DriverSummary> findDriver(UUID driverId) {
        return drivers.findDetailedById(driverId).map(mapper::toSummary);
    }

    public DriverSummary requireDriver(UUID driverId) {
        return findDriver(driverId).orElseThrow(() -> new NotFoundException("Repartidor", driverId));
    }

    public Map<UUID, DriverSummary> findDrivers(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return drivers.findDetailedByIdIn(ids).stream().map(mapper::toSummary)
                .collect(Collectors.toMap(DriverSummary::id, Function.identity()));
    }

    public DepotSummary requireDepot(UUID depotId) {
        Depot d = depot(depotId);
        return new DepotSummary(d.getId(), d.getName(), Geometries.toGeoPoint(d.getLocation()));
    }

    /** Polígono de la zona asignada al repartidor, si tiene. */
    public Optional<Polygon> zoneAreaOfDriver(UUID driverId) {
        return drivers.findDetailedById(driverId).map(Driver::getZone).map(Zone::getArea);
    }

    public boolean vehicleExists(UUID vehicleId) {
        return vehicles.existsById(vehicleId);
    }

    public Optional<String> vehiclePlate(UUID vehicleId) {
        return vehicles.findById(vehicleId).map(Vehicle::getPlate);
    }

    @Transactional
    public void markOnRoute(UUID driverId, boolean onRoute) {
        setDriverStatus(driverId, onRoute ? Driver.Status.ON_ROUTE : Driver.Status.ACTIVE);
    }

    private Depot depot(UUID id) {
        return depots.findById(id).orElseThrow(() -> new NotFoundException("Almacén", id));
    }

    private Vehicle vehicle(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> new NotFoundException("Vehículo", id));
    }
}
