package com.rutaexpress.geo;

import org.springframework.data.jpa.repository.JpaRepository;

interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, String> {
}
