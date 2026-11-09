package com.rutaexpress.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DriverPositionRepository extends JpaRepository<DriverPosition, UUID> {

    @Query(value = """
            select * from driver_position
            where driver_id = :driverId and recorded_at > :since
            order by recorded_at desc limit 1
            """, nativeQuery = true)
    Optional<DriverPosition> findLatest(UUID driverId, Instant since);

    /** Última posición de cada repartidor (DISTINCT ON de PostgreSQL). */
    @Query(value = """
            select distinct on (driver_id) * from driver_position
            where recorded_at > :since
            order by driver_id, recorded_at desc
            """, nativeQuery = true)
    List<DriverPosition> findLatestOfAll(Instant since);

    @Query("""
            select p from DriverPosition p
            where p.driverId = :driverId and p.recordedAt > :since
            order by p.recordedAt asc
            """)
    List<DriverPosition> findTrail(UUID driverId, Instant since);
}
