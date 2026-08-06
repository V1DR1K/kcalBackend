package com.scalegrams.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import com.scalegrams.auth.AuthDtos.AuthResponse;
import com.scalegrams.auth.AuthDtos.ChangePasswordRequest;
import com.scalegrams.auth.AuthDtos.LoginRequest;
import com.scalegrams.auth.AuthDtos.RefreshRequest;
import com.scalegrams.auth.AuthDtos.RegisterRequest;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.CurrentUser;
import com.scalegrams.user.AppUser;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUser currentUser;
    private final boolean registrationEnabled;

    public AuthController(AuthService authService, CurrentUser currentUser,
            @Value("${app.auth.registration-enabled:true}") boolean registrationEnabled) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.registrationEnabled = registrationEnabled;
    }

    @PostMapping("/register")
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (!registrationEnabled) {
            throw new BadRequestException("El registro de nuevas cuentas no está habilitado.");
        }
        return authService.register(request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.rotate(request.refreshToken());
    }

    @PostMapping("/logout")
    void logout(@RequestBody(required = false) RefreshRequest request) {
        authService.revoke(request == null ? null : request.refreshToken());
    }

    @PutMapping("/change-password")
    AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        AppUser user = currentUser.from(authentication);
        return authService.changePassword(user, request.currentPassword(), request.newPassword());
    }
}
