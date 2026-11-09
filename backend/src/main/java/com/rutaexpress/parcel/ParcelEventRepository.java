package com.rutaexpress.parcel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ParcelEventRepository extends JpaRepository<ParcelEvent, UUID> {

    List<ParcelEvent> findByParcelIdOrderByOccurredAtAsc(UUID parcelId);
}
