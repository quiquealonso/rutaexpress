package com.rutaexpress.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @EntityGraph(attributePaths = "depot")
    Page<Vehicle> findAllBy(Pageable pageable);

    boolean existsByPlate(String plate);
}
