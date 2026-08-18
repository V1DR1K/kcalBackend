package com.scalegrams.externalfood;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;

import org.springframework.stereotype.Component;

@Component
public class UsdaFoodDataProvider implements ExternalFoodProvider {
    private final FoodLookupProperties properties;
    private final RestTemplate restTemplate;

    public UsdaFoodDataProvider(FoodLookupProperties properties, RestTemplate foodLookupRestTemplate) {
        this.properties = properties;
        this.restTemplate = foodLookupRestTemplate;
    }

    public Optional<ExternalFoodCandidate> searchOne(String query) {
        // Reserved for generic food search enrichment. Barcode lookup stays on Open Food Facts in v1.
        if (query == null || query.isBlank() || properties.usda().apiKey() == null || properties.usda().apiKey().isBlank()) {
            return Optional.empty();
        }
        try {
            String url = UriComponentsBuilder.fromUriString(properties.usda().baseUrl())
                    .path("/foods/search").queryParam("api_key", properties.usda().apiKey())
                    .queryParam("query", query).queryParam("pageSize", 1).build().toUriString();
            Map<String, Object> body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            if (body == null || !(body.get("foods") instanceof List<?> foods) || foods.isEmpty()) return Optional.empty();
            Object first = foods.get(0);
            if (!(first instanceof Map<?, ?> food)) return Optional.empty();
            String fdcId = text(food.get("fdcId"));
            return fdcId == null ? Optional.of(candidate(food)) : lookupByFdcId(fdcId).or(() -> Optional.of(candidate(food)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ExternalFoodCandidate> lookupByBarcode(String barcode) {
        return Optional.empty();
    }

    public Optional<ExternalFoodCandidate> lookupByFdcId(String fdcId) {
        if (fdcId == null || fdcId.isBlank() || properties.usda().apiKey() == null || properties.usda().apiKey().isBlank()) return Optional.empty();
        try {
            String url = UriComponentsBuilder.fromUriString(properties.usda().baseUrl()).path("/food/{id}")
                    .queryParam("api_key", properties.usda().apiKey()).buildAndExpand(fdcId).toUriString();
            Map<String, Object> body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
            return body == null ? Optional.empty() : Optional.of(candidate(body));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }

    @Override
    public List<ExternalFoodCandidate> searchByText(String query, int limit) {
        return searchOne(query).stream().limit(Math.max(1, limit)).toList();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/json");
        return headers;
    }

    private ExternalFoodCandidate candidate(Map<?, ?> food) {
        Map<String, BigDecimal> nutrients = new LinkedHashMap<>();
        if (food.get("foodNutrients") instanceof Iterable<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> nutrient)) continue;
                Object id = nutrient.get("nutrientId");
                Object amount = nutrient.get("value");
                if (id == null && nutrient.get("nutrient") instanceof Map<?, ?> meta) {
                    id = meta.get("id");
                    amount = nutrient.get("amount");
                }
                String code = code(String.valueOf(id));
                BigDecimal value = decimal(amount);
                if (code != null && value != null) nutrients.put(code, value);
            }
        }
        Integer calories = integer(nutrients.get("CALORIES"));
        BigDecimal protein = nutrients.getOrDefault("PROTEIN", BigDecimal.ZERO);
        BigDecimal carbs = nutrients.getOrDefault("CARBOHYDRATE", BigDecimal.ZERO);
        BigDecimal fat = nutrients.getOrDefault("FAT", BigDecimal.ZERO);
        String name = text(food.get("description"));
        return new ExternalFoodCandidate(name == null ? "Alimento USDA" : name, null, null,
                FoodCategory.OTHER, calories, protein, carbs, fat, FoodPreparation.UNSPECIFIED,
                "USDA FoodData Central", null, null, null, java.util.Set.of("USDA"), "USDA",
                text(food.get("fdcId")), nutrients);
    }

    private static String code(String id) {
        return switch (id) {
            case "1008" -> "CALORIES"; case "1003" -> "PROTEIN"; case "1005" -> "CARBOHYDRATE"; case "1004" -> "FAT";
            case "1079" -> "FIBER"; case "2000" -> "SUGARS_TOTAL"; case "1258" -> "SATURATED_FAT"; case "1257" -> "TRANS_FAT";
            case "1292" -> "MONOUNSATURATED_FAT"; case "1293" -> "POLYUNSATURATED_FAT"; case "1253" -> "CHOLESTEROL";
            case "1093" -> "SODIUM"; case "1092" -> "POTASSIUM"; case "1087" -> "CALCIUM"; case "1089" -> "IRON";
            case "1090" -> "MAGNESIUM"; case "1091" -> "PHOSPHORUS"; case "1095" -> "ZINC"; case "1098" -> "COPPER";
            case "1101" -> "MANGANESE"; case "1103" -> "SELENIUM"; case "1106" -> "VITAMIN_A"; case "1162" -> "VITAMIN_C";
            case "1114" -> "VITAMIN_D"; case "1109" -> "VITAMIN_E"; case "1185" -> "VITAMIN_K"; case "1165" -> "VITAMIN_B1";
            case "1166" -> "VITAMIN_B2"; case "1167" -> "VITAMIN_B3"; case "1170" -> "VITAMIN_B5"; case "1175" -> "VITAMIN_B6";
            case "1176" -> "VITAMIN_B7"; case "1177" -> "VITAMIN_B9"; case "1178" -> "VITAMIN_B12"; default -> null;
        };
    }

    private static String text(Object value) { return value == null || value.toString().isBlank() ? null : value.toString().trim(); }
    private static BigDecimal decimal(Object value) { try { return value == null ? null : new BigDecimal(value.toString()); } catch (NumberFormatException ex) { return null; } }
    private static Integer integer(BigDecimal value) { return value == null ? null : value.setScale(0, java.math.RoundingMode.HALF_UP).intValue(); }
}
