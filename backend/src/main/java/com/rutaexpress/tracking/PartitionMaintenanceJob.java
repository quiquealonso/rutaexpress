package com.rutaexpress.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * driver_position está particionada por día: se crean las particiones de los próximos días y se eliminan
 * las que superan la retención (borrar una partición es instantáneo, a diferencia de un DELETE masivo).
 */
@Component
class PartitionMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceJob.class);
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    PartitionMaintenanceJob(JdbcTemplate jdbc, @Value("${rutaexpress.tracking.retention-days:30}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        maintain();
    }

    @Scheduled(cron = "0 5 0 * * *")
    void maintain() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= 7; i++) {
            createPartition(today.plusDays(i));
        }
        dropOldPartitions(today.minusDays(retentionDays));
    }

    private void createPartition(LocalDate day) {
        String name = "driver_position_" + day.format(SUFFIX);
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + name + " PARTITION OF driver_position FOR VALUES FROM ('"
                    + day + "') TO ('" + day.plusDays(1) + "')");
        } catch (RuntimeException e) {
            log.warn("No se pudo crear la partición {}: {}", name, e.getMessage());
        }
    }

    private void dropOldPartitions(LocalDate before) {
        List<String> partitions = jdbc.queryForList("""
                select c.relname from pg_inherits i
                join pg_class c on c.oid = i.inhrelid
                join pg_class p on p.oid = i.inhparent
                where p.relname = 'driver_position' and c.relname ~ '^driver_position_[0-9]{8}$'
                """, String.class);
        String limit = "driver_position_" + before.format(SUFFIX);
        for (String partition : partitions) {
            if (partition.compareTo(limit) < 0) {
                jdbc.execute("DROP TABLE IF EXISTS " + partition);
                log.info("Partición eliminada por retención: {}", partition);
            }
        }
    }
}
