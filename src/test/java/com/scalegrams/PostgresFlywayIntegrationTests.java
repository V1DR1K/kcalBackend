package com.scalegrams;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.scalegrams.nutrition.FoodLogRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate" })
class PostgresFlywayIntegrationTests extends PostgresTestSupport {
    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FoodLogRepository foodLogs;

    @Test
    void appliesAllPostgresMigrationsAndKeepsTheProductionConstraints() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("47");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'food' AND column_name = 'nutrition_fingerprint'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_food_active_ai_identity'", Integer.class))
                .isEqualTo(1);
        assertThat(foodLogs.findRecentMealGroupLogIds(-1L, LocalDate.now().minusDays(10), LocalDate.now(), 5)).isEmpty();
    }
}
