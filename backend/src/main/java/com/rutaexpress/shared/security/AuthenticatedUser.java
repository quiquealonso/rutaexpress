package com.rutaexpress.shared.security;

import java.util.UUID;

/**
 * Identidad del usuario autenticado, independiente de cómo se autenticó (JWT o token personal MCP).
 * {@code driverId} solo está presente para el rol DRIVER.
 */
public record AuthenticatedUser(UUID userId, String email, String role, UUID driverId, String authMethod) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_DISPATCHER = "DISPATCHER";
    public static final String ROLE_DRIVER = "DRIVER";

    public boolean isDriver() {
        return ROLE_DRIVER.equals(role);
    }

    public boolean isStaff() {
        return ROLE_ADMIN.equals(role) || ROLE_DISPATCHER.equals(role);
    }
}
