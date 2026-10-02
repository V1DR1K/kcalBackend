package com.scalegrams;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs the HTTP nutrition/profile/catalog roundtrips against the real production schema. */
class PostgresNutritionIntegrationTests extends ScaleGramsApplicationTests {
    @DynamicPropertySource
    static void requirePostgres(DynamicPropertyRegistry registry) {
        PostgresTestSupport.databaseProperties(registry);
    }
}
