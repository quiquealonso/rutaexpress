package com.rutaexpress.auth;

import com.rutaexpress.shared.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Autentica peticiones con "Authorization: Bearer rx_pat_..." (clientes MCP externos). */
class PersonalTokenAuthenticationFilter extends OncePerRequestFilter {

    private final PersonalTokenService personalTokens;

    PersonalTokenAuthenticationFilter(PersonalTokenService personalTokens) {
        this.personalTokens = personalTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer " + PersonalTokenService.PREFIX)) {
            String raw = header.substring("Bearer ".length()).trim();
            var user = personalTokens.authenticate(raw);
            if (user.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token personal no válido");
                return;
            }
            AuthenticatedUser principal = user.get();
            var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()),
                            new SimpleGrantedAuthority("SCOPE_mcp")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
