package com.scalegrams.nutrition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import com.google.genai.Client;

@Configuration
@Conditional(GeminiEmbeddingConfiguration.EmbeddingConfigured.class)
public class GeminiEmbeddingConfiguration {

    @Bean(destroyMethod = "close")
    Client geminiEmbeddingClient(AiNutritionProperties aiProperties) {
        return Client.builder().apiKey(aiProperties.getGeminiApiKey()).build();
    }

    @Bean
    GeminiSpringEmbeddingModel geminiSpringEmbeddingModel(Client client, SemanticSearchProperties properties) {
        return new GeminiSpringEmbeddingModel(client, properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions());
    }

    static class EmbeddingConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean enabled = context.getEnvironment().getProperty("app.semantic-search.enabled", Boolean.class, true);
            String apiKey = context.getEnvironment().getProperty("app.ai-nutrition.gemini-api-key");
            return enabled && StringUtils.hasText(apiKey);
        }
    }
}
