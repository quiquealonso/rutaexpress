package com.rutaexpress.catalog;

import com.rutaexpress.catalog.CatalogDtos.DepotResponse;
import com.rutaexpress.catalog.CatalogDtos.DriverResponse;
import com.rutaexpress.catalog.CatalogDtos.DriverSummary;
import com.rutaexpress.catalog.CatalogDtos.VehicleResponse;
import com.rutaexpress.catalog.CatalogDtos.ZoneResponse;
import com.rutaexpress.shared.geo.GeoPoint;
import com.rutaexpress.shared.geo.Geometries;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Mapeo entidad → DTO generado por MapStruct: las entidades nunca salen por la API. */
@Mapper
interface CatalogMapper {

    DepotResponse toResponse(Depot depot);

    @Mapping(target = "depotId", source = "depot.id")
    @Mapping(target = "depotName", source = "depot.name")
    VehicleResponse toResponse(Vehicle vehicle);

    @Mapping(target = "depotId", source = "depot.id")
    @Mapping(target = "depotName", source = "depot.name")
    ZoneResponse toResponse(Zone zone);

    @Mapping(target = "depotId", source = "depot.id")
    @Mapping(target = "depotName", source = "depot.name")
    @Mapping(target = "zoneId", source = "zone.id")
    @Mapping(target = "zoneName", source = "zone.name")
    @Mapping(target = "defaultVehicleId", source = "defaultVehicle.id")
    @Mapping(target = "defaultVehiclePlate", source = "defaultVehicle.plate")
    DriverResponse toResponse(Driver driver);

    @Mapping(target = "depotId", source = "depot.id")
    @Mapping(target = "zoneId", source = "zone.id")
    @Mapping(target = "defaultVehicleId", source = "defaultVehicle.id")
    DriverSummary toSummary(Driver driver);

    default GeoPoint map(Point point) {
        return Geometries.toGeoPoint(point);
    }

    default List<GeoPoint> map(Polygon polygon) {
        return Geometries.toGeoPoints(polygon);
    }

    default String map(Driver.Status status) {
        return status == null ? null : status.name();
    }
}
