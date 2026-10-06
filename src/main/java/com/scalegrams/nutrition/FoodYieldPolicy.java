package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.scalegrams.catalog.CookedYieldSource;
import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.nutrition.NutritionDtos.CreateFoodRequest;

@Component
public class FoodYieldPolicy {
    public void applyRequestedCookedYield(Food food, CreateFoodRequest request) {
        if (request.cookedYieldFactor() != null) {
            food.setCookedYieldFactor(request.cookedYieldFactor().setScale(4, RoundingMode.HALF_UP));
            food.setCookedYieldSource(CookedYieldSource.MANUAL);
            String assumption = clean(request.cookedYieldAssumption());
            food.setCookedYieldAssumption(assumption == null ? "Factor ingresado manualmente." : assumption);
            return;
        }
        initializeIdentityCookedYield(food);
    }

    public void initializeIdentityCookedYield(Food food) {
        if (food.getCookedYieldFactor() != null || food.getPreparation() == null) return;
        if (food.getPreparation() == FoodPreparation.COOKED || food.getPreparation() == FoodPreparation.AS_SOLD) {
            food.setCookedYieldFactor(BigDecimal.ONE.setScale(4));
            food.setCookedYieldSource(CookedYieldSource.IDENTITY);
            food.setCookedYieldAssumption("Sin cambio de peso: el alimento ya está cocido o listo para consumir.");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
