package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.common.SearchTextNormalizer;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateFoodProposal;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;

/** Keeps an AI proposal intact and only resolves catalog matches after review. */
@Service
public class AiFoodMatcher {
    private final FoodRepository foods;
    private final FoodSemanticSearchService semanticSearch;

    @Autowired
    public AiFoodMatcher(FoodRepository foods, FoodSemanticSearchService semanticSearch) {
        this.foods = foods;
        this.semanticSearch = semanticSearch;
    }

    /** Legacy constructor used by deterministic matching tests. */
    public AiFoodMatcher(FoodRepository foods) {
        this.foods = foods;
        this.semanticSearch = null;
    }

    public List<AiEstimateItem> enrich(List<AiEstimateItem> items) {
        return items.stream().map(item -> new AiEstimateItem(item.name(), item.estimatedGrams(),
                item.category(), item.preparation(), item.proteinGrams(), item.carbsGrams(), item.fatGrams(),
                item.nutrients())).toList();
    }

    public Optional<Food> resolve(AiEstimateFoodProposal proposal) {
        return exactMatch(proposal.name(), proposal.category(), proposal.preparation());
    }

    public Optional<Food> resolve(AiEstimateItem item) {
        return exactMatch(item.name(), item.category(), item.preparation());
    }

    public Optional<CatalogMatch> preview(AiEstimateItem item) {
        List<Candidate> candidates = new ArrayList<>();
        if (semanticSearch != null) {
            semanticSearch.search(item.name(), item.category(), normalizedPreparation(item.preparation()), 5)
                    .stream()
                    .filter(match -> canUseForGramEstimate(match.food()))
                    .filter(match -> item.category() == null || match.food().getCategory() == item.category())
                    .filter(match -> normalizedPreparation(match.food().getPreparation())
                            == normalizedPreparation(item.preparation()))
                    .map(match -> new Candidate(match.food(), match.similarity()))
                    .forEach(candidates::add);
        }
        exactMatch(item.name(), item.category(), item.preparation())
                .filter(AiFoodMatcher::canUseForGramEstimate)
                .ifPresent(food -> {
                    if (candidates.stream().noneMatch(candidate -> candidate.food().getId().equals(food.getId()))) {
                        candidates.add(new Candidate(food, 1.0));
                    }
                });
        return candidates.stream()
                .map(candidate -> toCatalogMatch(candidate.food(), candidate.similarity(), item))
                .min(Comparator.comparing(CatalogMatch::macrosDiffer)
                        .thenComparing(Comparator.comparingDouble(CatalogMatch::similarity).reversed()));
    }

    private Optional<Food> exactMatch(String name, FoodCategory category, FoodPreparation preparation) {
        String query = SearchTextNormalizer.normalize(name);
        if (query.isBlank()) return Optional.empty();
        FoodPreparation requested = normalizedPreparation(preparation);
        List<Food> candidates = foods.findActiveBySearchName(query, ModerationStatus.APPROVED).stream()
                .filter(food -> food.getDeletedAt() == null && food.getModerationStatus() == ModerationStatus.APPROVED)
                .filter(food -> category == null || food.getCategory() == category)
                .filter(food -> normalizedPreparation(food.getPreparation()) == requested)
                .filter(AiFoodMatcher::canUseForGramEstimate)
                // A proposal without an explicit brand cannot identify a packaged product.
                .filter(food -> food.getBrand() == null || food.getBrand().isBlank())
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static CatalogMatch toCatalogMatch(Food food, double similarity, AiEstimateItem item) {
        BigDecimal ratio = item.estimatedGrams().divide(food.getBaseQuantity(), 8, RoundingMode.HALF_UP);
        BigDecimal protein = scaled(food.getProteinGrams(), ratio);
        BigDecimal carbs = scaled(food.getCarbsGrams(), ratio);
        BigDecimal fat = scaled(food.getFatGrams(), ratio);
        boolean macrosDiffer = differs(protein, item.proteinGrams())
                || differs(carbs, item.carbsGrams())
                || differs(fat, item.fatGrams());
        return new CatalogMatch(food, similarity, protein, carbs, fat, macrosDiffer);
    }

    private static BigDecimal scaled(BigDecimal value, BigDecimal ratio) {
        return value == null ? null : value.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    }

    private static boolean differs(BigDecimal catalogValue, BigDecimal estimateValue) {
        if (catalogValue == null || estimateValue == null) return true;
        return catalogValue.setScale(1, RoundingMode.HALF_UP)
                .compareTo(estimateValue.setScale(1, RoundingMode.HALF_UP)) != 0;
    }

    private static boolean canUseForGramEstimate(Food food) {
        return food.getBaseUnit() == FoodUnit.GRAM
                && food.getBaseQuantity() != null && food.getBaseQuantity().signum() > 0;
    }

    private static FoodPreparation normalizedPreparation(FoodPreparation preparation) {
        return preparation == null ? FoodPreparation.UNSPECIFIED : preparation;
    }

    public record CatalogMatch(Food food, double similarity, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams, boolean macrosDiffer) {
    }

    private record Candidate(Food food, double similarity) {
    }
}
