package com.scalegrams.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import com.scalegrams.catalog.FoodRepository;

@ExtendWith(MockitoExtension.class)
class FoodSemanticSearchServiceTests {
    @Mock FoodVectorRepository vectors;
    @Mock FoodRepository foods;
    @Mock SemanticSearchProperties properties;
    @Mock ObjectProvider<EmbeddingModel> embeddingModels;

    @Test
    void documentText_includesNameBrandAndAvailableFoodDetails() {
        String document = FoodSemanticSearchService.documentText("Yogur natural", "Lácteos del Sur",
                "DAIRY", "AS_SOLD", "sin azúcar, descremado");

        assertThat(document).contains("Name: Yogur natural", "Brand: Lácteos del Sur",
                "Category: DAIRY", "Preparation: AS_SOLD", "Tags: sin azúcar, descremado");
    }

    @Test
    void search_whenGeminiEmbeddingFails_returnsNoSemanticMatchesForTextFallback() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(properties.isEnabled()).thenReturn(true);
        when(embeddingModels.getIfAvailable()).thenReturn(model);
        when(model.embed(anyString())).thenThrow(new IllegalStateException("Gemini unavailable"));
        FoodSemanticSearchService service = new FoodSemanticSearchService(vectors, foods, properties, embeddingModels);

        assertThat(service.search("leche alta en proteínas", null, null, 10)).isEmpty();

        verify(vectors, never()).findSimilar(org.mockito.ArgumentMatchers.any(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void search_whenSemanticSearchIsDisabled_doesNotCallGeminiOrTheVectorStore() {
        when(properties.isEnabled()).thenReturn(false);
        FoodSemanticSearchService service = new FoodSemanticSearchService(vectors, foods, properties, embeddingModels);

        assertThat(service.search("leche", null, null, 10)).isEmpty();

        verify(embeddingModels, never()).getIfAvailable();
        verify(vectors, never()).findSimilar(org.mockito.ArgumentMatchers.any(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble());
    }
}
