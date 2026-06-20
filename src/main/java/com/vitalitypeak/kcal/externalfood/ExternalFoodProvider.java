package com.vitalitypeak.kcal.externalfood;

import java.util.Optional;

public interface ExternalFoodProvider {
    Optional<ExternalFoodCandidate> lookupByBarcode(String barcode);
}
