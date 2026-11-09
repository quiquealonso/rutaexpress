package com.rutaexpress.parcel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** RGPD: los datos personales de los destinatarios se anonimizan pasado el periodo de retención. */
@Component
class DataRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);

    private final RecipientRepository recipients;
    private final Duration retention;

    DataRetentionJob(RecipientRepository recipients,
                     @Value("${rutaexpress.privacy.recipient-retention:P90D}") Duration retention) {
        this.recipients = recipients;
        this.retention = retention;
    }

    @Scheduled(cron = "${rutaexpress.privacy.anonymize-cron:0 15 2 * * *}")
    @Transactional
    public void anonymizeOldRecipients() {
        Instant now = Instant.now();
        var candidates = recipients.findAnonymizable(now.minus(retention));
        candidates.forEach(r -> r.anonymize(now));
        if (!candidates.isEmpty()) {
            log.info("Destinatarios anonimizados por retención: {}", candidates.size());
        }
    }
}
