package com.rutaexpress.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto que implementa el módulo catalog: dado un usuario, devuelve su repartidor.
 * Así auth puede meter el driverId en el token sin depender de catalog (evita el ciclo).
 */
public interface DriverDirectory {

    Optional<UUID> findDriverIdByUserId(UUID userId);
}
