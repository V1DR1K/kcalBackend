package com.scalegrams.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;

@ExtendWith(MockitoExtension.class)
class AiFoodMatcherTests {
    @Mock
    FoodRepository foods;

    @Mock
    GeminiNutritionClient gemini;

    @Test
    void enrich_whenExactCandidatesAreAmbiguous_usesGeminiTieBreaker() {
        Food first = food(10L, "Pechuga de pollo");
        Food second = food(11L, "Pechuga de pollo");
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED))
                .thenReturn(List.of(first, second));
        when(gemini.chooseFoodMatch(eq("Pechuga de pollo"), eq(FoodCategory.MEAT), eq(FoodPreparation.UNSPECIFIED), anyList()))
                .thenReturn(Optional.of(11L));

        AiEstimateItem result = new AiFoodMatcher(foods, gemini).enrich(List.of(item())).get(0);

        assertThat(result.catalogFoodId()).isEqualTo(11L);
        assertThat(result.catalogMatchType()).isEqualTo("AI_TIE_BREAKER");
        assertThat(result.catalogMatchConfidence()).isEqualTo(90);
        verify(gemini).chooseFoodMatch(eq("Pechuga de pollo"), eq(FoodCategory.MEAT), eq(FoodPreparation.UNSPECIFIED), anyList());
    }

    private AiEstimateItem item() {
        return new AiEstimateItem("Pechuga de pollo", BigDecimal.valueOf(150), FoodCategory.MEAT,
                FoodPreparation.UNSPECIFIED, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, Map.of());
    }

    private Food food(Long id, String name) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCategory(FoodCategory.MEAT);
        food.setPreparation(FoodPreparation.UNSPECIFIED);
        food.setModerationStatus(ModerationStatus.APPROVED);
        return food;
    }
}
