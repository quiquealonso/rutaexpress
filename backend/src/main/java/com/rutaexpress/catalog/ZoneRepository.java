package com.rutaexpress.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ZoneRepository extends JpaRepository<Zone, UUID> {

    @EntityGraph(attributePaths = "depot")
    List<Zone> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "depot")
    List<Zone> findByDepotIdOrderByNameAsc(UUID depotId);
}
