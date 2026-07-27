package com.vitalitypeak.kcal.nutrition;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalitypeak.kcal.common.BadRequestException;

@Component
public class GeminiNutritionClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiNutritionClient.class);
    private static final String PROMPT = """
            Analizá esta foto de comida para registrar un cheat meal. Respondé únicamente JSON válido.
            Identificá los alimentos visibles y estimá por cada uno gramos, proteínas, carbohidratos y grasas.
            No inventes precisión: usá estimaciones conservadoras, incluí aceite, queso o salsas solo si son visibles,
            y anotá las suposiciones. confidence debe ser un entero entre 0 y 100. Máximo 12 items.
            Incluí una descripción breve de lo que se observa en la foto, sin afirmar ingredientes que no sean visibles.
            Esquema: {"name":"nombre breve del plato","description":"descripción breve de lo observado","confidence":0,"assumptions":["..."],"items":[{"name":"...","estimatedGrams":0,"proteinGrams":0,"carbsGrams":0,"fatGrams":0}]}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiNutritionProperties properties;

    public GeminiNutritionClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
            AiNutritionProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AiNutritionResult analyze(byte[] image, String contentType) {
        try {
            Map<String, Object> content = Map.of("parts", List.of(
                    Map.of("text", PROMPT),
                    Map.of("inlineData", Map.of("mimeType", contentType, "data", Base64.getEncoder().encodeToString(image)))));
            Map<String, Object> body = Map.of(
                    "contents", List.of(content),
                    "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json"));
            JsonNode response = restClient.post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}",
                            properties.getModel(), properties.getGeminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response == null ? null : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            if (text == null || text.isBlank()) throw new BadRequestException("La IA no pudo reconocer alimentos en esta foto.");
            JsonNode result = objectMapper.readTree(stripCodeFence(text));
            List<AiNutritionItem> items = new ArrayList<>();
            for (JsonNode item : result.path("items")) {
                String name = item.path("name").asText("").trim();
                BigDecimal grams = decimal(item, "estimatedGrams");
                BigDecimal protein = decimal(item, "proteinGrams");
                BigDecimal carbs = decimal(item, "carbsGrams");
                BigDecimal fat = decimal(item, "fatGrams");
                if (name.length() >= 2 && grams.signum() > 0 && grams.compareTo(BigDecimal.valueOf(3000)) <= 0
                        && protein.compareTo(BigDecimal.valueOf(500)) <= 0 && carbs.compareTo(BigDecimal.valueOf(1000)) <= 0
                        && fat.compareTo(BigDecimal.valueOf(500)) <= 0) {
                    items.add(new AiNutritionItem(name.substring(0, Math.min(name.length(), 120)), grams, protein, carbs, fat));
                }
            }
            if (items.isEmpty()) throw new BadRequestException("No pudimos estimar los alimentos. Probá con mejor luz o cargalos manualmente.");
            if (items.size() > 12) items = items.subList(0, 12);
            List<String> assumptions = new ArrayList<>();
            for (JsonNode assumption : result.path("assumptions")) {
                String value = assumption.asText("").trim();
                if (!value.isBlank()) assumptions.add(value.substring(0, Math.min(value.length(), 160)));
                if (assumptions.size() == 4) break;
            }
            String name = result.path("name").asText("Comida estimada").trim();
            String description = result.path("description").asText("").trim();
            int confidence = Math.max(0, Math.min(100, result.path("confidence").asInt(50)));
            return new AiNutritionResult(
                    name.isBlank() ? "Comida estimada" : name.substring(0, Math.min(name.length(), 120)),
                    description.substring(0, Math.min(description.length(), 240)), confidence, assumptions, items);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) throw new AiQuotaExceededException(retryAt(ex));
            log.warn("Gemini meal analysis failed with upstream status {}", ex.getStatusCode().value());
            throw new BadRequestException("Gemini no pudo analizar la foto. Intentá nuevamente en unos minutos.");
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Gemini meal analysis did not return a valid estimate: {}", ex.getClass().getSimpleName());
            throw new BadRequestException("No se pudo analizar la foto. Intentá nuevamente en unos minutos.");
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        try {
            BigDecimal value = new BigDecimal(node.path(field).asText("0"));
            return value.max(BigDecimal.ZERO).setScale(1, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private OffsetDateTime retryAt(RestClientResponseException ex) {
        try {
            JsonNode details = objectMapper.readTree(ex.getResponseBodyAsString()).path("error").path("details");
            for (JsonNode detail : details) {
                String delay = detail.path("retryDelay").asText("");
                if (delay.endsWith("s")) return OffsetDateTime.now().plus(Duration.ofMillis((long) (Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1000)));
            }
        } catch (Exception ignored) {
            // Gemini may omit RetryInfo when its daily quota is exhausted.
        }
        ZoneId pacificTime = ZoneId.of("America/Los_Angeles");
        return ZonedDateTime.now(pacificTime).toLocalDate().plusDays(1).atStartOfDay(pacificTime).toOffsetDateTime();
    }

    private static String stripCodeFence(String value) {
        return value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }

    public record AiNutritionResult(String name, String description, int confidence, List<String> assumptions, List<AiNutritionItem> items) {
    }

    public record AiNutritionItem(String name, BigDecimal estimatedGrams, BigDecimal proteinGrams,
            BigDecimal carbsGrams, BigDecimal fatGrams) {
    }
}
