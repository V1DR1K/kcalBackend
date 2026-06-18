package com.vitalitypeak.kcal.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.vitalitypeak.kcal.user.ActivityLevel;
import com.vitalitypeak.kcal.user.FitnessGoal;
import com.vitalitypeak.kcal.user.Gender;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ProfileDtos {
    public record ProfileResponse(Long id, String fullName, String email, String planName, String nutritionStyle,
            BigDecimal weightKg, BigDecimal heightCm, Integer age, Gender gender, ActivityLevel activityLevel,
            FitnessGoal goal, BigDecimal targetWeightKg, Integer dailyCalorieGoal, Integer proteinGoalGrams,
            Integer carbsGoalGrams, Integer fatGoalGrams, BigDecimal waterGoalLiters) {
    }

    public record UpdateProfileRequest(String fullName, @Positive BigDecimal weightKg, @Positive BigDecimal heightCm,
            LocalDate birthDate, Gender gender, ActivityLevel activityLevel, FitnessGoal goal,
            @Positive BigDecimal targetWeightKg, String nutritionStyle, @Positive BigDecimal waterGoalLiters) {
    }

    public record NutritionPlanResponse(Long id, String name, Integer dailyCalories, BigDecimal proteinPercent,
            BigDecimal carbsPercent, BigDecimal fatPercent, Integer proteinGoalGrams, Integer carbsGoalGrams,
            Integer fatGoalGrams, LocalDate startDate, LocalDate endDate) {
    }

    public record UpsertNutritionPlanRequest(@NotBlank @Size(min = 2, max = 120) String name,
            @NotNull @Positive Integer dailyCalories,
            @NotNull @PositiveOrZero BigDecimal proteinPercent,
            @NotNull @PositiveOrZero BigDecimal carbsPercent,
            @NotNull @PositiveOrZero BigDecimal fatPercent,
            @NotNull LocalDate startDate,
            LocalDate endDate) {
    }

    public record NutritionPlanPresetResponse(String key, String name, String description, Integer dailyCalories,
            BigDecimal proteinPercent, BigDecimal carbsPercent, BigDecimal fatPercent) {
    }
}
