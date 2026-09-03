package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.Map;

import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.CookedYieldSource;
import com.scalegrams.profile.ProfileDtos.NutritionPlanResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.scalegrams.catalog.ModerationStatus;

public class NutritionDtos {
    public record NutrientValueResponse(String code, String name, String group, String unit, BigDecimal value,
            String source, String status) { }

    public record NutrientInput(@NotBlank @Size(max = 80) String code, @NotNull @PositiveOrZero BigDecimal value) { }

    public record NutrientUpdateRequest(@NotEmpty @Size(max = 40) List<@Valid NutrientInput> nutrients) { }

    public record FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
            BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            FoodPreparation preparation, String preparationSource, String preparationGroup, String servingName, BigDecimal servingWeightGrams,
            String imageUrl, String source, String sourceId, OffsetDateTime lastSyncedAt, Set<String> tags,
            Long createdById, OffsetDateTime createdAt, ModerationStatus moderationStatus, List<NutrientValueResponse> nutrients,
            BigDecimal cookedYieldFactor, CookedYieldSource cookedYieldSource, String cookedYieldAssumption) {
        public FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
                BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
                FoodPreparation preparation, String preparationSource, String preparationGroup, String servingName, BigDecimal servingWeightGrams,
                String imageUrl, String source, String sourceId, OffsetDateTime lastSyncedAt, Set<String> tags,
                Long createdById, OffsetDateTime createdAt, ModerationStatus moderationStatus) {
            this(id, name, brand, barcode, category, baseUnit, baseQuantity, calories, proteinGrams, carbsGrams, fatGrams,
                    preparation, preparationSource, preparationGroup, servingName, servingWeightGrams, imageUrl, source, sourceId,
                    lastSyncedAt, tags, createdById, createdAt, moderationStatus, List.of(), null, null, null);
        }
    }

    public record FoodSummaryResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
            BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams,
            BigDecimal fatGrams, FoodPreparation preparation, String preparationGroup, String servingName,
            BigDecimal servingWeightGrams, String imageUrl, List<NutrientValueResponse> nutrients,
            BigDecimal cookedYieldFactor, CookedYieldSource cookedYieldSource, String cookedYieldAssumption) {
        public FoodSummaryResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
                BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams,
                BigDecimal fatGrams, FoodPreparation preparation, String preparationGroup, String servingName,
                BigDecimal servingWeightGrams, String imageUrl) {
            this(id, name, brand, barcode, category, baseUnit, baseQuantity, calories, proteinGrams, carbsGrams, fatGrams,
                    preparation, preparationGroup, servingName, servingWeightGrams, imageUrl, List.of(), null, null, null);
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    public record CreateFoodRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 120) String brand,
            @Pattern(regexp = "^$|\\d{6,32}", message = "Debe contener entre 6 y 32 digitos.") String barcode,
            @NotNull FoodCategory category,
            @NotNull FoodUnit baseUnit,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal baseQuantity,
            @PositiveOrZero Integer calories,
            @NotNull @PositiveOrZero @Digits(integer = 36, fraction = 2) BigDecimal proteinGrams,
            @NotNull @PositiveOrZero @Digits(integer = 36, fraction = 2) BigDecimal carbsGrams,
            @NotNull @PositiveOrZero @Digits(integer = 36, fraction = 2) BigDecimal fatGrams,
            FoodPreparation preparation,
            @Size(max = 80) String servingName,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal servingWeightGrams,
            @Size(max = 10) Set<@Size(max = 40) String> tags,
            @Positive @DecimalMax("10") BigDecimal cookedYieldFactor,
            @Size(max = 240) String cookedYieldAssumption) {
        @AssertTrue(message = "El supuesto de rendimiento requiere un factor manual.")
        public boolean hasFactorWhenCookedYieldAssumptionIsProvided() {
            return cookedYieldAssumption == null || cookedYieldAssumption.isBlank() || cookedYieldFactor != null;
        }
    }

    public record NutritionPreviewRequest(@NotNull Long foodId, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity, @NotNull FoodUnit unit) {
    }

    public record NutritionPreviewResponse(Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            List<NutrientValueResponse> nutrients) {
        public NutritionPreviewResponse(Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams) {
            this(calories, proteinGrams, carbsGrams, fatGrams, List.of());
        }
    }

    public record AddFoodLogRequest(@NotNull Long foodId, @NotNull MealType mealType, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate) {
    }

    public record AddMealLogRequest(@NotNull MealItemType itemType, @NotNull Long itemId, @NotNull MealType mealType,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity, @NotNull FoodUnit unit, LocalDate logDate) {
    }

    public record BatchAddMealLogsRequest(
            @NotEmpty @Size(max = 50) List<@NotNull @Valid AddMealLogRequest> logs) {
    }

    public record AddRecipeMealLogRequest(@NotNull Long recipeId, @NotNull MealType mealType,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity, LocalDate logDate,
            @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients) {
    }

    public record UpdateFoodLogRequest(@NotNull MealType mealType, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate, Long itemId) {
    }

    public record UpdateRecipeFoodLogRequest(@NotNull MealType mealType, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity,
            LocalDate logDate, @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> recipeIngredients) {
    }

    public record UpdateRecipeLogIngredientsRequest(
            @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients) {
    }

    public record FoodLogResponse(Long id, LocalDate logDate, MealType mealType, MealItemType itemType, FoodResponse food,
            RecipeResponse recipe, BigDecimal quantity,
            FoodUnit unit, BigDecimal recipeRawTotalWeightGrams, BigDecimal recipeCookedTotalWeightGrams,
            Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            boolean recipeAdjusted, String displayName, Integer aiEstimateConfidence, String aiEstimateDetails,
            List<NutrientValueResponse> nutrients) {
        public FoodLogResponse(Long id, LocalDate logDate, MealType mealType, MealItemType itemType, FoodResponse food,
                RecipeResponse recipe, BigDecimal quantity, FoodUnit unit, Integer calories, BigDecimal proteinGrams,
                BigDecimal carbsGrams, BigDecimal fatGrams, boolean recipeAdjusted, String displayName,
                Integer aiEstimateConfidence, String aiEstimateDetails) {
            this(id, logDate, mealType, itemType, food, recipe, quantity, unit, null, null, calories, proteinGrams, carbsGrams, fatGrams,
                    recipeAdjusted, displayName, aiEstimateConfidence, aiEstimateDetails, List.of());
        }
    }

    public record AiEstimateItem(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotNull @Positive @Digits(integer = 36, fraction = 2) BigDecimal estimatedGrams,
            FoodCategory category,
            FoodPreparation preparation,
            @NotNull @PositiveOrZero BigDecimal proteinGrams,
            @NotNull @PositiveOrZero BigDecimal carbsGrams,
            @NotNull @PositiveOrZero BigDecimal fatGrams,
            Map<String, BigDecimal> nutrients,
            Long catalogFoodId,
            String catalogMatchType,
            Integer catalogMatchConfidence) {
        public AiEstimateItem(String name, BigDecimal estimatedGrams, FoodCategory category, FoodPreparation preparation,
                BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams) {
            this(name, estimatedGrams, category, preparation, proteinGrams, carbsGrams, fatGrams, Map.of(), null, null, null);
        }

        public AiEstimateItem(String name, BigDecimal estimatedGrams, FoodCategory category, FoodPreparation preparation,
                BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams, Map<String, BigDecimal> nutrients) {
            this(name, estimatedGrams, category, preparation, proteinGrams, carbsGrams, fatGrams, nutrients, null, null, null);
        }
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
            @NotNull @Positive @DecimalMax("3000") @Digits(integer = 36, fraction = 2) BigDecimal servedGrams) {
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
            @NotNull @PositiveOrZero BigDecimal fatGrams,
            Map<String, BigDecimal> nutrients) {
        public AiEstimateFoodProposal(String name, FoodCategory category, FoodPreparation preparation,
                BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams) {
            this(name, category, preparation, proteinGrams, carbsGrams, fatGrams, Map.of());
        }
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

    public record RecentMealResponse(LocalDate sourceDate, MealType mealType, String label, Integer calories,
            BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams, List<FoodLogResponse> items) {
    }

    public record DashboardResponse(LocalDate date, Integer calorieGoal, Integer caloriesConsumed, Integer caloriesRemaining,
            List<MacroProgress> macros, List<MealSummary> meals, BigDecimal waterConsumedLiters, BigDecimal waterGoalLiters,
            NutritionPlanResponse plan, List<NutrientValueResponse> nutrients) {
        public DashboardResponse(LocalDate date, Integer calorieGoal, Integer caloriesConsumed, Integer caloriesRemaining,
                List<MacroProgress> macros, List<MealSummary> meals, BigDecimal waterConsumedLiters, BigDecimal waterGoalLiters,
                NutritionPlanResponse plan) {
            this(date, calorieGoal, caloriesConsumed, caloriesRemaining, macros, meals, waterConsumedLiters, waterGoalLiters, plan, List.of());
        }
    }

    public record DaySummary(LocalDate date, Integer caloriesConsumed, Integer calorieGoal, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams, boolean goalReached, Long planId, String planName) {
    }

    public record HistoryResponse(int year, int month, List<DaySummary> days, Integer averageCalories,
            long completedGoalDays) {
    }

    public record MealTypeResponse(MealType code, String label) {
    }

    public record DayPresetItemRequest(
            @NotNull MealItemType itemType,
            Long itemId,
            @NotNull MealType mealType,
            @NotNull @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity,
            @NotNull FoodUnit unit,
            String displayName,
            Integer calories,
            @NotNull @PositiveOrZero BigDecimal proteinGrams,
            @NotNull @PositiveOrZero BigDecimal carbsGrams,
            @NotNull @PositiveOrZero BigDecimal fatGrams,
            Integer aiEstimateConfidence,
            String aiEstimateDetails) {
    }

    public record CreateDayPresetRequest(
            @NotBlank @Size(min = 1, max = 120) String name,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid DayPresetItemRequest> items) {
    }

    public record UpdateDayPresetRequest(
            @NotBlank @Size(min = 1, max = 120) String name,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid DayPresetItemRequest> items) {
    }

    public record DayPresetResponse(Long id, String name, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<DayPresetItemRequest> items, int itemCount, Map<String, Integer> mealCounts) {
    }

    public record ApplyDayPresetRequest(@NotNull LocalDate logDate, boolean replace) {
    }

    public record RecipeIngredientRequest(@NotNull Long foodId, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity, @NotNull FoodUnit unit) {
    }

    public record CreateRecipeRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 500) String description,
            BigDecimal totalWeightGrams,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal cookedTotalWeightGrams,
            boolean clearCookedTotalWeight,
            @NotEmpty @Size(max = 50) List<@NotNull RecipeIngredientRequest> ingredients) {
        @AssertTrue(message = "No se puede informar y borrar el peso cocido al mismo tiempo.")
        public boolean hasConsistentCookedWeightInstruction() {
            return !clearCookedTotalWeight || cookedTotalWeightGrams == null;
        }
    }

    public record CreateRecipeFromMealRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 500) String description,
            @NotNull MealType mealType,
            LocalDate logDate,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal cookedTotalWeightGrams) {
    }

    public record RecipeIngredientResponse(FoodResponse food, BigDecimal quantity, FoodUnit unit) {
    }

    public record RecipeResponse(Long id, String name, String description, BigDecimal totalWeightGrams,
            BigDecimal rawTotalWeightGrams, BigDecimal cookedTotalWeightGrams, Integer calories,
            BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            List<RecipeIngredientResponse> ingredients, List<NutrientValueResponse> nutrients) {
        public RecipeResponse(Long id, String name, String description, BigDecimal totalWeightGrams, Integer calories,
                BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
                List<RecipeIngredientResponse> ingredients) {
            this(id, name, description, totalWeightGrams, totalWeightGrams, null, calories, proteinGrams, carbsGrams, fatGrams,
                    ingredients, List.of());
        }
    }

    public record RecipeFromMealResponse(RecipeResponse recipe, List<String> skippedItems) {
    }

    public record RecipeOwnerResponse(Long id, String fullName, long recipeCount) {
    }
}
