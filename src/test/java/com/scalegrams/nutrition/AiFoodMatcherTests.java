package com.scalegrams.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;

@ExtendWith(MockitoExtension.class)
class AiFoodMatcherTests {
    @Mock FoodRepository foods;
    @Mock FoodSemanticSearchService semanticSearch;

    @Test
    void enrich_preservesTheAiProposalUntilTheUserReviewsIt() {
        AiEstimateItem proposed = item("Pechuga de pollo", "150", "30", "0", "1.5");

        AiEstimateItem result = new AiFoodMatcher(foods).enrich(List.of(proposed)).getFirst();

        assertThat(result.proteinGrams()).isEqualByComparingTo("30");
        assertThat(result.carbsGrams()).isEqualByComparingTo("0");
        assertThat(result.fatGrams()).isEqualByComparingTo("1.5");
        assertThat(result.catalogFoodId()).isNull();
    }

    @Test
    void preview_usesUniqueTextMatchWhenSemanticEmbeddingsAreUnavailable() {
        Food food = food(10L);
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food));

        AiFoodMatcher.CatalogMatch result = new AiFoodMatcher(foods).preview(
                item("Pechuga de pollo", "150", "30", "0", "1.5")).orElseThrow();

        assertThat(result.food()).isSameAs(food);
        assertThat(result.similarity()).isEqualTo(1.0);
        assertThat(result.proteinGrams()).isEqualByComparingTo("30.0");
        assertThat(result.macrosDiffer()).isFalse();
    }

    @Test
    void preview_marksProteinDifferenceForTheSameEstimatedGrams() {
        assertThat(previewWith(item("Pechuga de pollo", "150", "31", "0", "1.5"))
                .macrosDiffer()).isTrue();
    }

    @Test
    void preview_marksCarbohydrateDifferenceForTheSameEstimatedGrams() {
        assertThat(previewWith(item("Pechuga de pollo", "150", "30", "0.2", "1.5"))
                .macrosDiffer()).isTrue();
    }

    @Test
    void preview_marksFatDifferenceForTheSameEstimatedGrams() {
        assertThat(previewWith(item("Pechuga de pollo", "150", "30", "0", "1.6"))
                .macrosDiffer()).isTrue();
    }

    @Test
    void preview_comparesMacrosAfterRoundingToOneDecimal() {
        AiEstimateItem estimate = item("Pechuga de pollo", "150", "30.04", "0", "1.5");
        Food food = food(10L);
        food.setProteinGrams(new BigDecimal("20.026"));
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food));

        assertThat(new AiFoodMatcher(foods).preview(estimate).orElseThrow().macrosDiffer()).isFalse();
    }

    @Test
    void preview_usesTheBestAvailableSemanticCandidateWhenSeveralFoodsMatch() {
        Food textCandidate = food(10L);
        Food semanticCandidate = food(11L);
        semanticCandidate.setName("Pechuga de pollo orgánica");
        semanticCandidate.setBrand("Campo Verde");
        when(semanticSearch.search("Pechuga de pollo", FoodCategory.MEAT, FoodPreparation.UNSPECIFIED, 5))
                .thenReturn(List.of(new FoodSemanticSearchService.FoodMatch(semanticCandidate, 0.94),
                        new FoodSemanticSearchService.FoodMatch(textCandidate, 0.88)));

        AiFoodMatcher.CatalogMatch result = new AiFoodMatcher(foods, semanticSearch)
                .preview(item("Pechuga de pollo", "100", "20", "0", "1")).orElseThrow();

        assertThat(result.food()).isSameAs(semanticCandidate);
        assertThat(result.similarity()).isEqualTo(0.94);
    }

    @Test
    void preview_prefersTheMacroEquivalentFoodAmongSemanticCandidates() {
        Food closestByName = food(10L);
        closestByName.setProteinGrams(new BigDecimal("18"));
        Food equivalentMacros = food(11L);
        when(semanticSearch.search("Pechuga de pollo", FoodCategory.MEAT, FoodPreparation.UNSPECIFIED, 5))
                .thenReturn(List.of(new FoodSemanticSearchService.FoodMatch(closestByName, 0.97),
                        new FoodSemanticSearchService.FoodMatch(equivalentMacros, 0.89)));

        AiFoodMatcher.CatalogMatch result = new AiFoodMatcher(foods, semanticSearch)
                .preview(item("Pechuga de pollo", "100", "20", "0", "1")).orElseThrow();

        assertThat(result.food()).isSameAs(equivalentMacros);
        assertThat(result.macrosDiffer()).isFalse();
    }

    @Test
    void preview_keepsAmbiguousTextMatchesAsNewEstimates() {
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED))
                .thenReturn(List.of(food(10L), food(11L)));

        assertThat(new AiFoodMatcher(foods).preview(item("Pechuga de pollo", "150", "30", "0", "1.5")))
                .isEmpty();
    }

    @Test
    void preview_doesNotIdentifyABrandedProductFromAnUnbrandedProposal() {
        Food brandedFood = food(10L);
        brandedFood.setBrand("Marca específica");
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(brandedFood));

        assertThat(new AiFoodMatcher(foods).preview(item("Pechuga de pollo", "150", "30", "0", "1.5")))
                .isEmpty();
    }

    @Test
    void preview_doesNotMatchDifferentPreparationOrCategory() {
        Food cooked = food(10L);
        cooked.setPreparation(FoodPreparation.COOKED);
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(cooked));

        assertThat(new AiFoodMatcher(foods).preview(item("Pechuga de pollo", "150", "30", "0", "1.5")))
                .isEmpty();
    }

    private AiFoodMatcher.CatalogMatch previewWith(AiEstimateItem estimate) {
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food(10L)));
        return new AiFoodMatcher(foods).preview(estimate).orElseThrow();
    }

    private static AiEstimateItem item(String name, String grams, String protein, String carbs, String fat) {
        return new AiEstimateItem(name, new BigDecimal(grams), FoodCategory.MEAT, FoodPreparation.UNSPECIFIED,
                new BigDecimal(protein), new BigDecimal(carbs), new BigDecimal(fat), Map.of());
    }

    private static Food food(Long id) {
        Food food = new Food();
        food.setId(id);
        food.setName("Pechuga de pollo");
        food.setCategory(FoodCategory.MEAT);
        food.setPreparation(FoodPreparation.UNSPECIFIED);
        food.setModerationStatus(ModerationStatus.APPROVED);
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setProteinGrams(BigDecimal.valueOf(20));
        food.setCarbsGrams(BigDecimal.ZERO);
        food.setFatGrams(BigDecimal.ONE);
        return food;
    }
}
