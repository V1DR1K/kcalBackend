package com.vitalitypeak.kcal.auth;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.vitalitypeak.kcal.user.ActivityLevel;
import com.vitalitypeak.kcal.user.FitnessGoal;
import com.vitalitypeak.kcal.user.Gender;
import com.vitalitypeak.kcal.user.Role;

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

    public record AuthResponse(String token, String tokenType, UserSummary user) {
    }

    public record UserSummary(Long id, String fullName, String email, String planName, Role role) {
    }
}
