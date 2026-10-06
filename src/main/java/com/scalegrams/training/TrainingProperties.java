package com.scalegrams.training;

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
}
