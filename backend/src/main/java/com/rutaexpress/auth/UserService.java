package com.rutaexpress.auth;

import com.rutaexpress.auth.AuthDtos.CreateUserRequest;
import com.rutaexpress.auth.AuthDtos.UpdateUserRequest;
import com.rutaexpress.auth.AuthDtos.UserResponse;
import com.rutaexpress.shared.error.BusinessException;
import com.rutaexpress.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** API pública del módulo auth para gestionar usuarios (la usa catalog al dar de alta repartidores). */
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, AuthService authService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return authService.toResponse(createUser(request.email(), request.password(), request.role()));
    }

    @Transactional
    public UUID createUserAccount(String email, String rawPassword, Role role) {
        return createUser(email, rawPassword, role).getId();
    }

    private User createUser(String email, String rawPassword, Role role) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("email-taken", "Ya existe un usuario con el email " + email);
        }
        return users.save(new User(email, passwordEncoder.encode(rawPassword), role));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario", id));
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
            if (!request.enabled()) {
                authService.revokeAllSessions(id);
            }
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            authService.revokeAllSessions(id);
        }
        return authService.toResponse(user);
    }

    @Transactional
    public void disable(UUID id) {
        users.findById(id).ifPresent(u -> {
            u.setEnabled(false);
            authService.revokeAllSessions(id);
        });
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(authService::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean exists(String email) {
        return users.existsByEmailIgnoreCase(email);
    }
}
