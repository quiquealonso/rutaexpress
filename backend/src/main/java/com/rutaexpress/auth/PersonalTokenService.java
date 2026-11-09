package com.rutaexpress.auth;

import com.rutaexpress.auth.AuthDtos.PersonalTokenCreated;
import com.rutaexpress.auth.AuthDtos.PersonalTokenResponse;
import com.rutaexpress.shared.error.NotFoundException;
import com.rutaexpress.shared.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tokens personales para el cliente MCP externo. Formato: rx_pat_&lt;aleatorio&gt;; solo se guarda el hash. */
@Service
public class PersonalTokenService {

    public static final String PREFIX = "rx_pat_";

    private final PersonalAccessTokenRepository tokens;
    private final UserRepository users;
    private final SecurityProperties properties;
    private final AuthService authService;

    public PersonalTokenService(PersonalAccessTokenRepository tokens, UserRepository users,
                                SecurityProperties properties, AuthService authService) {
        this.tokens = tokens;
        this.users = users;
        this.properties = properties;
        this.authService = authService;
    }

    @Transactional
    public PersonalTokenCreated create(UUID userId, String name, Integer expiresInDays) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario", userId));
        String raw = PREFIX + Hashing.randomToken(32);
        Duration ttl = expiresInDays != null && expiresInDays > 0
                ? Duration.ofDays(Math.min(expiresInDays, 365)) : properties.personalTokenTtl();
        Instant expiresAt = Instant.now().plus(ttl);
        PersonalAccessToken token = tokens.save(new PersonalAccessToken(user, name,
                raw.substring(0, PREFIX.length() + 6), Hashing.sha256(raw), expiresAt));
        return new PersonalTokenCreated(token.getId(), name, raw, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<PersonalTokenResponse> list(UUID userId) {
        Instant now = Instant.now();
        return tokens.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(t -> new PersonalTokenResponse(t.getId(), t.getName(), t.getPrefix(), t.getCreatedAt(),
                        t.getExpiresAt(), t.getLastUsedAt(), t.isActive(now)))
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        PersonalAccessToken token = tokens.findById(tokenId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Token", tokenId));
        token.revoke(Instant.now());
    }

    /** Valida un token en claro y devuelve la identidad asociada. */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return tokens.findByTokenHash(Hashing.sha256(raw))
                .filter(t -> t.isActive(now) && t.getUser().isEnabled())
                .map(t -> {
                    t.markUsed(now);
                    User user = t.getUser();
                    return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name(),
                            authService.driverIdOf(user), "PAT");
                });
    }
}
