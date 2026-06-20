package com.vitalitypeak.kcal.externalfood;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.vitalitypeak.kcal.catalog.FoodCategory;

@Component
public class OpenFoodFactsProvider implements ExternalFoodProvider {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final FoodLookupProperties properties;

    public OpenFoodFactsProvider(RestTemplate foodLookupRestTemplate, FoodLookupProperties properties) {
        this.restTemplate = foodLookupRestTemplate;
        this.properties = properties;
    }

    @Override
    public Optional<ExternalFoodCandidate> lookupByBarcode(String barcode) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(properties.openFoodFacts().baseUrl())
                    .path("/api/v2/product/{barcode}.json")
                    .queryParam("fields", "status,product_name,generic_name,brands,categories_tags,image_front_url,nutriments")
                    .buildAndExpand(barcode)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, properties.openFoodFacts().userAgent());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), MAP_TYPE);
            return parse(barcode, response.getBody());
        } catch (RestClientException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<ExternalFoodCandidate> parse(String barcode, Map<String, Object> body) {
        if (body == null || number(body.get("status")).intValue() != 1) {
            return Optional.empty();
        }
        Object productValue = body.get("product");
        if (!(productValue instanceof Map<?, ?> product)) {
            return Optional.empty();
        }

        String name = firstText(product.get("product_name"), product.get("generic_name"));
        Map<String, Object> nutriments = product.get("nutriments") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Integer calories = integer(nutriments.get("energy-kcal_100g"));
        BigDecimal protein = decimal(nutriments.get("proteins_100g"));
        BigDecimal carbs = decimal(nutriments.get("carbohydrates_100g"));
        BigDecimal fat = decimal(nutriments.get("fat_100g"));
        if (name == null || calories == null || protein == null || carbs == null || fat == null) {
            return Optional.empty();
        }

        Set<String> tags = new LinkedHashSet<>();
        tags.add("Open Food Facts");
        if (product.get("categories_tags") instanceof Iterable<?> categories) {
            for (Object category : categories) {
                String tag = text(category);
                if (tag != null && tags.size() < 10) {
                    tags.add(tag.replace("en:", "").replace("-", " "));
                }
            }
        }

        return Optional.of(new ExternalFoodCandidate(
                name,
                text(product.get("brands")),
                barcode,
                FoodCategory.OTHER,
                calories,
                protein,
                carbs,
                fat,
                text(product.get("image_front_url")),
                tags,
                "OPEN_FOOD_FACTS",
                barcode));
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null) return text;
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private static Number number(Object value) {
        if (value instanceof Number number) return number;
        String text = text(value);
        if (text == null) return 0;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static Integer integer(Object value) {
        BigDecimal decimal = decimal(value);
        return decimal == null ? null : decimal.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = text(value);
        if (text == null) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
