package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.scalegrams.user.AppUser;

/** Keeps an AI proposal intact and only resolves catalog matches after review. */
@Service
public class AiFoodMatcher {
    private static final Set<String> GENERIC_FOOD_WORDS = Set.of(
            "a", "al", "as", "con", "cocida", "cocido", "cocidos", "cocidas", "cruda", "crudo",
            "de", "del", "en", "frita", "frito", "grillado", "grillada", "horneado", "horneada",
            "la", "las", "lo", "los", "and", "cooked", "food", "fresh", "fried", "grilled",
            "mashed", "of", "pure", "puree", "roasted", "raw", "the", "with", "y");
    private static final Map<String, String> FOOD_SYNONYMS = Map.ofEntries(
            Map.entry("papa", "potato"), Map.entry("patata", "potato"), Map.entry("potato", "potato"),
            Map.entry("zanahoria", "carrot"), Map.entry("carrot", "carrot"),
            Map.entry("calabaza", "pumpkin"), Map.entry("zapallo", "pumpkin"), Map.entry("pumpkin", "pumpkin"),
            Map.entry("squash", "pumpkin"), Map.entry("palta", "avocado"), Map.entry("aguacate", "avocado"),
            Map.entry("avocado", "avocado"), Map.entry("frutilla", "strawberry"), Map.entry("fresa", "strawberry"),
            Map.entry("strawberry", "strawberry"), Map.entry("anana", "pineapple"), Map.entry("pina", "pineapple"),
            Map.entry("pineapple", "pineapple"), Map.entry("choclo", "corn"), Map.entry("maiz", "corn"),
            Map.entry("corn", "corn"), Map.entry("batata", "sweetpotato"), Map.entry("camote", "sweetpotato"),
            Map.entry("bread", "bread"), Map.entry("pan", "bread"));

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
        return exactMatch(null, proposal.name(), proposal.category(), proposal.preparation());
    }

    public Optional<Food> resolve(AiEstimateItem item) {
        return exactMatch(null, item.name(), item.category(), item.preparation());
    }

    public Optional<CatalogMatch> preview(AiEstimateItem item) {
        return preview(null, item);
    }

    public Optional<CatalogMatch> preview(AppUser user, AiEstimateItem item) {
        List<Candidate> candidates = new ArrayList<>();
        if (semanticSearch != null) {
            List<FoodSemanticSearchService.FoodMatch> semanticMatches = user == null
                    ? semanticSearch.search(item.name(), item.category(), normalizedPreparation(item.preparation()), 5)
                    : semanticSearch.search(item.name(), null, null, 20, user.getId());
            semanticMatches.stream()
                    .filter(match -> canUseForGramEstimate(match.food()))
                    .filter(match -> isOwnedBy(match.food(), user)
                            || item.category() == null || match.food().getCategory() == item.category())
                    .filter(match -> isOwnedBy(match.food(), user)
                            || normalizedPreparation(match.food().getPreparation())
                                    == normalizedPreparation(item.preparation()))
                    .filter(match -> sharesFoodIdentity(item.name(), match.food()))
                    .map(match -> new Candidate(match.food(), match.similarity(), false,
                            isOwnedBy(match.food(), user)))
                    .forEach(candidates::add);
        }
        exactMatch(user, item.name(), item.category(), item.preparation())
                .filter(AiFoodMatcher::canUseForGramEstimate)
                .ifPresent(food -> {
                    candidates.removeIf(candidate -> candidate.food().getId().equals(food.getId()));
                    candidates.add(new Candidate(food, 1.0, true, isOwnedBy(food, user)));
                });
        return candidates.stream()
                .max(Comparator.comparing(Candidate::personalFood)
                        .thenComparing(Candidate::exactName)
                        .thenComparingDouble(Candidate::similarity))
                .map(candidate -> toCatalogMatch(candidate.food(), candidate.similarity(), item));
    }

    private Optional<Food> exactMatch(AppUser user, String name, FoodCategory category,
            FoodPreparation preparation) {
        String query = SearchTextNormalizer.normalize(name);
        if (query.isBlank()) return Optional.empty();
        FoodPreparation requested = normalizedPreparation(preparation);
        List<Food> candidates = foods.findActiveBySearchNameOrBrand(query, ModerationStatus.APPROVED).stream()
                .filter(food -> food.getDeletedAt() == null && food.getModerationStatus() == ModerationStatus.APPROVED)
                .filter(AiFoodMatcher::canUseForGramEstimate)
                .toList();

        List<Food> personalMatches = candidates.stream()
                .filter(food -> isOwnedBy(food, user))
                .toList();
        if (personalMatches.size() == 1) return Optional.of(personalMatches.getFirst());
        Optional<Food> uniquePersonalMetadataMatch = uniqueMetadataMatch(personalMatches, category, requested);
        if (uniquePersonalMetadataMatch.isPresent()) return uniquePersonalMetadataMatch;

        List<Food> sharedMatches = candidates.stream()
                .filter(food -> food.getCreatedBy() == null)
                // A proposal without an explicit brand cannot identify a packaged product.
                .filter(food -> food.getBrand() == null || food.getBrand().isBlank())
                .toList();
        return uniqueMetadataMatch(sharedMatches, category, requested);
    }

    private static Optional<Food> uniqueMetadataMatch(List<Food> candidates, FoodCategory category,
            FoodPreparation preparation) {
        List<Food> compatible = candidates.stream()
                .filter(food -> category == null || food.getCategory() == category)
                .filter(food -> normalizedPreparation(food.getPreparation()) == preparation)
                .toList();
        return compatible.size() == 1 ? Optional.of(compatible.getFirst()) : Optional.empty();
    }

    private static boolean isOwnedBy(Food food, AppUser user) {
        return user != null && user.getId() != null && food.getCreatedBy() != null
                && user.getId().equals(food.getCreatedBy().getId());
    }

    private static boolean sharesFoodIdentity(String proposedName, Food food) {
        Set<String> proposedTokens = identityTokens(proposedName);
        if (proposedTokens.isEmpty()) return false;
        Set<String> catalogTokens = identityTokens(food.getName() + " " + (food.getBrand() == null ? "" : food.getBrand()));
        proposedTokens.retainAll(catalogTokens);
        return !proposedTokens.isEmpty();
    }

    private static Set<String> identityTokens(String value) {
        Set<String> tokens = new HashSet<>();
        for (String token : SearchTextNormalizer.normalize(value).replaceAll("[^a-z0-9]+", " ").split("\\s+")) {
            if (token.length() < 2 || GENERIC_FOOD_WORDS.contains(token)) continue;
            tokens.add(FOOD_SYNONYMS.getOrDefault(token, token));
        }
        return tokens;
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

    private record Candidate(Food food, double similarity, boolean exactName, boolean personalFood) {
    }
}
