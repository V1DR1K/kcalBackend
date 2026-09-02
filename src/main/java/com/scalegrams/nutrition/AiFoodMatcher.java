package com.scalegrams.nutrition;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.common.SearchTextNormalizer;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateFoodProposal;

@Service
public class AiFoodMatcher {
    private static final Logger log = LoggerFactory.getLogger(AiFoodMatcher.class);
    private static final double FUZZY_THRESHOLD = 0.78;

    private final FoodRepository foods;
    private final GeminiNutritionClient gemini;

    public AiFoodMatcher(FoodRepository foods, GeminiNutritionClient gemini) {
        this.foods = foods;
        this.gemini = gemini;
    }

    public List<AiEstimateItem> enrich(List<AiEstimateItem> items) {
        return items.stream().map(item -> {
            MatchResult match = match(item.name(), item.category(), item.preparation());
            return new AiEstimateItem(item.name(), item.estimatedGrams(), item.category(), item.preparation(),
                    item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients(), match.foodId().orElse(null),
                    match.type(), match.confidence());
        }).toList();
    }

    public Optional<Food> resolve(AiEstimateFoodProposal proposal) {
        return match(proposal.name(), proposal.category(), proposal.preparation()).foodId()
                .flatMap(foods::findById)
                .filter(food -> food.getDeletedAt() == null && food.getModerationStatus() == ModerationStatus.APPROVED);
    }

    public Optional<Food> resolve(AiEstimateItem item) {
        return match(item.name(), item.category(), item.preparation()).foodId()
                .flatMap(foods::findById)
                .filter(food -> food.getDeletedAt() == null && food.getModerationStatus() == ModerationStatus.APPROVED);
    }

    private MatchResult match(String name, FoodCategory category, FoodPreparation preparation) {
        String query = SearchTextNormalizer.normalize(name);
        if (query.isBlank()) return MatchResult.none();

        List<Food> exact = foods.findActiveBySearchName(query, ModerationStatus.APPROVED);
        List<ScoredFood> candidates = score(exact, query, category, preparation);
        if (candidates.isEmpty()) {
            List<Food> fuzzyCandidates = tokens(query).stream()
                    .flatMap(token -> foods.findActiveBySearchToken(token, ModerationStatus.APPROVED, PageRequest.of(0, 20)).stream())
                    .collect(Collectors.toMap(Food::getId, food -> food, (left, right) -> left))
                    .values().stream().toList();
            candidates = score(fuzzyCandidates, query, category, preparation);
        }
        candidates = candidates.stream().filter(candidate -> candidate.score() >= FUZZY_THRESHOLD).toList();
        if (candidates.isEmpty()) return MatchResult.none();

        ScoredFood best = candidates.get(0);
        boolean ambiguous = candidates.size() > 1
                && best.score() - candidates.get(1).score() < 0.12;
        if (!ambiguous) {
            return new MatchResult(Optional.of(best.food().getId()), best.score() >= 1.0 ? "EXACT" : "FUZZY",
                    confidence(best.score()));
        }

        List<GeminiNutritionClient.FoodMatchCandidate> promptCandidates = candidates.stream()
                .limit(5)
                .map(candidate -> new GeminiNutritionClient.FoodMatchCandidate(candidate.food().getId(),
                        candidate.food().getName(), candidate.food().getCategory(), candidate.food().getPreparation(),
                        candidate.food().getCalories(), candidate.food().getProteinGrams(), candidate.food().getCarbsGrams(),
                        candidate.food().getFatGrams()))
                .toList();
        try {
            Optional<Long> selected = gemini.chooseFoodMatch(name, category, preparation, promptCandidates);
            if (selected.isPresent() && promptCandidates.stream().anyMatch(candidate -> candidate.id().equals(selected.get()))) {
                return new MatchResult(selected, "AI_TIE_BREAKER", 90);
            }
        } catch (Exception ex) {
            log.warn("AI food tie-breaker failed: {}", ex.getClass().getSimpleName());
        }
        return MatchResult.none();
    }

    private List<ScoredFood> score(List<Food> foods, String query, FoodCategory category,
            FoodPreparation preparation) {
        return foods.stream()
                .map(food -> new ScoredFood(food, score(food, query, category, preparation)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredFood::score).reversed()
                        .thenComparing(candidate -> candidate.food().getId()))
                .toList();
    }

    private double score(Food food, String query, FoodCategory category, FoodPreparation preparation) {
        FoodPreparation candidatePreparation = food.getPreparation() == null
                ? FoodPreparation.UNSPECIFIED : food.getPreparation();
        FoodPreparation requestedPreparation = preparation == null ? FoodPreparation.UNSPECIFIED : preparation;
        if (requestedPreparation != FoodPreparation.UNSPECIFIED
                && candidatePreparation != FoodPreparation.UNSPECIFIED
                && candidatePreparation != requestedPreparation) return 0;

        String candidateName = SearchTextNormalizer.normalize(food.getName());
        double nameScore = candidateName.equals(query) ? 1.0 : tokenScore(query, candidateName);
        if (nameScore < 0.55) return 0;
        double score = nameScore;
        if (category != null && food.getCategory() == category) score += 0.06;
        if (requestedPreparation != FoodPreparation.UNSPECIFIED && candidatePreparation == requestedPreparation) score += 0.10;
        if (candidatePreparation == FoodPreparation.UNSPECIFIED && requestedPreparation != FoodPreparation.UNSPECIFIED) score -= 0.04;
        if (!"AI_ESTIMATE".equals(food.getSource())) score += 0.03;
        return Math.min(1.0, score);
    }

    private double tokenScore(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0;
        long overlap = leftTokens.stream().filter(rightTokens::contains).count();
        double jaccard = (double) overlap / (leftTokens.size() + rightTokens.size() - overlap);
        if (right.contains(left) || left.contains(right)) return Math.max(0.82, jaccard);
        return jaccard;
    }

    private Set<String> tokens(String value) {
        Set<String> ignored = Set.of("de", "del", "la", "el", "y", "con", "sin", "en");
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(token -> token.length() > 1 && !ignored.contains(token))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private int confidence(double score) {
        return (int) Math.round(Math.min(99, Math.max(0, score * 100)));
    }

    private record ScoredFood(Food food, double score) { }

    public record MatchResult(Optional<Long> foodId, String type, int confidence) {
        static MatchResult none() { return new MatchResult(Optional.empty(), "NONE", 0); }
    }
}
