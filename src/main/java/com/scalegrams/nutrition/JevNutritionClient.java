package com.scalegrams.nutrition;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalegrams.nutrition.GeminiNutritionClient.AiNutritionResult;

@Component
public class JevNutritionClient {
    private static final Logger log = LoggerFactory.getLogger(JevNutritionClient.class);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiNutritionProperties properties;

    public JevNutritionClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
            AiNutritionProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<JevDecision> classify(AiCaptureTarget requestedTarget, AiNutritionResult result) {
        if (!properties.isJevEnabled() || properties.getJevApiKey() == null
                || properties.getJevApiKey().isBlank()) return Optional.empty();
        try {
            var requestFactory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
            requestFactory.setReadTimeout(Duration.ofSeconds(3));
            RestClient client = restClientBuilder.requestFactory(requestFactory)
                    .baseUrl(properties.getJevBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getJevApiKey())
                    .build();

            Map<String, Object> state = new LinkedHashMap<>();
            state.put("requestedTarget", requestedTarget.name());
            state.put("name", result.name());
            state.put("description", result.description());
            state.put("providerConfidence", result.confidence());
            state.put("assumptions", result.assumptions());
            state.put("items", result.items());
            state.put("macronutrientsPer100g", result.items().stream().map(item -> Map.of(
                    "name", item.name(),
                    "category", item.category().name(),
                    "preparation", item.preparation().name(),
                    "proteinGrams", perHundred(item.proteinGrams(), item.estimatedGrams()),
                    "carbsGrams", perHundred(item.carbsGrams(), item.estimatedGrams()),
                    "fatGrams", perHundred(item.fatGrams(), item.estimatedGrams()))).toList());

            Map<String, Object> questions = new LinkedHashMap<>();
            questions.put("detected_type", Map.of(
                    "type", "choice",
                    "instructions", "Clasificá el resultado nutricional según represente un único alimento reutilizable, una receta o plato compuesto, o sea ambiguo.",
                    "criteria", Map.of(
                            "FOOD", "Un único alimento o producto que debe guardarse como ficha reutilizable.",
                            "RECIPE", "Un plato o preparación compuesta por uno o más ingredientes.",
                            "AMBIGUOUS", "La evidencia estructurada no permite decidir con seguridad.")));
            questions.put("protein_quality", macroQuestion("proteínas"));
            questions.put("carbohydrate_quality", macroQuestion("carbohidratos"));
            questions.put("fat_quality", macroQuestion("grasas"));

            JsonNode response = client.post().uri("/api/v1/systemone")
                    .body(Map.of("state", state, "model", properties.getJevModel(), "questions", questions))
                    .retrieve().body(JsonNode.class);
            JsonNode answers = response == null ? objectMapper.createObjectNode() : response.path("answers");
            JsonNode type = answers.path("detected_type");
            return Optional.of(new JevDecision(
                    type.path("choice").asText("AMBIGUOUS"),
                    type.path("confidence").isNumber() ? type.path("confidence").asDouble() : null,
                    macroDecision(answers.path("protein_quality")),
                    macroDecision(answers.path("carbohydrate_quality")),
                    macroDecision(answers.path("fat_quality")),
                    response == null ? properties.getJevModel() : response.path("model").asText(properties.getJevModel())));
        } catch (Exception ex) {
            log.warn("JEV shadow classification unavailable: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Map<String, Object> macroQuestion(String macroLabel) {
        return Map.of(
                "type", "choice",
                "instructions", "Clasificá la plausibilidad de " + macroLabel + " por 100 g para cada alimento, considerando su categoría y preparación. Detectá valores imposibles o claramente incoherentes, no diferencias normales entre marcas o recetas.",
                "criteria", Map.of(
                        "CONSISTENT", "Plausible para estos alimentos y preparaciones.",
                        "REVIEW", "Hay una incoherencia clara que una persona debería revisar.",
                        "INSUFFICIENT", "No hay evidencia suficiente para valorar este macronutriente."));
    }

    private static MacroDecision macroDecision(JsonNode answer) {
        return new MacroDecision(answer.path("choice").asText("INSUFFICIENT"),
                answer.path("confidence").isNumber() ? answer.path("confidence").asDouble() : null);
    }

    private static java.math.BigDecimal perHundred(java.math.BigDecimal nutrient, java.math.BigDecimal grams) {
        if (nutrient == null || grams == null || grams.signum() <= 0) return java.math.BigDecimal.ZERO;
        return nutrient.multiply(java.math.BigDecimal.valueOf(100))
                .divide(grams, 2, java.math.RoundingMode.HALF_UP);
    }

    public record MacroDecision(String classification, Double confidence) { }

    public record JevDecision(String detectedType, Double confidence, MacroDecision proteinQuality,
            MacroDecision carbohydrateQuality, MacroDecision fatQuality, String model) { }
}
