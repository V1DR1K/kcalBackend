package com.vitalitypeak.kcal.externalfood;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class ExternalFoodLookupService {
    private final FoodLookupProperties properties;
    private final List<ExternalFoodProvider> providers;

    public ExternalFoodLookupService(FoodLookupProperties properties, List<ExternalFoodProvider> providers) {
        this.properties = properties;
        this.providers = providers;
    }

    public Optional<ExternalFoodCandidate> lookupByBarcode(String barcode) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        return providers.stream()
                .map(provider -> provider.lookupByBarcode(barcode))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public List<ExternalFoodCandidate> searchByText(String query, int limit) {
        if (!properties.enabled() || query == null || query.isBlank()) return List.of();
        return providers.stream()
                .flatMap(provider -> provider.searchByText(query.trim(), limit).stream())
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }
}
