package com.vitalitypeak.kcal.auth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

import com.vitalitypeak.kcal.user.AppUser;
import com.vitalitypeak.kcal.user.FitnessGoal;
import com.vitalitypeak.kcal.user.Gender;

public final class NutritionGoalCalculator {
    private NutritionGoalCalculator() {
    }

    public static void apply(AppUser user) {
        if (user.getWeightKg() == null || user.getHeightCm() == null || user.getBirthDate() == null || user.getGender() == null) {
            return;
        }
        int age = Math.max(16, Period.between(user.getBirthDate(), LocalDate.now()).getYears());
        double base = 10 * user.getWeightKg().doubleValue() + 6.25 * user.getHeightCm().doubleValue() - 5 * age;
        base += user.getGender() == Gender.FEMALE ? -161 : 5;
        int maintenance = (int) Math.round(base * user.getActivityLevel().multiplier());
        int calories = switch (user.getGoal()) {
            case LOSE -> maintenance - 400;
            case GAIN -> maintenance + 300;
            case MAINTAIN -> maintenance;
        };
        calories = Math.max(1300, calories);
        user.setDailyCalorieGoal(calories);
        user.setProteinGoalGrams(user.getWeightKg().multiply(BigDecimal.valueOf(2.2)).setScale(0, RoundingMode.HALF_UP).intValue());
        user.setFatGoalGrams(Math.max(45, (int) Math.round((calories * 0.25) / 9)));
        int proteinCalories = user.getProteinGoalGrams() * 4;
        int fatCalories = user.getFatGoalGrams() * 9;
        user.setCarbsGoalGrams(Math.max(60, (calories - proteinCalories - fatCalories) / 4));
    }
}
