package com.rutaexpress.auth;

import com.rutaexpress.auth.AuthDtos.CreatePersonalTokenRequest;
import com.rutaexpress.auth.AuthDtos.LoginRequest;
import com.rutaexpress.auth.AuthDtos.PersonalTokenCreated;
import com.rutaexpress.auth.AuthDtos.PersonalTokenResponse;
import com.rutaexpress.auth.AuthDtos.RefreshRequest;
import com.rutaexpress.auth.AuthDtos.TokenResponse;
import com.rutaexpress.auth.AuthDtos.UserResponse;
import com.rutaexpress.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Autenticación")
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final PersonalTokenService personalTokens;

    AuthController(AuthService authService, PersonalTokenService personalTokens) {
        this.authService = authService;
        this.personalTokens = personalTokens;
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    UserResponse me() {
        return authService.me(CurrentUser.get().userId());
    }

    /** Tokens personales del usuario actual, para conectar un cliente MCP externo. */
    @GetMapping("/tokens")
    List<PersonalTokenResponse> tokens() {
        return personalTokens.list(CurrentUser.get().userId());
    }

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    PersonalTokenCreated createToken(@Valid @RequestBody CreatePersonalTokenRequest request) {
        return personalTokens.create(CurrentUser.get().userId(), request.name(), request.expiresInDays());
    }

    @DeleteMapping("/tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeToken(@PathVariable UUID id) {
        personalTokens.revoke(CurrentUser.get().userId(), id);
    }
}
