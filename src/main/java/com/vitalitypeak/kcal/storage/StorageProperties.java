package com.vitalitypeak.kcal.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String publicBaseUrl,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        long maxImageSizeBytes) {
}
