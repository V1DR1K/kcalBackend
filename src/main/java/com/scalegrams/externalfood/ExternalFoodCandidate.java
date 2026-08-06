package com.scalegrams.externalfood;

import java.math.BigDecimal;
import java.util.Set;

import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;

public record ExternalFoodCandidate(
        String name,
        String brand,
        String barcode,
        FoodCategory category,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        FoodPreparation preparation,
        String preparationSource,
        String servingName,
        BigDecimal servingWeightGrams,
        String imageUrl,
        Set<String> tags,
        String source,
        String sourceId) {
}
