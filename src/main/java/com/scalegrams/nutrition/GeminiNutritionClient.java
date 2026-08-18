package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.HttpClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateDraft;

@Component
public class GeminiNutritionClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiNutritionClient.class);
    private static final List<String> FALLBACK_MODELS = List.of("gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-2.0-flash");
    private static final String PROMPT = """
            Analizá esta foto de comida para registrar un cheat meal. Respondé únicamente JSON válido.
            Identificá los alimentos visibles y estimá por cada uno gramos, proteínas, carbohidratos y grasas.
            Si no se puede asociar con una base nutricional confiable, incluí también nutrientes estimados en el objeto nutrients;
            no inventes valores para nutrientes desconocidos y omitilos. La aplicación marcará esos valores como ESTIMATED.
            Clasificá cada alimento con una categoría y preparación de esta lista exacta: categorías PROTEIN, MEAT, DAIRY, FRUIT, VEGETABLE, LEGUME, CEREAL, BAKERY, BEVERAGE, SWEET, SNACK, FAT, OTHER; preparaciones RAW, COOKED, AS_SOLD, UNSPECIFIED.
            No inventes precisión: usá estimaciones conservadoras, incluí aceite, queso o salsas solo si son visibles,
            y anotá las suposiciones. confidence debe ser un entero entre 0 y 100. Máximo 12 items.
            Incluí una descripción breve de lo que se observa en la foto, sin afirmar ingredientes que no sean visibles.
            Esquema: {"name":"nombre breve del plato","description":"descripción breve de lo observado","confidence":0,"assumptions":["..."],"items":[{"name":"...","estimatedGrams":0,"category":"OTHER","preparation":"UNSPECIFIED","proteinGrams":0,"carbsGrams":0,"fatGrams":0,"nutrients":{"FIBER":0,"SODIUM":0}}]}
            """;
    private static final String TRANSCRIPTION_PROMPT = """
            Transcribí esta breve nota de voz en español sobre una comida. Devolvé solo la transcripción,
            sin comentarios ni puntuación adicional. Máximo 240 caracteres.
            """;
    private static final String REFINEMENT_PROMPT = """
            Revisá una estimación nutricional de una foto de comida. Respondé únicamente JSON válido con el mismo esquema indicado.
            La foto es la evidencia principal. El contexto original y la corrección de la persona tienen prioridad sobre el borrador;
            el borrador actual puede incluir cambios manuales y debe usarse como base, no como una fuente nutricional definitiva.
            Aplicá la corrección a una lista completa de alimentos: agregá, quitá o ajustá ítems según corresponda.
            No inventes precisión: usá estimaciones conservadoras, anotá las suposiciones y devolvé como máximo 12 ítems.
            Esquema: {"name":"nombre breve del plato","description":"descripción breve de lo observado","confidence":0,"assumptions":["..."],"items":[{"name":"...","estimatedGrams":0,"category":"OTHER","preparation":"UNSPECIFIED","proteinGrams":0,"carbsGrams":0,"fatGrams":0}]}
            """;
    private static final String TRANSLATION_PROMPT = """
            Traducí estos nombres de alimentos del español al inglés americano, para buscar en una base de datos nutricional genérica (USDA FoodData Central).
            Devolvé únicamente JSON válido con la misma cantidad de claves: {"<nombre original>":"<traducción en inglés>"}.
            Usá términos genéricos y simples (ej. "Pechuga de Pollo" -> "chicken breast", "Arroz Blanco" -> "white rice").
            """;
    private static final String NAME_ESTIMATE_PROMPT = """
            Estimá el perfil nutricional por 100 gramos de alimento comestible, para una base de datos nutricional.
            Respondé únicamente JSON válido:
            {"name":"...","calories":0,"proteinGrams":0.0,"carbsGrams":0.0,"fatGrams":0.0,"nutrients":{"FIBER":0.0,"SUGARS_TOTAL":0.0,"SATURATED_FAT":0.0,"SODIUM":0.0,"CHOLESTEROL":0.0,"POTASSIUM":0.0,"CALCIUM":0.0,"IRON":0.0,"VITAMIN_C":0.0,"VITAMIN_D":0.0}}
            Valores por 100 gramos, estimación conservadora basada en tablas nutricionales reconocidas.
            Incluí en nutrients solo los valores que puedas estimar con confianza; omití los inciertos. No inventes precisión.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiNutritionProperties properties;

    public GeminiNutritionClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
            AiNutritionProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AiNutritionResult analyze(byte[] image, String contentType, String context) {
        return nutritionResult(List.of(
                Map.of("text", nutritionPrompt(context)),
                inlineData(contentType, image)));
    }

    public AiNutritionResult refine(byte[] image, String contentType, String context, AiEstimateDraft currentEstimate,
            String correction) {
        return nutritionResult(List.of(
                Map.of("text", refinementPrompt(context, currentEstimate, correction)),
                inlineData(contentType, image)));
    }

    private AiNutritionResult nutritionResult(List<Map<String, Object>> parts) {
        try {
            JsonNode response = generateContent(parts, true);
            String text = responseText(response);
            if (text == null || text.isBlank()) throw unreadablePhoto();
            JsonNode result = objectMapper.readTree(stripCodeFence(text));
            List<AiNutritionItem> items = new ArrayList<>();
            for (JsonNode item : result.path("items")) {
                String name = item.path("name").asText("").trim();
                BigDecimal grams = decimal(item, "estimatedGrams");
                FoodCategory category = enumValue(item.path("category").asText(null), FoodCategory.class, FoodCategory.OTHER);
                FoodPreparation preparation = enumValue(item.path("preparation").asText(null), FoodPreparation.class, FoodPreparation.UNSPECIFIED);
                BigDecimal protein = decimal(item, "proteinGrams");
                BigDecimal carbs = decimal(item, "carbsGrams");
                BigDecimal fat = decimal(item, "fatGrams");
                Map<String, BigDecimal> nutrients = new HashMap<>();
                if (item.path("nutrients").isObject()) item.path("nutrients").fields().forEachRemaining(entry -> {
                    try {
                        BigDecimal value = new BigDecimal(entry.getValue().asText("0"));
                        if (value.signum() >= 0) nutrients.put(entry.getKey(), value.setScale(1, java.math.RoundingMode.HALF_UP));
                    } catch (NumberFormatException ignored) { }
                });
                if (name.length() >= 2 && grams.signum() > 0 && grams.compareTo(BigDecimal.valueOf(3000)) <= 0
                        && protein.compareTo(BigDecimal.valueOf(500)) <= 0 && carbs.compareTo(BigDecimal.valueOf(1000)) <= 0
                        && fat.compareTo(BigDecimal.valueOf(500)) <= 0) {
                    items.add(new AiNutritionItem(name.substring(0, Math.min(name.length(), 120)), grams, category, preparation, protein, carbs, fat, nutrients));
                }
            }
            if (items.isEmpty()) throw unreadablePhoto();
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
            int status = ex.getStatusCode().value();
            log.warn("Gemini meal analysis failed with upstream status {} ({})", status, upstreamReason(ex));
            if (status == 400) throw unreadablePhoto();
            if (status == 401 || status == 403) {
                throw new AiProviderException("AI_PROVIDER_CONFIGURATION", HttpStatus.SERVICE_UNAVAILABLE,
                        "Gemini no está disponible por una configuración del servicio. Intentá nuevamente más tarde.");
            }
            if (status == 404) {
                throw new AiProviderException("AI_PROVIDER_MODEL_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                        "El modelo de estimación no está disponible por el momento. Intentá nuevamente más tarde.");
            }
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini no está disponible por el momento. Intentá nuevamente en unos minutos.");
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Gemini meal analysis did not return a valid estimate: {}", ex.getClass().getSimpleName());
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini no está disponible por el momento. Intentá nuevamente en unos minutos.");
        }
    }

    public String transcribe(byte[] audio, String contentType) {
        try {
            String transcript = responseText(generateContent(List.of(
                    Map.of("text", TRANSCRIPTION_PROMPT),
                    inlineData(contentType, audio)), false));
            if (transcript == null || transcript.isBlank()) {
                throw new BadRequestException("No pudimos transcribir la nota. Intentá nuevamente.");
            }
            String normalized = transcript.replaceAll("\\s+", " ").trim();
            return normalized.substring(0, Math.min(normalized.length(), 240));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Gemini meal note transcription did not return text: {}", ex.getClass().getSimpleName());
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini no está disponible para transcribir la nota. Intentá nuevamente en unos minutos.");
        }
    }

    public Map<String, String> translateFoodNames(List<String> names) {
        if (names == null || names.isEmpty()) return Map.of();
        try {
            JsonNode response = generateContent(List.of(Map.of("text", TRANSLATION_PROMPT + "\nNombres: "
                    + objectMapper.writeValueAsString(names))), true);
            String text = responseText(response);
            if (text == null || text.isBlank()) return Map.of();
            JsonNode result = objectMapper.readTree(stripCodeFence(text));
            Map<String, String> translations = new HashMap<>();
            for (String name : names) {
                String translation = result.path(name).asText(null);
                if (translation != null && !translation.isBlank()) translations.put(name, translation.trim());
            }
            return translations;
        } catch (Exception ex) {
            log.warn("Gemini food name translation failed: {}", ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    public Optional<AiNutritionItem> estimateByName(String name, String category) {
        if (name == null || name.isBlank()) return Optional.empty();
        try {
            String prompt = NAME_ESTIMATE_PROMPT + "\nAlimento: \"" + name + "\""
                    + (category == null || category.isBlank() ? "" : " (categoría: " + category + ").");
            JsonNode response = generateContent(List.of(Map.of("text", prompt)), true);
            String text = responseText(response);
            if (text == null || text.isBlank()) return Optional.empty();
            JsonNode result = objectMapper.readTree(stripCodeFence(text));
            if (!result.path("calories").isNumber()) return Optional.empty();
            int calories = Math.max(0, result.path("calories").asInt(0));
            BigDecimal protein = decimal(result, "proteinGrams");
            BigDecimal carbs = decimal(result, "carbsGrams");
            BigDecimal fat = decimal(result, "fatGrams");
            if (calories == 0 && protein.signum() == 0 && carbs.signum() == 0 && fat.signum() == 0) {
                return Optional.empty();
            }
            Map<String, BigDecimal> nutrients = new HashMap<>();
            if (result.path("nutrients").isObject()) result.path("nutrients").fields().forEachRemaining(entry -> {
                try {
                    BigDecimal value = new BigDecimal(entry.getValue().asText("0"));
                    if (value.signum() >= 0) nutrients.put(entry.getKey(), value.setScale(1, RoundingMode.HALF_UP));
                } catch (NumberFormatException ignored) { }
            });
            int grams = Math.max((int) Math.round(100), 100);
            String itemName = result.path("name").asText("").trim();
            return Optional.of(new AiNutritionItem(
                    itemName.isBlank() ? name.substring(0, Math.min(name.length(), 120)) : itemName.substring(0, Math.min(itemName.length(), 120)),
                    BigDecimal.valueOf(grams),
                    FoodCategory.OTHER,
                    FoodPreparation.UNSPECIFIED,
                    protein, carbs, fat, nutrients));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) throw new AiQuotaExceededException(retryAt(ex));
            int status = ex.getStatusCode().value();
            log.warn("Gemini name estimate failed with upstream status {} ({})", status, upstreamReason(ex));
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Gemini name estimate failed for '{}': {}", name, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private JsonNode generateContent(List<Map<String, Object>> parts, boolean jsonResponse) {
        RestClientResponseException retryableFailure = null;
        for (String model : candidateModels()) {
            try {
                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("temperature", 0.2);
                if (jsonResponse) generationConfig.put("responseMimeType", "application/json");
                Map<String, Object> content = Map.of("parts", parts);
                Map<String, Object> body = Map.of("contents", List.of(content), "generationConfig", generationConfig);
                return restClient.post()
                        .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}", model, properties.getGeminiApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status != 404 && status != 500 && status != 503) throw ex;
                retryableFailure = ex;
                log.warn("Gemini model {} returned {} ({}); trying a compatible fallback", model, status, upstreamReason(ex));
            }
        }
        throw retryableFailure == null ? new IllegalStateException("No Gemini model configured") : retryableFailure;
    }

    private List<String> candidateModels() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add(properties.getModel());
        models.addAll(FALLBACK_MODELS);
        return models.stream().filter(model -> model != null && !model.isBlank()).toList();
    }

    private static Map<String, Object> inlineData(String contentType, byte[] content) {
        return Map.of("inlineData", Map.of("mimeType", contentType, "data", Base64.getEncoder().encodeToString(content)));
    }

    private static String nutritionPrompt(String context) {
        if (context == null || context.isBlank()) return PROMPT;
        return PROMPT + "\nContexto opcional declarado por la persona: \"" + context
                + "\". Usalo solo para identificar la comida; la foto sigue siendo la fuente principal.";
    }

    private String refinementPrompt(String context, AiEstimateDraft currentEstimate, String correction) {
        try {
            String draft = objectMapper.writeValueAsString(currentEstimate);
            String originalContext = context == null || context.isBlank() ? "(sin observación original)" : context;
            return REFINEMENT_PROMPT + "\nObservación original: \"" + originalContext + "\""
                    + "\nBorrador actual: " + draft
                    + "\nCorrección solicitada: \"" + correction + "\"";
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo preparar la corrección de la estimación.", ex);
        }
    }

    private static String responseText(JsonNode response) {
        return response == null ? null : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
    }

    private static AiProviderException unreadablePhoto() {
        return new AiProviderException("AI_IMAGE_UNREADABLE", HttpStatus.BAD_REQUEST,
                "No pudimos identificar alimentos en esta foto. Elegí una imagen más clara o probá con otra.");
    }

    private String upstreamReason(RestClientResponseException ex) {
        try {
            return objectMapper.readTree(ex.getResponseBodyAsString()).path("error").path("status").asText("unknown");
        } catch (Exception ignored) {
            return "unknown";
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

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
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

    public record AiNutritionItem(String name, BigDecimal estimatedGrams, FoodCategory category,
            FoodPreparation preparation, BigDecimal proteinGrams, BigDecimal carbsGrams, BigDecimal fatGrams,
            Map<String, BigDecimal> nutrients) {
    }
}
