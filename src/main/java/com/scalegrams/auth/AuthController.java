package com.scalegrams.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scalegrams.auth.AuthDtos.AuthResponse;
import com.scalegrams.auth.AuthDtos.ChangePasswordRequest;
import com.scalegrams.auth.AuthDtos.LoginRequest;
import com.scalegrams.auth.AuthDtos.RefreshRequest;
import com.scalegrams.common.CurrentUser;
import com.scalegrams.user.AppUser;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import jakarta.servlet.http.HttpServletRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    void logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
    }

    @PutMapping("/change-password")
    AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication,
            HttpServletRequest httpRequest) {
        AppUser user = currentUser.from(authentication);
        String authorization = httpRequest.getHeader("Authorization");
        String accessToken = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        return authService.changePassword(user, accessToken, request.currentPassword(), request.newPassword());
    }
}
