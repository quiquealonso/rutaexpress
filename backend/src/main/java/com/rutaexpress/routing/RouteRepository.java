package com.rutaexpress.routing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RouteRepository extends JpaRepository<Route, UUID> {

    @EntityGraph(attributePaths = {"stops", "stops.parcelIds"})
    @Query("select r from Route r where r.id = :id")
    Optional<Route> findDetailedById(UUID id);

    boolean existsByLoadId(UUID loadId);

    Optional<Route> findByLoadId(UUID loadId);

    Optional<Route> findFirstByDriverIdAndStatusInOrderByCreatedAtDesc(UUID driverId, Collection<Route.Status> statuses);

    Optional<Route> findFirstByDriverIdOrderByCreatedAtDesc(UUID driverId);

    List<Route> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    List<Route> findByStatusIn(Collection<Route.Status> statuses);
}
