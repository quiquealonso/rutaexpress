package com.rutaexpress.catalog;

import com.rutaexpress.auth.DriverDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
class DriverDirectoryAdapter implements DriverDirectory {

    private final DriverRepository drivers;

    DriverDirectoryAdapter(DriverRepository drivers) {
        this.drivers = drivers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findDriverIdByUserId(UUID userId) {
        return drivers.findByUserId(userId).map(Driver::getId);
    }
}
