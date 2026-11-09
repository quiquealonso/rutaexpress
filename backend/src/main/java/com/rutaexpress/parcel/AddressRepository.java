package com.rutaexpress.parcel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AddressRepository extends JpaRepository<Address, UUID> {

    Optional<Address> findByNormalizedHash(String normalizedHash);

    List<Address> findByNeedsReviewTrueOrderByCreatedAtDesc();
}
