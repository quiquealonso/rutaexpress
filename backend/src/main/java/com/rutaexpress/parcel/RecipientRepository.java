package com.rutaexpress.parcel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    /** Destinatarios cuyos paquetes están todos cerrados desde antes de la fecha dada (candidatos a anonimizar). */
    @Query("""
            select r from Recipient r
            where r.anonymizedAt is null
              and not exists (select 1 from Parcel p where p.recipient = r
                              and (p.status not in (com.rutaexpress.parcel.ParcelStatus.DELIVERED,
                                                    com.rutaexpress.parcel.ParcelStatus.RETURNED,
                                                    com.rutaexpress.parcel.ParcelStatus.CANCELLED)
                                   or p.updatedAt > :before))
            """)
    List<Recipient> findAnonymizable(Instant before);
}
