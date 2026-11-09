package com.rutaexpress.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface DepotRepository extends JpaRepository<Depot, UUID> {
}
