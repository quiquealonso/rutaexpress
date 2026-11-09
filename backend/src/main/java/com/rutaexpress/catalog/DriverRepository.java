package com.rutaexpress.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DriverRepository extends JpaRepository<Driver, UUID> {

    @EntityGraph(attributePaths = {"depot", "zone", "defaultVehicle"})
    Page<Driver> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"depot", "zone", "defaultVehicle"})
    @Query("select d from Driver d where d.id = :id")
    Optional<Driver> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"depot", "zone", "defaultVehicle"})
    @Query("select d from Driver d where d.id in :ids")
    List<Driver> findDetailedByIdIn(java.util.Collection<UUID> ids);

    Optional<Driver> findByUserId(UUID userId);
}
