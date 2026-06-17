package com.vitalitypeak.kcal.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.catalog.FoodUnit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class NutritionDtos {
    public record FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
            BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            String imageUrl, Set<String> tags) {
    }

    public record NutritionPreviewRequest(@NotNull Long foodId, @Positive BigDecimal quantity, @NotNull FoodUnit unit) {
    }

    public record NutritionPreviewResponse(Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams) {
    }

    public record AddFoodLogRequest(@NotNull Long foodId, @NotNull MealType mealType, @Positive BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate) {
    }

    public record FoodLogResponse(Long id, LocalDate logDate, MealType mealType, FoodResponse food, BigDecimal quantity,
            FoodUnit unit, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams) {
    }

    public record AddWaterRequest(LocalDate logDate, @Positive BigDecimal liters) {
    }

    public record MacroProgress(String key, String label, BigDecimal consumed, BigDecimal goal, BigDecimal remaining) {
    }

    public record MealSummary(MealType mealType, String label, Integer calories, List<FoodLogResponse> items) {
    }

    public record DashboardResponse(LocalDate date, Integer calorieGoal, Integer caloriesConsumed, Integer caloriesRemaining,
            List<MacroProgress> macros, List<MealSummary> meals, BigDecimal waterConsumedLiters, BigDecimal waterGoalLiters) {
    }

    public record DaySummary(LocalDate date, Integer caloriesConsumed, Integer calorieGoal, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams, boolean goalReached) {
    }

    public record HistoryResponse(int year, int month, List<DaySummary> days, Integer averageCalories,
            long completedGoalDays) {
    }
}
