package com.vitalitypeak.kcal.externalfood;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.food-lookup")
public record FoodLookupProperties(
        boolean enabled,
        Duration timeout,
        OpenFoodFacts openFoodFacts,
        Usda usda) {

    public record OpenFoodFacts(String baseUrl, String userAgent) {
    }

    public record Usda(String baseUrl, String apiKey) {
    }
}
