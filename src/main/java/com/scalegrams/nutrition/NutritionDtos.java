package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.profile.ProfileDtos.NutritionPlanResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.ModerationStatus;

public class NutritionDtos {
    public record FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
            BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            FoodPreparation preparation, String preparationSource, String preparationGroup, String servingName, BigDecimal servingWeightGrams,
            String imageUrl, String source, String sourceId, OffsetDateTime lastSyncedAt, Set<String> tags,
            Long createdById, OffsetDateTime createdAt, ModerationStatus moderationStatus) {
    }

    public record FoodSummaryResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
            BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams,
            BigDecimal fatGrams, FoodPreparation preparation, String preparationGroup, String servingName,
            BigDecimal servingWeightGrams, String imageUrl) {
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
            @PositiveOrZero Integer calories,
            @NotNull @PositiveOrZero BigDecimal proteinGrams,
            @NotNull @PositiveOrZero BigDecimal carbsGrams,
            @NotNull @PositiveOrZero BigDecimal fatGrams,
            FoodPreparation preparation,
            @Size(max = 80) String servingName,
            @Positive BigDecimal servingWeightGrams,
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

    public record BatchAddMealLogsRequest(
            @NotEmpty @Size(max = 50) List<@NotNull @Valid AddMealLogRequest> logs) {
    }

    public record AddRecipeMealLogRequest(@NotNull Long recipeId, @NotNull MealType mealType,
            @Positive BigDecimal quantity, LocalDate logDate,
            @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients) {
    }

    public record UpdateFoodLogRequest(@NotNull MealType mealType, @Positive BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate, Long itemId) {
    }

    public record UpdateRecipeFoodLogRequest(@NotNull MealType mealType, @Positive BigDecimal quantity,
            LocalDate logDate, @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> recipeIngredients) {
    }

    public record UpdateRecipeLogIngredientsRequest(
            @NotEmpty @Size(max = 50) List<@NotNull RecipeIngredientRequest> ingredients) {
    }

    public record FoodLogResponse(Long id, LocalDate logDate, MealType mealType, MealItemType itemType, FoodResponse food,
            RecipeResponse recipe, BigDecimal quantity,
            FoodUnit unit, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            boolean recipeAdjusted, String displayName, Integer aiEstimateConfidence, String aiEstimateDetails) {
    }

    public record AiEstimateItem(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotNull @Positive BigDecimal estimatedGrams,
            @NotNull @PositiveOrZero BigDecimal proteinGrams,
            @NotNull @PositiveOrZero BigDecimal carbsGrams,
            @NotNull @PositiveOrZero BigDecimal fatGrams) {
    }

    public record AiEstimateResponse(
            @NotBlank String name,
            String description,
            int confidence,
            List<String> assumptions,
            List<AiEstimateItem> items,
            AiEstimateUsageResponse usage) {
    }

    public record AiTranscriptionResponse(@NotBlank String transcript) {
    }

    public record AiEstimateUsageResponse(boolean available, int used, OffsetDateTime blockedUntil, String status) {
    }

    public record AiEstimateDraft(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 240) String description,
            @NotNull @PositiveOrZero Integer confidence,
            @Size(max = 4) List<@NotBlank @Size(max = 160) String> assumptions,
            @NotEmpty @Size(max = 12) List<@Valid AiEstimateItem> items) {
    }

    public record RefineAiEstimateRequest(
            @NotNull @Valid AiEstimateDraft currentEstimate,
            @NotBlank @Size(max = 240) String correction) {
    }

    public record ConfirmAiEstimateRequest(
            @NotNull MealType mealType,
            LocalDate logDate,
            @NotEmpty @Size(max = 12) List<@NotNull @Valid ConfirmAiEstimateItem> items) {
    }

    public record ConfirmAiEstimateItem(
            @Positive Long foodId,
            @Valid AiEstimateFoodProposal proposal,
            @NotNull @Positive @DecimalMax("3000") BigDecimal servedGrams) {
        @AssertTrue(message = "Debe informar exactamente uno de foodId o proposal.")
        public boolean hasExactlyOneFoodSource() {
            return (foodId == null) != (proposal == null);
        }
    }

    public record AiEstimateFoodProposal(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotNull FoodCategory category,
            FoodPreparation preparation,
            @NotNull @PositiveOrZero BigDecimal proteinGrams,
            @NotNull @PositiveOrZero BigDecimal carbsGrams,
            @NotNull @PositiveOrZero BigDecimal fatGrams) {
    }

    public record UpdateAiEstimateRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 240) String description,
            @Size(max = 240) String context,
            @NotNull MealType mealType,
            LocalDate logDate,
            @NotNull @PositiveOrZero Integer confidence,
            @Size(max = 4) List<@NotBlank @Size(max = 160) String> assumptions,
            @NotEmpty @Size(max = 12) List<@Valid AiEstimateItem> items) {
    }

    public record SaveAiEstimateItemRequest(
            @NotNull FoodCategory category,
            FoodPreparation preparation,
            @Size(max = 10) Set<@Size(max = 40) String> tags) {
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
            BigDecimal carbsGrams, BigDecimal fatGrams, boolean goalReached, Long planId, String planName) {
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
            BigDecimal totalWeightGrams,
            @NotEmpty @Size(max = 50) List<@NotNull RecipeIngredientRequest> ingredients) {
    }

    public record RecipeIngredientResponse(FoodResponse food, BigDecimal quantity, FoodUnit unit) {
    }

    public record RecipeResponse(Long id, String name, String description, BigDecimal totalWeightGrams, Integer calories,
            BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            List<RecipeIngredientResponse> ingredients) {
    }
}
