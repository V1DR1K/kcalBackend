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
import com.scalegrams.catalog.*;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;

@ExtendWith(MockitoExtension.class)
class AiFoodMatcherTests {
    @Mock FoodRepository foods;

    @Test void exactCompatibleIdentityIsReused() {
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food(10L)));
        assertThat(match().catalogFoodId()).isEqualTo(10L);
    }
    @Test void ambiguousIdentitiesRemainEstimates() {
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food(10L), food(11L)));
        assertThat(match().catalogFoodId()).isNull();
    }
    @Test void genericProposalCannotIdentifyBrandedProduct() {
        Food food = food(10L); food.setBrand("Marca específica");
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food));
        assertThat(match().catalogFoodId()).isNull();
    }
    @Test void unspecifiedPreparationCannotMatchCookedFood() {
        Food food = food(10L); food.setPreparation(FoodPreparation.COOKED);
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food));
        assertThat(match().catalogFoodId()).isNull();
    }
    @Test void differentCategoryCannotMatch() {
        Food food = food(10L); food.setCategory(FoodCategory.OTHER);
        when(foods.findActiveBySearchName("pechuga de pollo", ModerationStatus.APPROVED)).thenReturn(List.of(food));
        assertThat(match().catalogFoodId()).isNull();
    }
    private AiEstimateItem match() {
        return new AiFoodMatcher(foods).enrich(List.of(new AiEstimateItem("Pechuga de pollo", BigDecimal.valueOf(150), FoodCategory.MEAT,
                FoodPreparation.UNSPECIFIED, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, Map.of()))).getFirst();
    }
    private Food food(Long id) {
        Food food = new Food(); food.setId(id); food.setName("Pechuga de pollo");
        food.setCategory(FoodCategory.MEAT); food.setPreparation(FoodPreparation.UNSPECIFIED);
        food.setModerationStatus(ModerationStatus.APPROVED); return food;
    }
}
