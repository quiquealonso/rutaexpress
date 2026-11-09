package com.rutaexpress.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    List<Incident> findByResolvedAtIsNullOrderByCreatedAtDesc();

    long countByCreatedAtAfter(Instant since);
}
