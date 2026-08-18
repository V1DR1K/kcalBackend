package com.scalegrams.nutrition;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodLogNutrientRepository extends JpaRepository<FoodLogNutrient, Long> {
    List<FoodLogNutrient> findByFoodLogIdOrderByDefinitionDisplayOrder(Long foodLogId);
}
