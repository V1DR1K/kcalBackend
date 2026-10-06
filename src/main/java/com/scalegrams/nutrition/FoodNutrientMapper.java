package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.scalegrams.catalog.Food;
import com.scalegrams.nutrition.NutritionDtos.FoodSummaryResponse;
import com.scalegrams.nutrition.NutritionDtos.NutrientValueResponse;

@Component
public class FoodNutrientMapper {
    private final NutrientDefinitionRepository nutrientDefinitions;

    public FoodNutrientMapper(NutrientDefinitionRepository nutrientDefinitions) {
        this.nutrientDefinitions = nutrientDefinitions;
    }

    public FoodSummaryResponse toSummaryResponse(Food food) {
        return new FoodSummaryResponse(food.getId(), food.getName(), food.getBrand(), food.getBarcode(), food.getCategory(),
                food.getBaseUnit(), food.getBaseQuantity(), food.getCalories(), food.getProteinGrams(), food.getCarbsGrams(),
                food.getFatGrams(), food.getPreparation(), food.getPreparationGroup(), food.getServingName(),
                food.getServingWeightGrams(), food.getImageUrl(), scaleNutrients(food, BigDecimal.ONE),
                food.getCookedYieldFactor(), food.getCookedYieldSource(), food.getCookedYieldAssumption());
    }

    public List<NutrientValueResponse> scaleNutrients(Food food, BigDecimal ratio) {
        Map<String, FoodNutrient> existing = food.getNutrients().stream().filter(item -> item.getDefinition() != null)
                .collect(Collectors.toMap(item -> item.getDefinition().getCode(), item -> item, (left, right) -> left,
                        LinkedHashMap::new));
        List<NutrientValueResponse> values = nutrientDefinitions.findAll().stream()
                .filter(NutrientDefinition::isVisible)
                .sorted(Comparator.comparing(NutrientDefinition::getDisplayOrder))
                .map(definition -> {
                    FoodNutrient item = existing.get(definition.getCode());
                    BigDecimal legacyValue = switch (definition.getCode()) {
                        case "CALORIES" -> food.getCalories() == null ? null : BigDecimal.valueOf(food.getCalories());
                        case "PROTEIN" -> food.getProteinGrams();
                        case "CARBOHYDRATE" -> food.getCarbsGrams();
                        case "FAT" -> food.getFatGrams();
                        default -> null;
                    };
                    BigDecimal value = item == null ? legacyValue : item.getValue();
                    String source = item == null ? NutrientSource.LEGACY.name()
                            : item.getSource() == null ? NutrientSource.LEGACY.name() : item.getSource().name();
                    String status = item == null
                            ? (legacyValue == null ? NutrientStatus.MISSING.name() : NutrientStatus.PARTIAL.name())
                            : item.getStatus() == null ? NutrientStatus.MISSING.name() : item.getStatus().name();
                    return new NutrientValueResponse(definition.getCode(), definition.getName(),
                            definition.getNutrientGroup(), definition.getUnit(),
                            value == null ? null : NutritionMath.scale(value.multiply(ratio)), source, status);
                }).toList();
        if (!values.isEmpty()) return values;
        return List.of(
                new NutrientValueResponse("PROTEIN", "Proteínas", "MACRO", "g",
                        NutritionMath.scaled(food.getProteinGrams(), ratio), "LEGACY", "PARTIAL"),
                new NutrientValueResponse("CARBOHYDRATE", "Carbohidratos", "MACRO", "g",
                        NutritionMath.scaled(food.getCarbsGrams(), ratio), "LEGACY", "PARTIAL"),
                new NutrientValueResponse("FAT", "Grasas", "MACRO", "g",
                        NutritionMath.scaled(food.getFatGrams(), ratio), "LEGACY", "PARTIAL"));
    }
}
