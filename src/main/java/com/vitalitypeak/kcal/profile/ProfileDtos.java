package com.vitalitypeak.kcal.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.vitalitypeak.kcal.user.ActivityLevel;
import com.vitalitypeak.kcal.user.FitnessGoal;
import com.vitalitypeak.kcal.user.Gender;

import jakarta.validation.constraints.Positive;

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
}
