package com.rutaexpress.scanning;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface LoadRepository extends JpaRepository<Load, UUID> {

    @EntityGraph(attributePaths = "expectedParcelIds")
    @Query("select l from Load l where l.id = :id")
    Optional<Load> findWithExpectedById(UUID id);

    Optional<Load> findFirstByDriverIdAndStatusOrderByOpenedAtDesc(UUID driverId, Load.Status status);

    Optional<Load> findFirstByDriverIdOrderByOpenedAtDesc(UUID driverId);

    List<Load> findByOpenedAtAfterOrderByOpenedAtDesc(Instant since);
}
