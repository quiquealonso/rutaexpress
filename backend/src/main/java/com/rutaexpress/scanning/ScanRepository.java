package com.rutaexpress.scanning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ScanRepository extends JpaRepository<Scan, UUID> {

    Optional<Scan> findByClientScanId(UUID clientScanId);

    boolean existsByLoadIdAndParcelIdAndResult(UUID loadId, UUID parcelId, Scan.Result result);

    List<Scan> findByLoadIdOrderByScannedAtDesc(UUID loadId);

    @Query("select s.parcelId from Scan s where s.load.id = :loadId and s.result = com.rutaexpress.scanning.Scan.Result.OK")
    List<UUID> findLoadedParcelIds(UUID loadId);
}
