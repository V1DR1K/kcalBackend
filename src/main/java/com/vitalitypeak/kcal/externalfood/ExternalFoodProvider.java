package com.vitalitypeak.kcal.externalfood;

import java.util.Optional;
import java.util.List;

public interface ExternalFoodProvider {
    Optional<ExternalFoodCandidate> lookupByBarcode(String barcode);

    default List<ExternalFoodCandidate> searchByText(String query, int limit) {
        return List.of();
    }
}
