package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
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
            String source, String status, BigDecimal knownValue, boolean complete) {
        public NutrientValueResponse(String code, String name, String group, String unit, BigDecimal value, String source, String status) {
            this(code, name, group, unit, value, source, status, value, value != null);
        }
    }

    public record NutrientInput(@NotBlank @Size(max = 80) String code, @NotNull @PositiveOrZero BigDecimal value) { }

    public record NutrientUpdateRequest(@NotEmpty @Size(max = 40) List<@Valid NutrientInput> nutrients) { }

    public record FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
            BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            FoodPreparation preparation, String preparationSource, String preparationGroup, String servingName, BigDecimal servingWeightGrams,
            String imageUrl, String source, String sourceId, OffsetDateTime lastSyncedAt, Set<String> tags,
            Long createdById, OffsetDateTime createdAt, ModerationStatus moderationStatus, List<NutrientValueResponse> nutrients,
            BigDecimal cookedYieldFactor, CookedYieldSource cookedYieldSource, String cookedYieldAssumption, boolean archived) {
        public FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit, BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams, FoodPreparation preparation, String preparationSource, String preparationGroup, String servingName, BigDecimal servingWeightGrams, String imageUrl, String source, String sourceId, OffsetDateTime lastSyncedAt, Set<String> tags, Long createdById, OffsetDateTime createdAt, ModerationStatus moderationStatus, List<NutrientValueResponse> nutrients, BigDecimal cookedYieldFactor, CookedYieldSource cookedYieldSource, String cookedYieldAssumption) { this(id, name, brand, barcode, category, baseUnit, baseQuantity, calories, proteinGrams, carbsGrams, fatGrams, preparation, preparationSource, preparationGroup, servingName, servingWeightGrams, imageUrl, source, sourceId, lastSyncedAt, tags, createdById, createdAt, moderationStatus, nutrients, cookedYieldFactor, cookedYieldSource, cookedYieldAssumption, false); }

        public FoodResponse(Long id, String name, String brand, String barcode, FoodCategory category, FoodUnit baseUnit,
                BigDecimal baseQuantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
                FoodPreparation preparation, String preparationSource, String preparationGroup, String servingName, BigDecimal servingWeightGrams,
                String imageUrl, String source, String sourceId, OffsetDateTime lastSyncedAt, Set<String> tags,
                Long createdById, OffsetDateTime createdAt, ModerationStatus moderationStatus) {
            this(id, name, brand, barcode, category, baseUnit, baseQuantity, calories, proteinGrams, carbsGrams, fatGrams,
                    preparation, preparationSource, preparationGroup, servingName, servingWeightGrams, imageUrl, source, sourceId,
                    lastSyncedAt, tags, createdById, createdAt, moderationStatus, List.of(), null, null, null);
        }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean energyComplete() { return calories != null; }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean nutritionComplete() { return calories != null && proteinGrams != null && carbsGrams != null && fatGrams != null; }
        @com.fasterxml.jackson.annotation.JsonProperty public String nutritionWarning() {
            if (!nutritionComplete()) return "Información nutricional incompleta.";
            return (category == FoodCategory.PROTEIN || category == FoodCategory.MEAT) && calories == 0 && proteinGrams.signum() == 0 && carbsGrams.signum() == 0 && fatGrams.signum() == 0 ? "Composición pendiente de verificar. Compará con otra variante." : null;
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
        @com.fasterxml.jackson.annotation.JsonProperty public boolean energyComplete() { return calories != null; }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean nutritionComplete() { return calories != null && proteinGrams != null && carbsGrams != null && fatGrams != null; }
        @com.fasterxml.jackson.annotation.JsonProperty public String nutritionWarning() {
            if (!nutritionComplete()) return "Información nutricional incompleta.";
            return (category == FoodCategory.PROTEIN || category == FoodCategory.MEAT) && calories == 0 && proteinGrams.signum() == 0 && carbsGrams.signum() == 0 && fatGrams.signum() == 0 ? "Composición pendiente de verificar. Compará con otra variante." : null;
        }
    }

    public record CreateFoodRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 120) String brand,
            @Pattern(regexp = "^$|\\d{6,32}", message = "Debe contener entre 6 y 32 digitos.") String barcode,
            @NotNull FoodCategory category,
            @NotNull FoodUnit baseUnit,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal baseQuantity,
            @PositiveOrZero Integer calories,
            @PositiveOrZero @Digits(integer = 36, fraction = 2) BigDecimal proteinGrams,
            @PositiveOrZero @Digits(integer = 36, fraction = 2) BigDecimal carbsGrams,
            @PositiveOrZero @Digits(integer = 36, fraction = 2) BigDecimal fatGrams,
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
        @com.fasterxml.jackson.annotation.JsonProperty public boolean energyComplete() { return calories != null; }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean nutritionComplete() { return calories != null && proteinGrams != null && carbsGrams != null && fatGrams != null; }
    }

    public record AddFoodLogRequest(@NotNull Long foodId, @NotNull MealType mealType, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public AddFoodLogRequest(Long foodId, MealType mealType, BigDecimal quantity, FoodUnit unit, LocalDate logDate) { this(foodId, mealType, quantity, unit, logDate, Set.of()); }

    }

    public record AddMealLogRequest(@NotNull MealItemType itemType, @NotNull Long itemId, @NotNull MealType mealType,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity, @NotNull FoodUnit unit, LocalDate logDate, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public AddMealLogRequest(MealItemType itemType, Long itemId, MealType mealType, BigDecimal quantity, FoodUnit unit, LocalDate logDate) { this(itemType, itemId, mealType, quantity, unit, logDate, Set.of()); }

    }

    public record BatchAddMealLogsRequest(
            @NotEmpty @Size(max = 50) List<@NotNull @Valid BatchAddMealLogRequest> logs, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public BatchAddMealLogsRequest(List<BatchAddMealLogRequest> logs) { this(logs, Set.of()); }

    }

    public record BatchAddMealLogRequest(@NotNull MealItemType itemType, @Positive Long itemId, @NotNull MealType mealType,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity, @NotNull FoodUnit unit, LocalDate logDate,
            @Size(max = 120) String displayName, @PositiveOrZero Integer aiEstimateConfidence, @Size(max = 20000) String aiEstimateDetails,
            @PositiveOrZero Integer calories, @PositiveOrZero BigDecimal proteinGrams, @PositiveOrZero BigDecimal carbsGrams,
            @PositiveOrZero BigDecimal fatGrams, List<NutrientValueResponse> nutrients, @Positive Long sourceLogId) {
        @AssertTrue(message = "El registro debe tener una referencia válida.")
        public boolean hasValidItemReference() {
            return itemType == MealItemType.AI_ESTIMATE ? itemId == null : itemId != null;
        }
    }

    public record AddRecipeMealLogRequest(@NotNull Long recipeId, @NotNull MealType mealType,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity, LocalDate logDate,
            @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public AddRecipeMealLogRequest(Long recipeId, MealType mealType, BigDecimal quantity, LocalDate logDate, List<RecipeIngredientRequest> ingredients) { this(recipeId, mealType, quantity, logDate, ingredients, Set.of()); }

    }

    public record UpdateFoodLogRequest(@NotNull MealType mealType, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity,
            @NotNull FoodUnit unit, LocalDate logDate, Long itemId, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public UpdateFoodLogRequest(MealType mealType, BigDecimal quantity, FoodUnit unit, LocalDate logDate, Long itemId) { this(mealType, quantity, unit, logDate, itemId, Set.of()); }

    }

    public record UpdateRecipeFoodLogRequest(@NotNull MealType mealType, @Positive @Digits(integer = 36, fraction = 2) BigDecimal quantity,
            LocalDate logDate, @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> recipeIngredients, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public UpdateRecipeFoodLogRequest(MealType mealType, BigDecimal quantity, LocalDate logDate, List<RecipeIngredientRequest> recipeIngredients) { this(mealType, quantity, logDate, recipeIngredients, Set.of()); }

    }

    public record UpdateRecipeLogIngredientsRequest(
            @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public UpdateRecipeLogIngredientsRequest(List<RecipeIngredientRequest> ingredients) { this(ingredients, Set.of()); }

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
        @com.fasterxml.jackson.annotation.JsonProperty public boolean energyComplete() { return calories != null; }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean nutritionComplete() { return calories != null && proteinGrams != null && carbsGrams != null && fatGrams != null; }
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
            UUID captureId,
            AiCaptureTarget targetType,
            @NotBlank String name,
            String description,
            int confidence,
            List<String> assumptions,
            List<AiEstimateItem> items,
            AiEstimateUsageResponse usage) {
        public AiEstimateResponse(String name, String description, int confidence, List<String> assumptions,
                List<AiEstimateItem> items, AiEstimateUsageResponse usage) {
            this(null, AiCaptureTarget.RECIPE, name, description, confidence, assumptions, items, usage);
        }
    }

    public record AiTranscriptionResponse(@NotBlank String transcript) {
    }

    public record AiEstimateUsageResponse(boolean available, int used, int dailyLimit, OffsetDateTime blockedUntil, String status) {
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
            @NotEmpty @Size(max = 12) List<@NotNull @Valid ConfirmAiEstimateItem> items, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public ConfirmAiEstimateRequest(MealType mealType, LocalDate logDate, List<ConfirmAiEstimateItem> items) { this(mealType, logDate, items, Set.of()); }

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

    public record MacroProgress(String key, String label, BigDecimal consumed, BigDecimal goal, BigDecimal remaining) {
    }

    public record MealSummary(MealType mealType, String label, Integer calories, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams, List<FoodLogResponse> items) {
    }

    public record RecentMealResponse(LocalDate sourceDate, MealType mealType, String label, Integer calories,
            BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams, List<FoodLogResponse> items) {
    }

    public record DashboardResponse(LocalDate date, Integer calorieGoal, Integer caloriesConsumed, Integer caloriesRemaining,
            List<MacroProgress> macros, List<MealSummary> meals, NutritionPlanResponse plan, List<NutrientValueResponse> nutrients) {
        @com.fasterxml.jackson.annotation.JsonProperty public boolean energyComplete() { return meals.stream().flatMap(meal -> meal.items().stream()).allMatch(FoodLogResponse::energyComplete); }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean nutritionComplete() { return meals.stream().flatMap(meal -> meal.items().stream()).allMatch(FoodLogResponse::nutritionComplete); }
    }

    public record DaySummary(LocalDate date, Integer caloriesConsumed, Integer calorieGoal, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams, boolean goalReached, Long planId, String planName,
            long recordCount, boolean energyComplete, boolean nutritionComplete, String recordState,
            BigDecimal knownProteinGrams, BigDecimal knownCarbsGrams, BigDecimal knownFatGrams) {
        public DaySummary(LocalDate date, Integer caloriesConsumed, Integer calorieGoal, BigDecimal proteinGrams,
                BigDecimal carbsGrams, BigDecimal fatGrams, boolean goalReached, Long planId, String planName) {
            this(date, caloriesConsumed, calorieGoal, proteinGrams, carbsGrams, fatGrams, goalReached, planId, planName,
                    0, true, true, "NONE", proteinGrams, carbsGrams, fatGrams);
        }
    }

    public record HistoryResponse(int year, int month, List<DaySummary> days, Integer averageCalories,
            long completedGoalDays, long averageDayCount) {
        public HistoryResponse(int year, int month, List<DaySummary> days, Integer averageCalories, long completedGoalDays) {
            this(year, month, days, averageCalories, completedGoalDays, 0);
        }
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
            String imageUrl,
            Integer calories,
            @PositiveOrZero BigDecimal proteinGrams,
            @PositiveOrZero BigDecimal carbsGrams,
            @PositiveOrZero BigDecimal fatGrams,
            Integer aiEstimateConfidence,
            String aiEstimateDetails,
            List<NutrientValueResponse> nutrients) {
    }

    public record CreateDayPresetRequest(
            @NotBlank @Size(min = 1, max = 120) String name,
            @Size(max = 240) String description,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid DayPresetItemRequest> items, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public CreateDayPresetRequest(String name, String description, List<DayPresetItemRequest> items) { this(name, description, items, Set.of()); }

    }

    public record UpdateDayPresetRequest(
            @NotBlank @Size(min = 1, max = 120) String name,
            @Size(max = 240) String description,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid DayPresetItemRequest> items, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public UpdateDayPresetRequest(String name, String description, List<DayPresetItemRequest> items) { this(name, description, items, Set.of()); }

    }

    public record DayPresetResponse(Long id, String name, String description, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<DayPresetItemRequest> items, int itemCount, Map<String, Integer> mealCounts) {
    }

    public record ApplyDayPresetRequest(@NotNull LocalDate logDate, boolean replace, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public ApplyDayPresetRequest(LocalDate logDate, boolean replace) { this(logDate, replace, Set.of()); }

    }

    public record RecipeIngredientRequest(@Positive Long foodId, @Positive Long recipeId,
            @NotNull @Positive @DecimalMax("10000000") @Digits(integer = 8, fraction = 2) BigDecimal quantity, @NotNull FoodUnit unit) {
        public RecipeIngredientRequest(Long foodId, BigDecimal quantity, FoodUnit unit) {
            this(foodId, null, quantity, unit);
        }

        @AssertTrue(message = "Cada ingrediente debe referenciar un alimento o una receta, pero no ambos.")
        public boolean hasSingleReference() {
            return (foodId != null) ^ (recipeId != null);
        }
    }

    public record ConfirmAiRegistrationRequest(
            @NotNull UUID captureId,
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 500) String description,
            MealType mealType,
            LocalDate logDate,
            @NotNull @PositiveOrZero Integer confidence,
            boolean addToDiary,
            @NotEmpty @Size(max = 12) List<@Valid AiEstimateItem> items,
            @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds,
            @Size(max = 12) List<@Valid AiRegistrationResolution> resolutions) {
        public ConfirmAiRegistrationRequest(UUID captureId, String name, String description, MealType mealType,
                LocalDate logDate, Integer confidence, boolean addToDiary, List<AiEstimateItem> items,
                Set<Long> acknowledgedArchivedFoodIds) {
            this(captureId, name, description, mealType, logDate, confidence, addToDiary, items,
                    acknowledgedArchivedFoodIds, List.of());
        }

        public ConfirmAiRegistrationRequest(UUID captureId, String name, String description, MealType mealType,
                LocalDate logDate, Integer confidence, boolean addToDiary, List<AiEstimateItem> items) {
            this(captureId, name, description, mealType, logDate, confidence, addToDiary, items, Set.of(), List.of());
        }
    }

    public record AiRegistrationMatchesRequest(@NotNull UUID captureId,
            @NotEmpty @Size(max = 12) List<@Valid AiEstimateItem> items) {
    }

    public enum AiCatalogChoice {
        USE_CATALOG,
        KEEP_ESTIMATE
    }

    public record AiRegistrationResolution(@NotNull @PositiveOrZero Integer itemIndex,
            @NotNull AiCatalogChoice choice, @Positive Long foodId) {
        @AssertTrue(message = "La resolución del alimento no es válida.")
        public boolean hasFoodIdOnlyWhenUsingCatalog() {
            return choice == AiCatalogChoice.USE_CATALOG ? foodId != null : foodId == null;
        }
    }

    public record AiCatalogFoodMatchResponse(Long foodId, String name, String brand, double similarity,
            BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams, boolean macrosDiffer) {
    }

    public record AiRegistrationItemMatch(int itemIndex, AiCatalogFoodMatchResponse match) {
    }

    public record AiRegistrationMatchesResponse(List<AiRegistrationItemMatch> items) {
    }

    public record AiRegistrationResponse(AiCaptureTarget targetType, FoodResponse food, RecipeResponse recipe,
            FoodLogResponse log) {
    }

    public record CreateRecipeRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 500) String description,
            BigDecimal totalWeightGrams,
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal cookedTotalWeightGrams,
            boolean clearCookedTotalWeight,
            @NotEmpty @Size(max = 50) List<@NotNull @Valid RecipeIngredientRequest> ingredients, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public CreateRecipeRequest(String name, String description, BigDecimal totalWeightGrams, BigDecimal cookedTotalWeightGrams, boolean clearCookedTotalWeight, List<RecipeIngredientRequest> ingredients) { this(name, description, totalWeightGrams, cookedTotalWeightGrams, clearCookedTotalWeight, ingredients, Set.of()); }

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
            @Positive @Digits(integer = 36, fraction = 2) BigDecimal cookedTotalWeightGrams, @Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {
        public CreateRecipeFromMealRequest(String name, String description, MealType mealType, LocalDate logDate, BigDecimal cookedTotalWeightGrams) { this(name, description, mealType, logDate, cookedTotalWeightGrams, Set.of()); }

    }

    public record RecipeIngredientResponse(FoodResponse food, RecipeReferenceResponse recipe, BigDecimal quantity, FoodUnit unit) {
    }

    public record RecipeReferenceResponse(Long id, String name, String description, BigDecimal rawTotalWeightGrams,
            BigDecimal cookedTotalWeightGrams, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams,
            BigDecimal fatGrams) {
    }

    public record RecipeResponse(Long id, String name, String description, BigDecimal totalWeightGrams,
            BigDecimal rawTotalWeightGrams, BigDecimal cookedTotalWeightGrams, Integer calories,
            BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            List<RecipeIngredientResponse> ingredients, List<NutrientValueResponse> nutrients, Integer ingredientCount) {
        public RecipeResponse(Long id, String name, String description, BigDecimal totalWeightGrams, BigDecimal rawTotalWeightGrams, BigDecimal cookedTotalWeightGrams, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams, List<RecipeIngredientResponse> ingredients, List<NutrientValueResponse> nutrients) {
            this(id, name, description, totalWeightGrams, rawTotalWeightGrams, cookedTotalWeightGrams, calories, proteinGrams, carbsGrams, fatGrams, ingredients, nutrients, ingredients == null || ingredients.isEmpty() ? null : ingredients.size());
        }
        public RecipeResponse(Long id, String name, String description, BigDecimal totalWeightGrams, Integer calories,
                BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
                List<RecipeIngredientResponse> ingredients) {
            this(id, name, description, totalWeightGrams, totalWeightGrams, null, calories, proteinGrams, carbsGrams, fatGrams,
                    ingredients, List.of());
        }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean energyComplete() { return calories != null; }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean nutritionComplete() { return calories != null && proteinGrams != null && carbsGrams != null && fatGrams != null; }
    }

    public record ArchivedFoodAcknowledgementRequest(@Size(max = 200) Set<@Positive Long> acknowledgedArchivedFoodIds) {}

    public record RecipeFromMealResponse(RecipeResponse recipe, List<String> skippedItems) {
    }

    public record RecipeOwnerResponse(Long id, String fullName, long recipeCount) {
    }
}
