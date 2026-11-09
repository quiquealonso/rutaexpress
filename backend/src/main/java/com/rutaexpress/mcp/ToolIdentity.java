package com.rutaexpress.mcp;

import com.rutaexpress.shared.error.ForbiddenException;
import com.rutaexpress.shared.events.EventSource;
import com.rutaexpress.shared.security.AuthenticatedUser;
import com.rutaexpress.shared.security.CurrentUser;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Identidad con la que se ejecuta una herramienta. Nunca se recibe un driverId como parámetro:
 * <ul>
 *   <li>Asistente integrado: el controlador mete el usuario autenticado en el ToolContext.</li>
 *   <li>Cliente MCP externo: el contexto de seguridad de la petición (token personal), que se propaga
 *   a los hilos de Reactor del SDK MCP (ver {@link ReactorSecurityPropagation}).</li>
 * </ul>
 * Si no hay ninguna de las dos, la herramienta falla cerrada.
 */
final class ToolIdentity {

    static final String USER_KEY = "rutaexpress.user";
    static final String SOURCE_KEY = "rutaexpress.source";

    private ToolIdentity() {
    }

    static Map<String, Object> context(AuthenticatedUser user, EventSource source) {
        return Map.of(USER_KEY, user, SOURCE_KEY, source);
    }

    static EventSource source(ToolContext ctx) {
        Object s = ctx == null ? null : ctx.getContext().get(SOURCE_KEY);
        return s instanceof EventSource es ? es : EventSource.MCP;
    }

    static <T> T run(ToolContext ctx, Supplier<T> action) {
        Object fromContext = ctx == null ? null : ctx.getContext().get(USER_KEY);
        if (fromContext instanceof AuthenticatedUser user) {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
            SecurityContextHolder.setContext(context);
            try {
                return checked(action);
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        }
        return checked(action);
    }

    private static <T> T checked(Supplier<T> action) {
        AuthenticatedUser user = CurrentUser.find()
                .orElseThrow(() -> new ForbiddenException("Herramienta sin usuario autenticado"));
        if (!user.isDriver() || user.driverId() == null) {
            throw new ForbiddenException("Las herramientas del asistente son solo para repartidores");
        }
        return action.get();
    }
}
