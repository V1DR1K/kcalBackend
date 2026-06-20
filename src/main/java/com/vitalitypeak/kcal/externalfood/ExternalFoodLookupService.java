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
}
