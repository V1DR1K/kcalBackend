package com.scalegrams.nutrition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.ModerationStatus;

@Service
public class FoodSemanticSearchService {
    private static final Logger log = LoggerFactory.getLogger(FoodSemanticSearchService.class);
    private static final String TASK = "Find food catalog entries semantically similar to the input.";

    private final FoodVectorRepository vectors;
    private final FoodRepository foods;
    private final SemanticSearchProperties properties;
    private final ObjectProvider<EmbeddingModel> embeddingModels;

    public FoodSemanticSearchService(FoodVectorRepository vectors, FoodRepository foods,
            SemanticSearchProperties properties, ObjectProvider<EmbeddingModel> embeddingModels) {
        this.vectors = vectors;
        this.foods = foods;
        this.properties = properties;
        this.embeddingModels = embeddingModels;
    }

    public List<FoodMatch> search(String query, FoodCategory category, FoodPreparation preparation, int limit) {
        return search(query, category, preparation, limit, null);
    }

    public List<FoodMatch> search(String query, FoodCategory category, FoodPreparation preparation, int limit,
            Long ownerUserId) {
        EmbeddingModel model = availableModel();
        if (model == null || query == null || query.isBlank() || limit < 1) return List.of();
        try {
            float[] embedding = model.embed(queryText(query, category, preparation));
            List<FoodVectorRepository.FoodVectorMatch> matches = vectors.findSimilar(embedding,
                    properties.getEmbeddingModel(), category, preparation, limit,
                    properties.getMinimumSimilarity(), ownerUserId);
            if (matches.isEmpty()) return List.of();
            Map<Long, Food> foodsById = new HashMap<>();
            foods.findAllById(matches.stream().map(FoodVectorRepository.FoodVectorMatch::foodId).toList())
                    .forEach(food -> foodsById.put(food.getId(), food));
            return matches.stream().map(match -> foodsById.get(match.foodId()))
                    .filter(food -> food != null && food.getDeletedAt() == null
                            && food.getModerationStatus() == ModerationStatus.APPROVED)
                    .map(food -> new FoodMatch(food, matches.stream()
                            .filter(match -> match.foodId() == food.getId()).findFirst().orElseThrow().similarity()))
                    .toList();
        } catch (Exception error) {
            log.warn("Semantic food search unavailable; falling back to text search cause={}",
                    error.getClass().getSimpleName());
            return List.of();
        }
    }

    public void indexIfAvailable(Food food) {
        EmbeddingModel model = availableModel();
        if (model == null || food == null || food.getId() == null) return;
        if (food.getDeletedAt() != null || food.getModerationStatus() != ModerationStatus.APPROVED) {
            vectors.delete(food.getId());
            return;
        }
        try {
            String sourceText = documentText(food.getName(), food.getBrand(),
                    food.getCategory() == null ? null : food.getCategory().name(),
                    food.getPreparation() == null ? null : food.getPreparation().name(),
                    food.getSearchTags());
            vectors.save(food.getId(), model.embed(sourceText), properties.getEmbeddingModel(), sourceText);
        } catch (Exception error) {
            log.warn("Food embedding could not be indexed; foodId={} cause={}", food.getId(),
                    error.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${app.semantic-search.reindex-delay-ms:60000}", initialDelay = 15000)
    public void indexPendingFoods() {
        EmbeddingModel model = availableModel();
        if (model == null) return;
        List<FoodVectorRepository.FoodEmbeddingSource> missing = vectors.findMissingEmbeddings(
                properties.getEmbeddingModel(), properties.getReindexBatchSize());
        for (FoodVectorRepository.FoodEmbeddingSource source : missing) {
            try {
                String sourceText = documentText(source.name(), source.brand(), source.category(),
                        source.preparation(), source.tags());
                vectors.save(source.id(), model.embed(sourceText), properties.getEmbeddingModel(), sourceText);
            } catch (Exception error) {
                log.warn("Food catalog embedding backfill stopped; foodId={} cause={}", source.id(),
                        error.getClass().getSimpleName());
                return;
            }
        }
        if (!missing.isEmpty()) log.info("Food catalog semantic index updated count={}", missing.size());
    }

    private EmbeddingModel availableModel() {
        if (!properties.isEnabled()) return null;
        return Optional.ofNullable(embeddingModels.getIfAvailable()).orElse(null);
    }

    static String queryText(String query, FoodCategory category, FoodPreparation preparation) {
        StringBuilder text = new StringBuilder(TASK).append("\nInput: ").append(query.trim());
        if (category != null) text.append("\nCategory: ").append(category.name());
        if (preparation != null && preparation != FoodPreparation.UNSPECIFIED) {
            text.append("\nPreparation: ").append(preparation.name());
        }
        return text.toString();
    }

    static String documentText(String name, String brand, String category, String preparation, String tags) {
        List<String> details = new ArrayList<>();
        addDetail(details, "Name", name);
        addDetail(details, "Brand", brand);
        addDetail(details, "Category", category);
        addDetail(details, "Preparation", preparation);
        addDetail(details, "Tags", tags);
        return TASK + "\nFood catalog entry:\n" + String.join("\n", details);
    }

    private static void addDetail(List<String> details, String label, String value) {
        if (value != null && !value.isBlank()) details.add(label + ": " + value.trim());
    }

    public record FoodMatch(Food food, double similarity) {
    }
}
