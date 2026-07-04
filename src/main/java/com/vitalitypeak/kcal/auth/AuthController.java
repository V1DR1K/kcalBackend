package com.vitalitypeak.kcal.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import com.vitalitypeak.kcal.auth.AuthDtos.AuthResponse;
import com.vitalitypeak.kcal.auth.AuthDtos.LoginRequest;
import com.vitalitypeak.kcal.auth.AuthDtos.RegisterRequest;
import com.vitalitypeak.kcal.common.BadRequestException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final boolean registrationEnabled;

    public AuthController(AuthService authService,
            @Value("${app.auth.registration-enabled:true}") boolean registrationEnabled) {
        this.authService = authService;
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
}
