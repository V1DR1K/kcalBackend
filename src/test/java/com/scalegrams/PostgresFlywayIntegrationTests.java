package com.scalegrams;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.UUID;

import com.scalegrams.nutrition.FoodLogRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate" })
class PostgresFlywayIntegrationTests extends PostgresTestSupport {
    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FoodLogRepository foodLogs;

    @Autowired
    DataSource dataSource;

    @Test
    void appliesAllPostgresMigrationsAndKeepsTheProductionConstraints() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("47");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'food' AND column_name = 'nutrition_fingerprint'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'uq_food_active_ai_identity'", Integer.class))
                .isEqualTo(1);
        assertThat(foodLogs.findRecentMealGroupLogIds(-1L, LocalDate.now().minusDays(10), LocalDate.now(), 5)).isEmpty();
    }

    @Test
    void upgradesVersion43WithoutChangingDeclaredDatesOrHistoricalNutrition() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway old = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("43").load();
        old.migrate();
        assertThat(old.info().current().getVersion().getVersion()).isEqualTo("43");
        try (var connection = dataSource.getConnection()) {
            String previousSchema = connection.getSchema();
            try {
            connection.setSchema(schema);
            JdbcTemplate legacy = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            Long userId = legacy.queryForObject("select min(id) from users", Long.class);
            legacy.update("""
                    insert into nutrition_plan(user_id,name,daily_calories,protein_percent,carbs_percent,fat_percent,
                    protein_goal_grams,carbs_goal_grams,fat_goal_grams,start_date,end_date,active)
                    values (?, 'V43 active',2000,30,40,30,150,200,67,'2026-01-01','2027-12-31',true),
                    (?, 'V43 archived',1800,30,40,30,135,180,60,'2025-01-01','2025-12-31',false)
                    """, userId, userId);
            Long logId = legacy.queryForObject("""
                    insert into food_log(user_id,item_type,meal_type,unit,quantity,log_date,calories,protein_grams,carbs_grams,fat_grams)
                    values (?,'AI_ESTIMATE','LUNCH','PORTION',1,'2026-01-01',417,20,null,10) returning id
                    """, Long.class, userId);
            legacy.update("insert into food_log_nutrient(food_log_id,nutrient_code,nutrient_value,source,status) values (?,'SODIUM',null,'LEGACY','MISSING'), (?,'IRON',2.5,'LEGACY','PARTIAL')", logId, logId);
            legacy.update("insert into water_log(user_id,log_date,liters) values (?,'2026-01-01',0.25)", userId);
            Flyway latest = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load();
            latest.migrate(); latest.validate();
            assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("47");
            assertThat(legacy.queryForObject("select status from nutrition_plan where name='V43 active'", String.class)).isEqualTo("SCHEDULED");
            assertThat(legacy.queryForObject("select status from nutrition_plan where name='V43 archived'", String.class)).isEqualTo("ARCHIVED");
            assertThat(legacy.queryForObject("select end_date from nutrition_plan where name='V43 active'", LocalDate.class)).isEqualTo(LocalDate.of(2027,12,31));
            assertThat(legacy.queryForObject("select calories from food_log where id=?", Integer.class, logId)).isEqualTo(417);
            assertThat(legacy.queryForObject("select carbs_grams from food_log where id=?", java.math.BigDecimal.class, logId)).isNull();
            assertThat(legacy.queryForObject("select complete from food_log_nutrient where food_log_id=? and nutrient_code='SODIUM'", Boolean.class, logId)).isFalse();
            assertThat(legacy.queryForObject("select known_value from food_log_nutrient where food_log_id=? and nutrient_code='IRON'", java.math.BigDecimal.class, logId)).isEqualByComparingTo("2.5");
            assertThat(legacy.queryForObject("select liters from water_log where user_id=?", java.math.BigDecimal.class, userId)).isEqualByComparingTo("0.25");
            } finally { connection.setSchema(previousSchema); }
        }
    }
}
