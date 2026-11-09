package com.rutaexpress.geo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface DistanceCacheRepository extends JpaRepository<DistanceCache, DistanceCache.Key> {

    @Query("select d from DistanceCache d where d.originKey in :keys and d.destinationKey in :keys")
    List<DistanceCache> findAllBetween(Collection<String> keys);

    @Modifying
    @Query("delete from DistanceCache d where d.createdAt < :before")
    int deleteOlderThan(Instant before);
}
