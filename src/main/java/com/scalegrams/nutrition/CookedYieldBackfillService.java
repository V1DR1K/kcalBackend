package com.scalegrams.nutrition;

import java.math.BigDecimal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.scalegrams.catalog.CookedYieldSource;
import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.user.AppUser;
import com.scalegrams.user.Role;

@Service
public class CookedYieldBackfillService {
    public record CookedYieldBackfillReport(boolean dryRun, int examined, int updated, int identity,
            int gemini, int skipped, long remaining) {
    }

    private final FoodRepository foods;
    private final GeminiNutritionClient gemini;

    public CookedYieldBackfillService(FoodRepository foods, GeminiNutritionClient gemini) {
        this.foods = foods;
        this.gemini = gemini;
    }

    public CookedYieldBackfillReport backfill(AppUser user, boolean dryRun, int requestedLimit) {
        if (user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Solo un administrador puede completar rendimientos de cocción.");
        }
        int limit = Math.min(Math.max(requestedLimit, 1), 25);
        int examined = 0;
        int updated = 0;
        int identity = 0;
        int geminiUpdated = 0;
        int skipped = 0;
        for (Food food : foods.findByDeletedAtIsNullAndCookedYieldFactorIsNull(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id")))) {
            examined++;
            // A manual source is never replaced, even if a malformed legacy row lacks its factor.
            if (food.getCookedYieldSource() == CookedYieldSource.MANUAL) {
                skipped++;
                continue;
            }
            FoodPreparation preparation = food.getPreparation() == null ? FoodPreparation.UNSPECIFIED : food.getPreparation();
            if (preparation == FoodPreparation.COOKED || preparation == FoodPreparation.AS_SOLD) {
                identity++;
                if (!dryRun) {
                    food.setCookedYieldFactor(BigDecimal.ONE.setScale(4));
                    food.setCookedYieldSource(CookedYieldSource.IDENTITY);
                    food.setCookedYieldAssumption("Sin cambio de peso: el alimento ya está cocido o listo para consumir.");
                    foods.save(food);
                    updated++;
                }
                continue;
            }
            geminiUpdated++;
            if (!dryRun) {
                GeminiNutritionClient.CookedYieldEstimate estimate = gemini.estimateCookedYield(food.getName(),
                        food.getCategory(), preparation);
                food.setCookedYieldFactor(estimate.factor());
                food.setCookedYieldSource(CookedYieldSource.GEMINI);
                food.setCookedYieldAssumption(estimate.assumption());
                foods.save(food);
                updated++;
            }
        }
        return new CookedYieldBackfillReport(dryRun, examined, updated, identity, geminiUpdated, skipped,
                foods.countByDeletedAtIsNullAndCookedYieldFactorIsNull());
    }
}
