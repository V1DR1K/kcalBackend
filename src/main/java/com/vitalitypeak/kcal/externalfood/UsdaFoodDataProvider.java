package com.vitalitypeak.kcal.externalfood;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class UsdaFoodDataProvider {
    private final FoodLookupProperties properties;

    public UsdaFoodDataProvider(FoodLookupProperties properties) {
        this.properties = properties;
    }

    public Optional<ExternalFoodCandidate> searchByText(String query) {
        // Reserved for generic food search enrichment. Barcode lookup stays on Open Food Facts in v1.
        if (query == null || query.isBlank() || properties.usda().apiKey() == null || properties.usda().apiKey().isBlank()) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
