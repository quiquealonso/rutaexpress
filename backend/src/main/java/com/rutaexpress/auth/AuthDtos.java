package com.rutaexpress.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType,
                                UserResponse user) {
    }

    public record UserResponse(UUID id, String email, String role, UUID driverId, boolean enabled,
                               Instant lastLoginAt) {
    }

    public record CreateUserRequest(@NotBlank @Email String email,
                                    @NotBlank @Size(min = 10, max = 100) String password,
                                    @NotNull Role role) {
    }

    public record UpdateUserRequest(Role role, Boolean enabled, @Size(min = 10, max = 100) String password) {
    }

    public record CreatePersonalTokenRequest(@NotBlank @Size(max = 80) String name, Integer expiresInDays) {
    }

    /** El token en claro solo se devuelve una vez, al crearlo. */
    public record PersonalTokenCreated(UUID id, String name, String token, Instant expiresAt) {
    }

    public record PersonalTokenResponse(UUID id, String name, String prefix, Instant createdAt, Instant expiresAt,
                                        Instant lastUsedAt, boolean active) {
    }
}
