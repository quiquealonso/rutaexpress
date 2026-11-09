package com.rutaexpress.routing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface RouteVersionRepository extends JpaRepository<RouteVersion, UUID> {

    List<RouteVersion> findByRouteIdOrderByVersionAsc(UUID routeId);
}
