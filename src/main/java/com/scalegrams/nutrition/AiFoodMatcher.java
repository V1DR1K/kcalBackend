package com.scalegrams.nutrition;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
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
            return new AiEstimateItem(item.name(), item.estimatedGrams(), item.category(), item.preparation(),
                    item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients(),
                    match.map(Food::getId).orElse(null), match.isPresent() ? "EXACT" : "NONE",
                    match.isPresent() ? 99 : 0);
        }).toList();
    }

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
                // Proposals carry no brand/barcode, so cannot identify packaged products.
                .filter(food -> food.getBrand() == null || food.getBrand().isBlank())
                .toList();
        // Ambiguity stays an estimate; do not ask another AI request to guess an identity.
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }
}
