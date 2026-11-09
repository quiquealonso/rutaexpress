package com.rutaexpress.auth;

import com.rutaexpress.auth.AuthDtos.TokenResponse;
import com.rutaexpress.auth.AuthDtos.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID = "Credenciales incorrectas";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SecurityProperties properties;
    private final List<DriverDirectory> driverDirectory;
    private final LoginAttemptRecorder attemptRecorder;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, SecurityProperties properties,
                       List<DriverDirectory> driverDirectory, LoginAttemptRecorder attemptRecorder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
        this.driverDirectory = driverDirectory;
        this.attemptRecorder = attemptRecorder;
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return dummyHash;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse login(String email, String password) {
        Instant now = Instant.now();
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            // Mismo coste que un usuario real para no revelar qué emails existen
            passwordEncoder.matches(password, dummyHash());
            throw new BadCredentialsException(INVALID);
        }
        if (!user.isEnabled() || user.isLocked(now)) {
            throw new BadCredentialsException(user.isEnabled()
                    ? "Cuenta bloqueada temporalmente por demasiados intentos" : INVALID);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            attemptRecorder.recordFailure(user.getId());
            throw new BadCredentialsException(INVALID);
        }
        user.registerSuccessfulLogin(now);
        return issueTokens(user, UUID.randomUUID(), now);
    }

    /** Rotación: el refresh token usado se revoca y se emite uno nuevo de la misma familia. */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHash(Hashing.sha256(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token no válido"));
        if (token.getRevokedAt() != null) {
            // Reutilización de un token ya rotado: posible robo. Se invalida toda la familia.
            log.warn("Reutilización de refresh token detectada para el usuario {}", token.getUser().getId());
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw new BadCredentialsException("Refresh token no válido");
        }
        if (!token.isActive(now) || !token.getUser().isEnabled()) {
            throw new BadCredentialsException("Refresh token caducado");
        }
        Issued issued = issue(token.getUser(), token.getFamilyId(), now);
        token.revoke(now, issued.refreshToken().getId());
        return issued.response();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        return toResponse(user);
    }

    UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name(), driverIdOf(user),
                user.isEnabled(), user.getLastLoginAt());
    }

    UUID driverIdOf(User user) {
        if (user.getRole() != Role.DRIVER) {
            return null;
        }
        return driverDirectory.stream()
                .map(d -> d.findDriverIdByUserId(user.getId()))
                .flatMap(java.util.Optional::stream)
                .findFirst().orElse(null);
    }

    private TokenResponse issueTokens(User user, UUID familyId, Instant now) {
        return issue(user, familyId, now).response();
    }

    private Issued issue(User user, UUID familyId, Instant now) {
        UUID driverId = driverIdOf(user);
        String access = tokenService.issueAccessToken(user, driverId);
        String rawRefresh = Hashing.randomToken(32);
        RefreshToken refresh = refreshTokens.save(new RefreshToken(user, Hashing.sha256(rawRefresh), familyId,
                now.plus(properties.refreshTokenTtl())));
        TokenResponse response = new TokenResponse(access, rawRefresh, tokenService.accessTokenTtlSeconds(),
                "Bearer", toResponse(user));
        return new Issued(response, refresh);
    }

    private record Issued(TokenResponse response, RefreshToken refreshToken) {
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int deleted = refreshTokens.deleteExpired(Instant.now().minus(java.time.Duration.ofDays(1)));
        log.info("Refresh tokens caducados eliminados: {}", deleted);
    }

    /** Registra los intentos fallidos en su propia transacción para que no se pierdan al lanzar la excepción. */
    @Service
    static class LoginAttemptRecorder {

        private final UserRepository users;
        private final SecurityProperties properties;

        LoginAttemptRecorder(UserRepository users, SecurityProperties properties) {
            this.users = users;
            this.properties = properties;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void recordFailure(UUID userId) {
            users.findById(userId).ifPresent(u ->
                    u.registerFailedLogin(Instant.now(), properties.maxLoginAttempts(), properties.lockDuration()));
        }
    }
}
