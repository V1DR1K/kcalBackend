package com.scalegrams.auth;

import com.scalegrams.user.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record AuthResponse(String accessToken, String tokenType, String refreshToken, UserSummary user,
            boolean mustChangePassword) {
        public AuthResponse withoutTokens() {
            return new AuthResponse(null, null, null, user, mustChangePassword);
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 128, message = "La contraseña debe tener entre 10 y 128 caracteres") String newPassword) {
    }

    public record UserSummary(Long id, String username, String fullName, String email, String planName, Role role) {
    }
}
