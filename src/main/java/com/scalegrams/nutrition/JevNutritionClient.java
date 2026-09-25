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

            Map<String, Object> questions = new LinkedHashMap<>();
            questions.put("detected_type", Map.of(
                    "type", "choice",
                    "instructions", "Clasificá el resultado nutricional según represente un único alimento reutilizable, una receta o plato compuesto, o sea ambiguo.",
                    "criteria", Map.of(
                            "FOOD", "Un único alimento o producto que debe guardarse como ficha reutilizable.",
                            "RECIPE", "Un plato o preparación compuesta por uno o más ingredientes.",
                            "AMBIGUOUS", "La evidencia estructurada no permite decidir con seguridad.")));
            questions.put("evidence_quality", Map.of(
                    "type", "score",
                    "instructions", "Evaluá si la evidencia es suficiente para guardar el resultado después de revisión humana.",
                    "criteria", java.util.List.of("Insuficiente", "Requiere correcciones", "Suficiente")));
            questions.put("needs_review", Map.of(
                    "type", "noul",
                    "instructions", "¿El resultado requiere revisión humana cuidadosa por ambigüedad, datos faltantes o incoherencias?",
                    "criteria", Map.of(
                            "true", "Hay ambigüedad, faltan datos importantes o existen incoherencias.",
                            "false", "La evidencia estructurada es consistente y suficiente para una revisión habitual.")));

            JsonNode response = client.post().uri("/api/v1/systemone")
                    .body(Map.of("state", state, "model", properties.getJevModel(), "questions", questions))
                    .retrieve().body(JsonNode.class);
            JsonNode answers = response == null ? objectMapper.createObjectNode() : response.path("answers");
            JsonNode type = answers.path("detected_type");
            JsonNode quality = answers.path("evidence_quality");
            JsonNode review = answers.path("needs_review");
            return Optional.of(new JevDecision(
                    type.path("choice").asText("AMBIGUOUS"),
                    type.path("confidence").isNumber() ? type.path("confidence").asDouble() : null,
                    quality.path("score").isNumber() ? quality.path("score").asDouble() : null,
                    review.path("noul").isNumber() ? review.path("noul").asDouble() : null,
                    response == null ? properties.getJevModel() : response.path("model").asText(properties.getJevModel())));
        } catch (Exception ex) {
            log.warn("JEV shadow classification unavailable: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public record JevDecision(String detectedType, Double confidence, Double evidenceQuality,
            Double needsReviewProbability, String model) { }
}
