package com.scalegrams.training;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.training")
@Getter
@Setter
public class TrainingProperties {
    private String defaultTimeZone = "America/Argentina/Buenos_Aires";

    public ZoneId resolveTimeZone(String value) {
        ZoneId fallback = ZoneId.of(defaultTimeZone);
        if (value == null || value.isBlank()) return fallback;
        try {
            return ZoneId.of(value);
        } catch (DateTimeException ignored) {
            return fallback;
        }
    }
}
