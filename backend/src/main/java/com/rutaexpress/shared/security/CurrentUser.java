package com.rutaexpress.shared.security;

import com.rutaexpress.shared.error.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/** Acceso al usuario autenticado desde cualquier módulo. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        if (principal instanceof Jwt jwt) {
            return Optional.of(fromJwt(jwt));
        }
        return Optional.empty();
    }

    public static AuthenticatedUser get() {
        return find().orElseThrow(() -> new ForbiddenException("No hay un usuario autenticado"));
    }

    /** Id del repartidor autenticado. Nunca se acepta como parámetro: siempre sale del token. */
    public static UUID driverId() {
        AuthenticatedUser user = get();
        if (user.driverId() == null) {
            throw new ForbiddenException("Esta operación solo está disponible para repartidores");
        }
        return user.driverId();
    }

    /** Un repartidor solo puede acceder a lo suyo; administración y tráfico, a todo. */
    public static void checkDriverAccess(UUID ownerDriverId) {
        AuthenticatedUser user = get();
        if (user.isStaff()) {
            return;
        }
        if (user.driverId() == null || !user.driverId().equals(ownerDriverId)) {
            throw new ForbiddenException("No tienes acceso a este recurso");
        }
    }

    public static String actorName() {
        return find().map(AuthenticatedUser::email).orElse("system");
    }

    public static AuthenticatedUser fromJwt(Jwt jwt) {
        String driverId = jwt.getClaimAsString("driverId");
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("role"),
                driverId == null ? null : UUID.fromString(driverId),
                "JWT");
    }
}
