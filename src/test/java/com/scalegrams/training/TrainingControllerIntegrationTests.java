package com.scalegrams.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.scalegrams.auth.CentralAuthClient;
import com.scalegrams.auth.CentralJwtService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrainingControllerIntegrationTests {
    @Autowired
    TestRestTemplate rest;

    @MockBean
    CentralAuthClient centralAuth;

    @MockBean
    CentralJwtService centralJwt;

    @BeforeEach
    void resetMocks() {
        reset(centralAuth, centralJwt);
        when(centralAuth.login(anyString(), anyString()))
                .thenAnswer(invocation -> centralToken(invocation.getArgument(0, String.class)));
        when(centralJwt.subject(anyString()))
                .thenAnswer(invocation -> UUID.nameUUIDFromBytes(invocation.getArgument(0, String.class).getBytes()));
    }

    @Test
    void requiresAuthenticationAndPreventsAccessToAnotherUsersExercises() {
        assertThat(rest.getForEntity("/api/training/modules", String.class).getStatusCode().value()).isEqualTo(401);

        HttpHeaders alex = authHeaders("training-alex");
        ResponseEntity<Map> created = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Press privado", "module", "GYM"), alex), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> denied = rest.exchange("/api/training/exercises/" + created.getBody().get("id"),
                HttpMethod.PUT, new HttpEntity<>(Map.of("name", "Press ajeno", "module", "GYM"),
                        authHeaders("training-other")), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
        assertThat(denied.getBody()).contains("NOT_FOUND");
    }

    @Test
    void listsTrainingDataWithoutOptionalSearchParameters() {
        HttpHeaders headers = authHeaders("training-listing");

        ResponseEntity<Map> exercises = rest.exchange("/api/training/exercises?page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(exercises.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> dashboard = rest.exchange("/api/training/dashboard?date=2040-01-01", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(dashboard.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void rejectsWeightsForCalisthenicsSessions() {
        HttpHeaders headers = authHeaders("training-calisthenics");
        ResponseEntity<Map> exercise = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Dominadas", "module", "CALISTHENICS"), headers), Map.class);

        Map<String, Object> session = Map.of(
                "date", "2040-02-01",
                "module", "CALISTHENICS",
                "exercises", List.of(Map.of(
                        "exerciseId", exercise.getBody().get("id"),
                        "targetSets", 3,
                        "targetRepetitions", 8,
                        "targetWeightKg", 10,
                        "sets", List.of(Map.of("setNumber", 1, "repetitions", 8, "weightKg", 10)))));
        ResponseEntity<String> rejected = rest.postForEntity("/api/training/sessions", new HttpEntity<>(session, headers),
                String.class);

        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(rejected.getBody()).contains("BAD_REQUEST", "Calistenia");
    }

    @Test
    void exposesSystemCategoriesAndAllowsOnlyOwnedCategoryChanges() {
        HttpHeaders headers = authHeaders("training-categories");
        ResponseEntity<Map> listed = rest.exchange(
                "/api/training/categories?module=CALISTHENICS&page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(listed.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> system = ((List<Map<?, ?>>) listed.getBody().get("items")).stream()
                .filter(category -> "EMPUJE".equals(category.get("name"))).findFirst().orElseThrow();
        assertThat(system.get("system")).isEqualTo(true);
        assertThat(system.get("editable")).isEqualTo(false);

        ResponseEntity<String> protectedSystem = rest.exchange("/api/training/categories/" + system.get("id"),
                HttpMethod.PUT, new HttpEntity<>(Map.of("name", "Empuje editado", "module", "CALISTHENICS"), headers),
                String.class);
        assertThat(protectedSystem.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<Map> created = rest.postForEntity("/api/training/categories",
                new HttpEntity<>(Map.of("name", "Mi circuito", "module", "CALISTHENICS"), headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(created.getBody().get("editable")).isEqualTo(true);
        ResponseEntity<Void> deleted = rest.exchange("/api/training/categories/" + created.getBody().get("id"),
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void createsExercisesWithMetadataAndSupportsMetadataFilters() {
        HttpHeaders headers = authHeaders("training-exercise-filters");
        ResponseEntity<Map> categories = rest.exchange("/api/training/categories?module=GYM&size=50",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<?, ?> chest = ((List<Map<?, ?>>) categories.getBody().get("items")).stream()
                .filter(category -> "PECHO".equals(category.get("name"))).findFirst().orElseThrow();
        Map<String, Object> request = Map.of("name", "Press técnico", "module", "GYM",
                "categoryId", chest.get("id"), "code", "TEST_PRESS_TECNICO", "equipment", "BARBELL",
                "difficulty", "INTERMEDIATE", "registrationType", "WEIGHT_AND_REPETITIONS",
                "primaryMuscles", List.of("Pectorales"), "secondaryMuscles", List.of("Triceps"),
                "externalLoad", true);
        ResponseEntity<Map> created = rest.postForEntity("/api/training/exercises", new HttpEntity<>(request, headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(created.getBody()).containsEntry("code", "TEST_PRESS_TECNICO")
                .containsEntry("categoryId", chest.get("id"));

        ResponseEntity<Map> filtered = rest.exchange(
                "/api/training/exercises?module=GYM&equipment=BARBELL&difficulty=INTERMEDIATE&registrationType=WEIGHT_AND_REPETITIONS&q=TEST_PRESS_TECNICO",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(filtered.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(filtered.getBody().get("items").toString()).contains("TEST_PRESS_TECNICO");
    }

    @Test
    void recordsTimeTargetsAndSecondsWithoutWeight() {
        HttpHeaders headers = authHeaders("training-time");
        ResponseEntity<Map> exercise = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Sostén personalizado", "module", "CALISTHENICS",
                        "registrationType", "TIME"), headers), Map.class);
        Map<String, Object> session = Map.of("date", "2040-04-01", "module", "CALISTHENICS", "exercises",
                List.of(Map.of("exerciseId", exercise.getBody().get("id"), "targetSets", 2, "targetSeconds", 30,
                        "sets", List.of(Map.of("setNumber", 1, "seconds", 30)))));
        ResponseEntity<Map> created = rest.postForEntity("/api/training/sessions",
                new HttpEntity<>(session, headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> sessionExercise = (Map<?, ?>) ((List<?>) created.getBody().get("exercises")).get(0);
        assertThat(sessionExercise.get("registrationType")).isEqualTo("TIME");
        assertThat(sessionExercise.get("targetSeconds")).isEqualTo(30);
    }

    @Test
    void snapshotsPresetDaysCompletesSessionsAndAggregatesTheCalendar() {
        HttpHeaders headers = authHeaders("training-snapshot");
        ResponseEntity<Map> exercise = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Press banca", "module", "GYM"), headers), Map.class);
        ResponseEntity<Map> preset = rest.postForEntity("/api/training/presets",
                new HttpEntity<>(Map.of("name", "Torso", "description", "Pecho", "module", "GYM"), headers), Map.class);
        Object presetId = preset.getBody().get("id");
        ResponseEntity<Map> monday = rest.postForEntity("/api/training/presets/" + presetId + "/days",
                new HttpEntity<>(Map.of("dayOfWeek", "MONDAY"), headers), Map.class);
        ResponseEntity<Map> wednesday = rest.postForEntity("/api/training/presets/" + presetId + "/days",
                new HttpEntity<>(Map.of("dayOfWeek", "WEDNESDAY"), headers), Map.class);
        assertThat(monday.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(wednesday.getStatusCode().is2xxSuccessful()).isTrue();
        Object mondayId = monday.getBody().get("id");
        Object wednesdayId = wednesday.getBody().get("id");

        ResponseEntity<List> reordered = rest.exchange("/api/training/presets/" + presetId + "/days/reorder",
                HttpMethod.PUT, new HttpEntity<>(Map.of("ids", List.of(wednesdayId, mondayId)), headers), List.class);
        assertThat(reordered.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((Map<?, ?>) reordered.getBody().get(0)).get("id")).isEqualTo(wednesdayId);

        ResponseEntity<Map> presetExercise = rest.postForEntity(
                "/api/training/presets/" + presetId + "/days/" + mondayId + "/exercises",
                new HttpEntity<>(Map.of(
                        "exerciseId", exercise.getBody().get("id"),
                        "targetSets", 4,
                        "targetRepetitions", 6,
                        "targetWeightKg", 70,
                        "notes", "Controlado"), headers), Map.class);
        assertThat(presetExercise.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> session = rest.postForEntity("/api/training/sessions",
                new HttpEntity<>(Map.of(
                         "date", "2040-03-05",
                        "module", "GYM",
                        "presetId", presetId,
                        "trainingDayId", mondayId,
                        "title", "Torso lunes"), headers), Map.class);
        assertThat(session.getStatusCode().is2xxSuccessful()).isTrue();
        List<?> sessionExercises = (List<?>) session.getBody().get("exercises");
        assertThat(sessionExercises).hasSize(1);
        assertThat(((Map<?, ?>) sessionExercises.get(0)).get("exerciseName")).isEqualTo("Press banca");
        assertThat(((Map<?, ?>) sessionExercises.get(0)).get("targetSets")).isEqualTo(4);

        ResponseEntity<Map> completed = rest.postForEntity(
                "/api/training/sessions/" + session.getBody().get("id") + "/complete",
                new HttpEntity<>(Map.of("durationMinutes", 45), headers), Map.class);
        assertThat(completed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");

        ResponseEntity<String> calendar = rest.exchange("/api/training/calendar?from=2040-03-01&to=2040-03-10",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(calendar.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(calendar.getBody()).contains("2040-03-05", "\"completedCount\":1", "\"durationMinutes\":45");
    }

    @Test
    void createsNestedDynamicPlanAndAdvancesOnlyAfterCompletionOrSkip() {
        HttpHeaders headers = authHeaders("training-dynamic");
        ResponseEntity<Map> exercise = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Sentadilla", "module", "GYM"), headers), Map.class);
        Object exerciseId = exercise.getBody().get("id");

        Map<String, Object> exerciseTarget = Map.of("exerciseId", exerciseId, "targetSets", 3,
                "targetRepetitions", 8, "targetWeightKg", 60);
        Map<String, Object> planRequest = Map.of(
                "name", "Fuerza dinámica",
                "description", "Secuencia semanal",
                "module", "GYM",
                "frequencyMode", "DYNAMIC",
                "targetSessionsPerWeek", 2,
                "startDate", "2040-01-01",
                "days", List.of(
                        Map.of("name", "Día A", "position", 0, "exercises", List.of(exerciseTarget)),
                        Map.of("name", "Día B", "position", 1, "exercises", List.of(exerciseTarget))));
        ResponseEntity<Map> plan = rest.postForEntity("/api/training/plans",
                new HttpEntity<>(planRequest, headers), Map.class);
        assertThat(plan.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(plan.getBody().get("frequencyMode")).isEqualTo("DYNAMIC");
        List<?> planDays = (List<?>) plan.getBody().get("days");
        Object firstDayId = ((Map<?, ?>) planDays.get(0)).get("id");
        Object secondDayId = ((Map<?, ?>) planDays.get(1)).get("id");

        Map<String, Object> sessionRequest = Map.of("date", "2040-01-01", "module", "GYM",
                "planId", plan.getBody().get("id"), "planDayId", firstDayId);
        ResponseEntity<Map> session = rest.postForEntity("/api/training/sessions",
                new HttpEntity<>(sessionRequest, headers), Map.class);
        assertThat(session.getStatusCode().is2xxSuccessful()).isTrue();
        List<?> snapshots = (List<?>) session.getBody().get("exercises");
        assertThat(((Map<?, ?>) snapshots.get(0)).get("targetWeightKg")).isEqualTo(60.0);

        ResponseEntity<Map> completed = rest.postForEntity(
                "/api/training/sessions/" + session.getBody().get("id") + "/complete",
                new HttpEntity<>(Map.of(), headers), Map.class);
        assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");

        ResponseEntity<Map> next = rest.exchange(
                "/api/training/plans/" + plan.getBody().get("id") + "/resolve?date=2040-01-02",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(next.getBody().get("planDayId")).isEqualTo(secondDayId);

        ResponseEntity<Map> skipped = rest.postForEntity(
                "/api/training/plans/" + plan.getBody().get("id") + "/skip",
                new HttpEntity<>(Map.of("date", "2040-01-02", "planDayId", secondDayId), headers), Map.class);
        assertThat(skipped.getBody().get("status")).isEqualTo("SKIPPED");

        ResponseEntity<Map> continued = rest.exchange(
                "/api/training/plans/" + plan.getBody().get("id") + "/resolve?date=2040-01-03",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(continued.getBody().get("planDayId")).isEqualTo(firstDayId);
    }

    private HttpHeaders authHeaders(String username) {
        ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
                new LoginRequest(username, "central-password"), LoginResponse.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        return headers;
    }

    private CentralAuthClient.TokenResponse centralToken(String username) {
        String token = "central-token-" + username;
        return new CentralAuthClient.TokenResponse(token, "central-refresh-" + username, "Bearer",
                new CentralAuthClient.CentralUser(UUID.nameUUIDFromBytes(token.getBytes()), username, false));
    }

    record LoginRequest(String username, String password) {
    }

    record LoginResponse(String accessToken) {
    }
}
