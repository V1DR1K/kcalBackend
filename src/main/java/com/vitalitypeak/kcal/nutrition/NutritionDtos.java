package com.vitalitypeak.kcal.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.catalog.FoodUnit;
import com.vitalitypeak.kcal.profile.ProfileDtos.NutritionPlanResponse;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.vitalitypeak.kcal.catalog.FoodPreparation;

public class NutritionDtos {
    public record FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
            BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            FoodPreparation preparation, String preparationSource, String preparationGroup, String servingName, BigDecimal servingWeightGrams,
            String imageUrl, String source, String sourceId, OffsetDateTime lastSyncedAt, Set<String> tags) {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    public record CreateFoodRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 120) String brand,
            @Pattern(regexp = "^$|\\d{6,32}", message = "Debe contener entre 6 y 32 digitos.") String barcode,
            @NotNull FoodCategory category,
            @NotNull FoodUnit baseUnit,
            @Positive BigDecimal baseQuantity,
            @NotNull @PositiveOrZero Integer calories,
            @NotNull @PositiveOrZero BigDecimal proteinGrams,
            @NotNull @PositiveOrZero BigDecimal carbsGrams,
            @NotNull @PositiveOrZero BigDecimal fatGrams,
            FoodPreparation preparation,
            @Size(max = 80) String servingName,
            @Positive BigDecimal servingWeightGrams,
            @Size(max = 500) String imageUrl,
            @Size(max = 10) Set<@Size(max = 40) String> tags) {
    }

    public record NutritionPreviewRequest(@NotNull Long foodId, @Positive BigDecimal quantity, @NotNull FoodUnit unit) {
    }

    public record NutritionPreviewResponse(Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams) {
    }

    public record AddFoodLogRequest(@NotNull Long foodId, @NotNull MealType mealType, @Positive BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate) {
    }

    public record AddMealLogRequest(@NotNull MealItemType itemType, @NotNull Long itemId, @NotNull MealType mealType,
            @Positive BigDecimal quantity, @NotNull FoodUnit unit, LocalDate logDate) {
    }

    public record UpdateFoodLogRequest(@NotNull MealType mealType, @Positive BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate) {
    }

    public record FoodLogResponse(Long id, LocalDate logDate, MealType mealType, MealItemType itemType, FoodResponse food,
            RecipeResponse recipe, BigDecimal quantity,
            FoodUnit unit, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams) {
    }

    public record AddWaterRequest(LocalDate logDate, @Positive BigDecimal liters) {
    }

    public record MacroProgress(String key, String label, BigDecimal consumed, BigDecimal goal, BigDecimal remaining) {
    }

    public record MealSummary(MealType mealType, String label, Integer calories, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams, List<FoodLogResponse> items) {
    }

    public record DashboardResponse(LocalDate date, Integer calorieGoal, Integer caloriesConsumed, Integer caloriesRemaining,
            List<MacroProgress> macros, List<MealSummary> meals, BigDecimal waterConsumedLiters, BigDecimal waterGoalLiters,
            NutritionPlanResponse plan) {
    }

    public record DaySummary(LocalDate date, Integer caloriesConsumed, Integer calorieGoal, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams, boolean goalReached) {
    }

    public record HistoryResponse(int year, int month, List<DaySummary> days, Integer averageCalories,
            long completedGoalDays) {
    }

    public record MealTypeResponse(MealType code, String label) {
    }

    public record RecipeIngredientRequest(@NotNull Long foodId, @Positive BigDecimal quantity, @NotNull FoodUnit unit) {
    }

    public record CreateRecipeRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 500) String description,
            @Positive BigDecimal totalWeightGrams,
            @NotEmpty @Size(max = 50) List<@NotNull RecipeIngredientRequest> ingredients) {
    }

    public record RecipeIngredientResponse(FoodResponse food, BigDecimal quantity, FoodUnit unit) {
    }

    public record RecipeResponse(Long id, String name, String description, BigDecimal totalWeightGrams, Integer calories,
            BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            List<RecipeIngredientResponse> ingredients) {
    }
}
