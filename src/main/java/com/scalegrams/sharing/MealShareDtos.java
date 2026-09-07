package com.scalegrams.sharing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.nutrition.MealItemType;
import com.scalegrams.nutrition.MealType;

import jakarta.validation.constraints.NotNull;

public final class MealShareDtos {
    private MealShareDtos() { }

    public record CreateMealShareRequest(@NotNull LocalDate sourceDate, @NotNull MealType mealType) { }

    public record AcceptMealShareRequest(@NotNull LocalDate targetDate, @NotNull MealType mealType) { }

    public record MealShareItemResponse(MealItemType itemType, String name, BigDecimal quantity, FoodUnit unit,
            Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            boolean estimated) { }

    public record MealSharePreviewResponse(LocalDate sourceDate, MealType sourceMealType, String sourceMealLabel,
            Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            List<MealShareItemResponse> items, OffsetDateTime expiresAt, boolean alreadyAccepted) { }

    public record MealShareCreatedResponse(String token, OffsetDateTime expiresAt, MealSharePreviewResponse preview) { }

    public record MealShareAcceptanceResponse(LocalDate targetDate, MealType targetMealType, int itemCount) { }
}
