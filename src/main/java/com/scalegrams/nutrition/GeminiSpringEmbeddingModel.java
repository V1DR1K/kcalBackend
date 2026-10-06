package com.scalegrams.nutrition;

import java.util.ArrayList;
import java.util.List;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Adapts the current Gemini embedding API to Spring AI's provider-neutral contract. */
public class GeminiSpringEmbeddingModel implements EmbeddingModel {
    private final Client client;
    private final String model;
    private final int dimensions;

    public GeminiSpringEmbeddingModel(Client client, String model, int dimensions) {
        this.client = client;
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        EmbedContentConfig config = EmbedContentConfig.builder()
                .outputDimensionality(dimensions)
                .build();
        List<Embedding> embeddings = new ArrayList<>(request.getInstructions().size());
        for (int index = 0; index < request.getInstructions().size(); index++) {
            EmbedContentResponse response = client.models.embedContent(
                    model, request.getInstructions().get(index), config);
            ContentEmbedding embedding = response.embeddings()
                    .orElseThrow(() -> new IllegalStateException("Gemini devolvió una respuesta sin embedding."))
                    .getFirst();
            List<Float> values = embedding.values()
                    .orElseThrow(() -> new IllegalStateException("Gemini devolvió un embedding vacío."));
            if (values.size() != dimensions) {
                throw new IllegalStateException("Gemini devolvió una dimensión de embedding inesperada.");
            }
            float[] vector = new float[values.size()];
            for (int dimension = 0; dimension < values.size(); dimension++) {
                vector[dimension] = values.get(dimension);
            }
            embeddings.add(new Embedding(vector, index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
