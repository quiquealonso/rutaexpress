package com.rutaexpress.parcel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ParcelRepository extends JpaRepository<Parcel, UUID>, JpaSpecificationExecutor<Parcel> {

    @Override
    @EntityGraph(attributePaths = {"recipient", "address"})
    Page<Parcel> findAll(Specification<Parcel> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient", "address"})
    @Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"recipient", "address"})
    @Query("select p from Parcel p where p.id in :ids")
    List<Parcel> findDetailedByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = {"recipient", "address"})
    Optional<Parcel> findByTrackingCode(String trackingCode);

    boolean existsByTrackingCode(String trackingCode);

    @Query("select p.id from Parcel p where p.trackingCode = :code")
    Optional<UUID> findIdByTrackingCode(String code);

    /** Paquetes pendientes de cargar asignados explícitamente a un repartidor. */
    @Query("""
            select p.id from Parcel p
            where p.depotId = :depotId and p.assignedDriverId = :driverId
              and p.status in (com.rutaexpress.parcel.ParcelStatus.REGISTERED,
                               com.rutaexpress.parcel.ParcelStatus.FAILED_ATTEMPT)
            """)
    List<UUID> findLoadableAssignedTo(UUID depotId, UUID driverId);

    /** Paquetes pendientes sin asignar cuya dirección cae dentro de la zona (consulta espacial con PostGIS). */
    @Query(value = """
            select p.id from parcel p join address a on a.id = p.address_id
            where p.depot_id = :depotId and p.assigned_driver_id is null
              and p.status in ('REGISTERED', 'FAILED_ATTEMPT')
              and a.location is not null
              and ST_Covers(ST_GeogFromText(:zoneWkt), a.location)
            """, nativeQuery = true)
    List<UUID> findLoadableInZone(UUID depotId, String zoneWkt);

    /** Proyección para el dashboard: paquetes por estado (sin cargar entidades). */
    @Query("""
            select p.status as status, count(p) as total from Parcel p
            where p.updatedAt >= :since or p.status not in (com.rutaexpress.parcel.ParcelStatus.DELIVERED,
                                                            com.rutaexpress.parcel.ParcelStatus.RETURNED,
                                                            com.rutaexpress.parcel.ParcelStatus.CANCELLED)
            group by p.status
            """)
    List<StatusCount> countByStatusSince(Instant since);

    interface StatusCount {
        ParcelStatus getStatus();

        long getTotal();
    }

    /** Búsqueda libre para el asistente: código, nombre del destinatario o calle. */
    @EntityGraph(attributePaths = {"recipient", "address"})
    @Query("""
            select p from Parcel p join p.recipient r join p.address a
            where p.id in :candidateIds
              and (lower(p.trackingCode) like :pattern or lower(r.name) like :pattern
                   or lower(a.street) like :pattern)
            """)
    List<Parcel> searchAmong(Collection<UUID> candidateIds, String pattern);
}
