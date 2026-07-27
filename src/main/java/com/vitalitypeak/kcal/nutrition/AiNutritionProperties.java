package com.vitalitypeak.kcal.nutrition;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.ai-nutrition")
@Getter
@Setter
public class AiNutritionProperties {
    private boolean enabled;
    private String geminiApiKey;
    private String model = "gemini-2.5-flash";
    private int dailyLimit = 3;
    private int maxImageBytes = 5_242_880;
}
