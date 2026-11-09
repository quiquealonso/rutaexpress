package com.rutaexpress.routing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StopRepository extends JpaRepository<Stop, UUID> {

    @EntityGraph(attributePaths = {"route", "parcelIds"})
    @Query("select s from Stop s where s.id = :id")
    Optional<Stop> findDetailedById(UUID id);

    /** Parada activa que contiene un paquete (para el seguimiento público). */
    @Query("""
            select s from Stop s join s.parcelIds p join fetch s.route r
            where p = :parcelId and r.status in :routeStatuses
            order by r.createdAt desc
            """)
    List<Stop> findByParcelInRoutes(UUID parcelId, Collection<Route.Status> routeStatuses);
}
