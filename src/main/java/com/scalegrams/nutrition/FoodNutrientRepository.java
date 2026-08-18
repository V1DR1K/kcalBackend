package com.scalegrams.nutrition;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodNutrientRepository extends JpaRepository<FoodNutrient, Long> {
    List<FoodNutrient> findByFoodIdOrderByDefinitionDisplayOrder(Long foodId);
    Optional<FoodNutrient> findByFoodIdAndDefinitionCode(Long foodId, String code);
}
