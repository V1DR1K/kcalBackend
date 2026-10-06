package com.scalegrams.nutrition;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.semantic-search")
@Getter
@Setter
public class SemanticSearchProperties {
    private boolean enabled = true;
    private String embeddingModel = "gemini-embedding-2";
    private int embeddingDimensions = 768;
    private double minimumSimilarity = 0.8;
    private int reindexBatchSize = 25;
    private long reindexDelayMs = 60_000;
}
