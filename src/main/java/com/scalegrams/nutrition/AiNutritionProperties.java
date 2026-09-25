package com.scalegrams.nutrition;

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
    private String model = "gemini-3.5-flash";
    private int maxImageBytes = 5_242_880;
    private int maxAudioBytes = 2_097_152;
    private int dailyLimit = 99;
    private boolean jevEnabled;
    private String jevApiKey;
    private String jevBaseUrl = "https://jev-agent.com";
    private String jevModel = "jev-latest";
    private String jevMode = "shadow";
}
