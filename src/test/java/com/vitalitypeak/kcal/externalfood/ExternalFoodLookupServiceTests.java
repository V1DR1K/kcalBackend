package com.vitalitypeak.kcal.externalfood;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

class ExternalFoodLookupServiceTests {
    private final FoodLookupProperties properties = new FoodLookupProperties(
            true,
            Duration.ofSeconds(3),
            new FoodLookupProperties.OpenFoodFacts("https://example.test", "KCALS test"),
            new FoodLookupProperties.Usda("https://example.test", ""));

    @Test
    void providerCancellationDoesNotBreakBarcodeLookup() {
        ExternalFoodLookupService service = new ExternalFoodLookupService(properties,
                List.of(new CancellingProvider()));

        assertThat(service.lookupByBarcode("7790000000000")).isEmpty();
    }

    @Test
    void providerCancellationDoesNotBreakTextSearch() {
        ExternalFoodLookupService service = new ExternalFoodLookupService(properties,
                List.of(new CancellingProvider()));

        assertThat(service.searchByText("galletitas", 10)).isEmpty();
    }

    static class CancellingProvider implements ExternalFoodProvider {
        @Override
        public Optional<ExternalFoodCandidate> lookupByBarcode(String barcode) {
            throw new CancellationException();
        }

        @Override
        public List<ExternalFoodCandidate> searchByText(String query, int limit) {
            throw new CancellationException();
        }
    }
}
