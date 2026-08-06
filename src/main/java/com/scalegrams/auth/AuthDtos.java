package com.scalegrams.auth;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.scalegrams.user.ActivityLevel;
import com.scalegrams.user.FitnessGoal;
import com.scalegrams.user.Gender;
import com.scalegrams.user.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6) String password,
            @Positive BigDecimal weightKg,
            @Positive BigDecimal heightCm,
            LocalDate birthDate,
            @NotNull Gender gender,
            @NotNull FitnessGoal goal,
            @NotNull ActivityLevel activityLevel) {
    }

    public record AuthResponse(String token, String tokenType, String refreshToken, UserSummary user) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String newPassword) {
    }

    public record UserSummary(Long id, String fullName, String email, String planName, Role role) {
    }
}
