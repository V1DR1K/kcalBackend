package com.vitalitypeak.kcal.externalfood;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExternalFoodLookupService {
    private static final Logger log = LoggerFactory.getLogger(ExternalFoodLookupService.class);

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
                .map(provider -> safeLookupByBarcode(provider, barcode))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public List<ExternalFoodCandidate> searchByText(String query, int limit) {
        if (!properties.enabled() || query == null || query.isBlank()) return List.of();
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        List<ExternalFoodCandidate> result = new ArrayList<>();
        for (ExternalFoodProvider provider : providers) {
            result.addAll(safeSearchByText(provider, query.trim(), boundedLimit));
            if (result.size() >= boundedLimit) break;
        }
        return result.stream().limit(boundedLimit).toList();
    }

    private Optional<ExternalFoodCandidate> safeLookupByBarcode(ExternalFoodProvider provider, String barcode) {
        try {
            return provider.lookupByBarcode(barcode);
        } catch (RuntimeException ex) {
            log.warn("External food lookup failed for provider {} and barcode {}: {}",
                    provider.getClass().getSimpleName(), barcode, ex.toString());
            return Optional.empty();
        }
    }

    private List<ExternalFoodCandidate> safeSearchByText(ExternalFoodProvider provider, String query, int limit) {
        try {
            return provider.searchByText(query, limit);
        } catch (RuntimeException ex) {
            log.warn("External food search failed for provider {} and query '{}': {}",
                    provider.getClass().getSimpleName(), query, ex.toString());
            return List.of();
        }
    }
}
