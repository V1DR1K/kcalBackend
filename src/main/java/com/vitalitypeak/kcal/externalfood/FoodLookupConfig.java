package com.vitalitypeak.kcal.externalfood;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(FoodLookupProperties.class)
public class FoodLookupConfig {
    @Bean
    RestTemplate foodLookupRestTemplate(RestTemplateBuilder builder, FoodLookupProperties properties) {
        return builder
                .connectTimeout(properties.timeout())
                .readTimeout(properties.timeout())
                .build();
    }
}
