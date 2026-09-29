package com.scalegrams.nutrition;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.common.SearchTextNormalizer;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateFoodProposal;

/** A similar name is not nutritional equivalence. Matching must be deterministic. */
@Service
public class AiFoodMatcher {
    private final FoodRepository foods;

    public AiFoodMatcher(FoodRepository foods) {
        this.foods = foods;
    }

    public List<AiEstimateItem> enrich(List<AiEstimateItem> items) {
        return items.stream().map(item -> {
            Optional<Food> match = match(item.name(), item.category(), item.preparation());
            if (match.isPresent()) {
                Food food = match.get();
                BigDecimal ratio = item.estimatedGrams().divide(food.getBaseQuantity(), 8, RoundingMode.HALF_UP);
                Map<String, BigDecimal> nutrients = food.getNutrients().stream().collect(Collectors.toMap(
                        nutrient -> nutrient.getDefinition().getCode(),
                        nutrient -> zero(nutrient.getValue()).multiply(ratio).setScale(1, RoundingMode.HALF_UP),
                        (first, ignored) -> first));
                return new AiEstimateItem(item.name(), item.estimatedGrams(), item.category(), item.preparation(),
                        zero(food.getProteinGrams()).multiply(ratio).setScale(1, RoundingMode.HALF_UP),
                        zero(food.getCarbsGrams()).multiply(ratio).setScale(1, RoundingMode.HALF_UP),
                        zero(food.getFatGrams()).multiply(ratio).setScale(1, RoundingMode.HALF_UP), nutrients,
                        food.getId(), "EXACT", 99);
            }
            return new AiEstimateItem(item.name(), item.estimatedGrams(), item.category(), item.preparation(),
                    item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients(),
                    match.map(Food::getId).orElse(null), match.isPresent() ? "EXACT" : "NONE",
                    match.isPresent() ? 99 : 0);
        }).toList();
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    public Optional<Food> resolve(AiEstimateFoodProposal proposal) {
        return match(proposal.name(), proposal.category(), proposal.preparation());
    }

    public Optional<Food> resolve(AiEstimateItem item) {
        return match(item.name(), item.category(), item.preparation());
    }

    private Optional<Food> match(String name, FoodCategory category, FoodPreparation preparation) {
        String query = SearchTextNormalizer.normalize(name);
        if (query.isBlank()) return Optional.empty();
        FoodPreparation requested = preparation == null ? FoodPreparation.UNSPECIFIED : preparation;
        List<Food> candidates = foods.findActiveBySearchName(query, ModerationStatus.APPROVED).stream()
                .filter(food -> food.getDeletedAt() == null && food.getModerationStatus() == ModerationStatus.APPROVED)
                .filter(food -> category == null || food.getCategory() == category)
                .filter(food -> (food.getPreparation() == null ? FoodPreparation.UNSPECIFIED : food.getPreparation()) == requested)
                .filter(food -> food.getBaseUnit() == FoodUnit.GRAM && food.getBaseQuantity() != null && food.getBaseQuantity().signum() > 0)
                // Proposals carry no brand/barcode, so cannot identify packaged products.
                .filter(food -> food.getBrand() == null || food.getBrand().isBlank())
                .toList();
        // Ambiguity stays an estimate; do not ask another AI request to guess an identity.
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }
}
