package com.scalegrams.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.scalegrams.auth.CentralAuthClient;
import com.scalegrams.auth.CentralJwtService;
import com.scalegrams.auth.CentralJwtTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrainingControllerIntegrationTests extends com.scalegrams.PostgresTestSupport {
    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    CentralAuthClient centralAuth;

    @MockitoBean
    CentralJwtService centralJwt;

    @BeforeEach
    void resetMocks() {
        rest.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        reset(centralAuth, centralJwt);
        when(centralAuth.login(anyString(), anyString()))
                .thenAnswer(invocation -> centralToken(invocation.getArgument(0, String.class)));
        CentralJwtTestSupport.stubIdentity(centralJwt);
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
    void dashboardReturnsMostRecentCompletedSessionRegardlessOfRequestedDate() {
        HttpHeaders headers = authHeaders("training-dashboard-recent");
        ResponseEntity<Map> created = rest.postForEntity("/api/training/sessions",
                new HttpEntity<>(Map.of("date", "2040-01-15", "module", "GYM"), headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> completed = rest.postForEntity(
                "/api/training/sessions/" + created.getBody().get("id") + "/complete",
                new HttpEntity<>(Map.of("version", created.getBody().get("version")), headers), Map.class);
        assertThat(completed.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> dashboard = rest.exchange("/api/training/dashboard?date=2040-03-20", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(dashboard.getBody().get("recentSession").toString()).contains("2040-01-15");
    }

    @Test
    void dashboardUsesTheLastSevenCalendarDaysForTrainingTotals() {
        HttpHeaders headers = authHeaders("training-dashboard-window");
        postCompletedSession(headers, "2040-02-29");
        postCompletedSession(headers, "2040-03-01");
        postCompletedSession(headers, "2040-03-07");
        postCompletedSession(headers, "2040-03-08");

        ResponseEntity<Map> dashboard = rest.exchange("/api/training/dashboard?date=2040-03-07", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(dashboard.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> weeklySummary = (Map<?, ?>) dashboard.getBody().get("weeklySummary");
        assertThat(weeklySummary.get("sessionCount")).isEqualTo(2);
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
    void createsGlobalExercisesThatAreVisibleAndSelectableForOtherUsers() {
        HttpHeaders creatorHeaders = authHeaders("training-global-creator");
        ResponseEntity<Map> created = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Ejercicio global compartido", "module", "GYM", "global", true), creatorHeaders), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(created.getBody()).containsEntry("global", true).containsEntry("editable", false);

        HttpHeaders otherHeaders = authHeaders("training-global-consumer");
        ResponseEntity<Map> listed = rest.exchange(
                "/api/training/exercises?module=GYM&size=50&q=Ejercicio global compartido", HttpMethod.GET,
                new HttpEntity<>(otherHeaders), Map.class);
        assertThat(listed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(listed.getBody().get("items").toString()).contains("Ejercicio global compartido");

        ResponseEntity<Map> selectable = rest.exchange(
                "/api/training/exercises/" + created.getBody().get("id"), HttpMethod.GET,
                new HttpEntity<>(otherHeaders), Map.class);
        assertThat(selectable.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(selectable.getBody()).containsEntry("global", true);

        ResponseEntity<String> deniedUpdate = rest.exchange(
                "/api/training/exercises/" + created.getBody().get("id"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Ejercicio global editado", "module", "GYM"), otherHeaders), String.class);
        assertThat(deniedUpdate.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<Void> deniedDelete = rest.exchange(
                "/api/training/exercises/" + created.getBody().get("id"), HttpMethod.DELETE,
                new HttpEntity<>(otherHeaders), Void.class);
        assertThat(deniedDelete.getStatusCode().value()).isEqualTo(404);
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
        assertThat(session.getBody().get("status")).isEqualTo("IN_PROGRESS");
        assertThat(((Map<?, ?>) sessionExercises.get(0)).get("targetSets")).isNull();
        assertThat(((Map<?, ?>) sessionExercises.get(0)).get("sets")).isEqualTo(List.of());

        ResponseEntity<Map> completed = rest.postForEntity(
                "/api/training/sessions/" + session.getBody().get("id") + "/complete",
                new HttpEntity<>(Map.of("durationMinutes", 45, "version", session.getBody().get("version")), headers), Map.class);
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
        assertThat(((Map<?, ?>) snapshots.get(0)).get("targetWeightKg")).isNull();

        ResponseEntity<Map> completed = rest.postForEntity(
                "/api/training/sessions/" + session.getBody().get("id") + "/complete",
                new HttpEntity<>(Map.of("version", session.getBody().get("version")), headers), Map.class);
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

    @Test
    void allowsEmptyTargetsAndPersistsSessionStructureChangesOnCompletion() {
        HttpHeaders headers = authHeaders("training-session-structure");
        ResponseEntity<Map> firstExercise = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Press inicial", "module", "GYM"), headers), Map.class);
        ResponseEntity<Map> secondExercise = rest.postForEntity("/api/training/exercises",
                new HttpEntity<>(Map.of("name", "Remo adicional", "module", "GYM"), headers), Map.class);

        Map<String, Object> planRequest = Map.of(
                "name", "Plan sin objetivos",
                "module", "GYM",
                "frequencyMode", "DYNAMIC",
                "targetSessionsPerWeek", 1,
                "startDate", "2041-01-01",
                "days", List.of(Map.of("name", "Día único", "position", 0, "exercises", List.of(
                        Map.of("exerciseId", firstExercise.getBody().get("id"))))));
        ResponseEntity<Map> plan = rest.postForEntity("/api/training/plans",
                new HttpEntity<>(planRequest, headers), Map.class);
        Map<?, ?> planDay = (Map<?, ?>) ((List<?>) plan.getBody().get("days")).get(0);

        ResponseEntity<Map> created = rest.postForEntity("/api/training/sessions",
                new HttpEntity<>(Map.of("date", "2041-01-01", "module", "GYM",
                        "planId", plan.getBody().get("id"), "planDayId", planDay.get("id")), headers), Map.class);
        Map<?, ?> snapshot = (Map<?, ?>) ((List<?>) created.getBody().get("exercises")).get(0);
        assertThat(snapshot.get("targetRepetitions")).isNull();
        assertThat(snapshot.get("sets")).isEqualTo(List.of());

        Map<String, Object> update = Map.of(
                "date", "2041-01-01",
                "module", "GYM",
                "planId", plan.getBody().get("id"),
                "planDayId", planDay.get("id"),
                "status", "IN_PROGRESS",
                "version", created.getBody().get("version"),
                "exercises", List.of(
                        Map.of("id", snapshot.get("id"), "exerciseId", firstExercise.getBody().get("id"), "position", 0),
                        Map.of("exerciseId", secondExercise.getBody().get("id"), "position", 1)));
        ResponseEntity<Map> updated = rest.exchange("/api/training/sessions/" + created.getBody().get("id"),
                HttpMethod.PUT, new HttpEntity<>(update, headers), Map.class);
        assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> complete = Map.of("version", updated.getBody().get("version"),
                "persistPlanChanges", true);
        ResponseEntity<Map> completed = rest.postForEntity(
                "/api/training/sessions/" + created.getBody().get("id") + "/complete",
                new HttpEntity<>(complete, headers), Map.class);
        assertThat(completed.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> reloadedPlan = rest.exchange("/api/training/plans/" + plan.getBody().get("id"),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<?, ?> reloadedDay = (Map<?, ?>) ((List<?>) reloadedPlan.getBody().get("days")).get(0);
        assertThat((List<?>) reloadedDay.get("exercises")).hasSize(2);
    }

    @Test
    void persistsOwnsAndDeletesCardioRecords() {
        HttpHeaders headers = authHeaders("training-cardio-crud");
        ResponseEntity<Map> profile = rest.exchange("/api/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("heightCm", 180), headers), Map.class);
        assertThat(profile.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> request = Map.of(
                "recordedAt", "2024-01-10T08:00:00Z",
                "speedKmh", 8.93,
                "durationMinutes", 42,
                "inclined", true);

        ResponseEntity<Map> created = rest.postForEntity("/api/training/cardio",
                new HttpEntity<>(request, headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(created.getBody()).containsEntry("equipment", "TREADMILL")
                .containsEntry("durationMinutes", 42).containsEntry("inclined", true)
                .containsEntry("speedKmh", 8.93).containsEntry("estimatedSteps", 8368);

        ResponseEntity<Map> listed = rest.exchange("/api/training/cardio?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(listed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat((List<?>) listed.getBody().get("items")).hasSize(1);

        Map<String, Object> update = Map.of(
                "recordedAt", "2024-01-10T08:00:00Z",
                "speedKmh", 9.0,
                "durationMinutes", 50,
                "inclined", false);
        ResponseEntity<Map> updated = rest.exchange("/api/training/cardio/" + created.getBody().get("id"),
                HttpMethod.PUT, new HttpEntity<>(update, headers), Map.class);
        assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(updated.getBody()).containsEntry("durationMinutes", 50).containsEntry("inclined", false)
                .containsEntry("speedKmh", 9.0).containsEntry("estimatedSteps", 10040);

        ResponseEntity<String> denied = rest.exchange("/api/training/cardio/" + created.getBody().get("id"),
                HttpMethod.DELETE, new HttpEntity<>(authHeaders("training-cardio-other")), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<Void> deleted = rest.exchange("/api/training/cardio/" + created.getBody().get("id"),
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void resetsTreadmillSummaryAfterLatestServiceAndUsesStrictCutoff() {
        HttpHeaders headers = authHeaders("training-cardio-summary");
        ResponseEntity<Map> profile = rest.exchange("/api/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("heightCm", 180), headers), Map.class);
        assertThat(profile.getStatusCode().is2xxSuccessful()).isTrue();
        postCardio(headers, "2024-02-01T08:00:00Z", 800);
        postCardio(headers, "2024-02-02T08:00:00Z", 500);

        ResponseEntity<Map> beforeService = rest.exchange("/api/training/cardio/summary", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(beforeService.getBody()).containsEntry("thresholdMinutes", 1200)
                .containsEntry("totalDurationMinutes", 1300).containsEntry("totalEstimatedSteps", 13387)
                .containsEntry("remainingMinutes", 0)
                .containsEntry("due", true);

        ResponseEntity<Map> service = rest.postForEntity("/api/training/cardio/services",
                new HttpEntity<>(Map.of("equipment", "TREADMILL", "servicedAt", "2024-03-01T08:00:00Z",
                        "notes", "Correa ajustada"), headers), Map.class);
        assertThat(service.getStatusCode().is2xxSuccessful()).isTrue();

        postCardio(headers, "2024-03-01T08:00:00Z", 100);
        postCardio(headers, "2024-03-02T08:00:00Z", 300);
        ResponseEntity<Map> afterService = rest.exchange("/api/training/cardio/summary", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(afterService.getBody()).containsEntry("totalDurationMinutes", 300)
                .containsEntry("totalEstimatedSteps", 26774).containsEntry("remainingMinutes", 900)
                .containsEntry("due", false);
        assertThat(afterService.getBody().get("latestService").toString()).contains("Correa ajustada");
    }

    @Test
    void groupsWeeklyCardioByTheRequestedLocalTimezoneAndKeepsStepsAcrossService() {
        HttpHeaders headers = authHeaders("training-cardio-weekly");
        rest.exchange("/api/profile", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("heightCm", 180), headers), Map.class);
        postCardio(headers, "2024-02-01T03:00:00Z", 60);
        postCardio(headers, "2024-02-05T03:30:00Z", 60);
        postCardio(headers, "2024-02-12T02:30:00Z", 60);
        postCardio(headers, "2024-02-08T03:00:00Z", 60);

        ResponseEntity<Map> weekly = rest.exchange(
                "/api/training/cardio/weekly?date=2024-02-07&timeZone=America/Argentina/Buenos_Aires",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(weekly.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> weeklyBody = weekly.getBody();
        assertThat(weeklyBody.get("from")).isEqualTo("2024-02-01");
        assertThat(weeklyBody.get("to")).isEqualTo("2024-02-07");
        assertThat(weeklyBody.get("stepsAvailable")).isEqualTo(true);
        assertThat((List<?>) weeklyBody.get("days")).hasSize(7);
        assertThat(weeklyBody.get("totalEstimatedSteps").toString()).isEqualTo("13387");
        Map<?, ?> firstDay = (Map<?, ?>) ((List<?>) weeklyBody.get("days")).get(0);
        assertThat(firstDay.get("date")).isEqualTo("2024-02-01");
        assertThat(firstDay.get("sessionCount")).isEqualTo(1);
        Map<?, ?> today = (Map<?, ?>) ((List<?>) weeklyBody.get("days")).get(6);
        assertThat(today.get("date")).isEqualTo("2024-02-07");
        assertThat(today.get("sessionCount")).isEqualTo(0);
    }

    @Test
    void preservesSeriesIdentityAcrossRepeatedSavesAndCompletion() {
        HttpHeaders headers = authHeaders("training-persist-series");
        Map exercise = rest.postForEntity("/api/training/exercises", new HttpEntity<>(
                Map.of("name", "Press series persistentes", "module", "GYM"), headers), Map.class).getBody();
        ResponseEntity<Map> created = rest.postForEntity("/api/training/sessions", new HttpEntity<>(Map.of(
                "date", "2040-04-01", "module", "GYM", "exercises", List.of(Map.of(
                "exerciseId", exercise.get("id"), "sets", List.of(Map.of("setNumber", 1, "repetitions", 6, "weightKg", 20))))), headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        Map current = created.getBody();
        Map sessionExercise = (Map) ((List) current.get("exercises")).get(0);
        Object seriesId = ((Map) ((List) sessionExercise.get("sets")).get(0)).get("id");
        for (int repetitions : List.of(8, 10, 12)) {
            ResponseEntity<Map> updated = rest.exchange("/api/training/sessions/" + current.get("id"), HttpMethod.PUT,
                    new HttpEntity<>(Map.of("date", "2040-04-01", "module", "GYM", "status", "IN_PROGRESS",
                            "version", current.get("version"), "exercises", List.of(Map.of("id", sessionExercise.get("id"),
                            "exerciseId", exercise.get("id"), "sets", List.of(Map.of("setNumber", 1,
                            "repetitions", repetitions, "weightKg", 20, "completed", true))))), headers), Map.class);
            assertThat(updated.getStatusCode().is2xxSuccessful()).as("save %s: %s", repetitions, updated.getBody()).isTrue();
            current = updated.getBody();
            Map savedSet = (Map) ((List) ((Map) ((List) current.get("exercises")).get(0)).get("sets")).get(0);
            assertThat(savedSet.get("id")).isEqualTo(seriesId);
            assertThat(savedSet.get("repetitions")).isEqualTo(repetitions);
        }
        String endpoint = "/api/training/sessions/" + current.get("id") + "/complete";
        HttpEntity<?> completion = new HttpEntity<>(Map.of("version", current.get("version")), headers);
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> rest.postForEntity(endpoint, completion, Map.class));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> rest.postForEntity(endpoint, completion, Map.class));
        ResponseEntity<Map> completed = first.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join();
        ResponseEntity<Map> repeated = second.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).join();
        assertThat(completed.getStatusCode().is2xxSuccessful()).as("complete: %s", completed.getBody()).isTrue();
        assertThat(repeated.getStatusCode().is2xxSuccessful()).as("repeat complete: %s", repeated.getBody()).isTrue();
        assertThat(repeated.getBody().get("version")).isEqualTo(completed.getBody().get("version"));
        assertThat(repeated.getBody().get("finishedAt")).isEqualTo(completed.getBody().get("finishedAt"));
    }

    @Test
    void preservesPlanIdentityWhenEditingAvailabilityAndCopiesInactive() {
        HttpHeaders headers = authHeaders("training-stable-plan");
        Map exercise = rest.postForEntity("/api/training/exercises", new HttpEntity<>(
                Map.of("name", "Sentadilla estable", "module", "GYM"), headers), Map.class).getBody();
        Map payload = Map.of("name", "Plan estable", "module", "GYM", "frequencyMode", "FIXED",
                "targetSessionsPerWeek", 1, "startDate", "2040-04-02", "active", true,
                "days", List.of(Map.of("name", "Lunes", "dayOfWeek", "MONDAY", "exercises", List.of(Map.of("exerciseId", exercise.get("id"))))));
        ResponseEntity<Map> created = rest.postForEntity("/api/training/plans", new HttpEntity<>(payload, headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).as("create: %s", created.getBody()).isTrue();
        Map plan = created.getBody();
        Object dayId = ((Map) ((List) plan.get("days")).get(0)).get("id");
        Object itemId = ((Map) ((List) ((Map) ((List) plan.get("days")).get(0)).get("exercises")).get(0)).get("id");
        ResponseEntity<Map> edited = rest.exchange("/api/training/plans/" + plan.get("id"), HttpMethod.PUT, new HttpEntity<>(plan, headers), Map.class);
        assertThat(edited.getStatusCode().is2xxSuccessful()).as("edit: %s", edited.getBody()).isTrue();
        plan = edited.getBody();
        assertThat(((Map) ((List) plan.get("days")).get(0)).get("id")).isEqualTo(dayId);
        ResponseEntity<Map> archived = rest.exchange("/api/training/plans/" + plan.get("id") + "/availability", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", false, "version", plan.get("version")), headers), Map.class);
        assertThat(archived.getStatusCode().is2xxSuccessful()).isTrue();
        Map day = (Map) ((List) archived.getBody().get("days")).get(0);
        assertThat(day.get("id")).isEqualTo(dayId);
        assertThat(((Map) ((List) day.get("exercises")).get(0)).get("id")).isEqualTo(itemId);
        ResponseEntity<Map> duplicate = rest.postForEntity("/api/training/plans/" + plan.get("id") + "/duplicate",
                new HttpEntity<>(Map.of("name", "Copia estable"), headers), Map.class);
        assertThat(duplicate.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(duplicate.getBody().get("active")).isEqualTo(false);
    }

    private void postCompletedSession(HttpHeaders headers, String date) {
        ResponseEntity<Map> created = rest.postForEntity("/api/training/sessions",
                new HttpEntity<>(Map.of("date", date, "module", "GYM"), headers), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<Map> completed = rest.postForEntity(
                "/api/training/sessions/" + created.getBody().get("id") + "/complete",
                new HttpEntity<>(Map.of("version", created.getBody().get("version")), headers), Map.class);
        assertThat(completed.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private void postCardio(HttpHeaders headers, String recordedAt, int durationMinutes) {
        ResponseEntity<Map> response = rest.postForEntity("/api/training/cardio",
                new HttpEntity<>(Map.of("recordedAt", recordedAt, "distanceKm", 5, "durationMinutes", durationMinutes,
                        "inclined", false), headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private HttpHeaders authHeaders(String username) {
        ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
                new LoginRequest(username, "central-password"), LoginResponse.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .map(cookie -> cookie.substring(0, cookie.indexOf(';'))).collect(Collectors.joining("; ")));
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
    @Test void maintenanceHistoryEditsAnnulsAndChecksOwnershipVersionsAndDuplicates() {
        var headers = authHeaders("maintenance-" + UUID.randomUUID());
        for (var entry : List.of(Map.entry("2024-02-01T08:00:00Z", 30), Map.entry("2024-03-01T08:00:00Z", 60))) {
            var response = rest.postForEntity("/api/training/cardio", new HttpEntity<>(Map.of("equipment", "TREADMILL", "recordedAt", entry.getKey(), "durationMinutes", entry.getValue(), "speedKmh", 5), headers), Map.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).as("%s", response.getBody()).isTrue();
        }
        Map<String,Object> firstBody = Map.of("equipment","TREADMILL","servicedAt","2024-02-15T08:00:00Z","notes","Correa");
        var first = rest.postForEntity("/api/training/cardio/services", new HttpEntity<>(firstBody, headers), Map.class);
        assertThat(first.getStatusCode().is2xxSuccessful()).as("%s", first.getBody()).isTrue();
        assertThat(rest.postForEntity("/api/training/cardio/services", new HttpEntity<>(firstBody, headers), Map.class).getStatusCode().value()).isEqualTo(409);
        assertThat(rest.postForEntity("/api/training/cardio/services", new HttpEntity<>(Map.of("equipment","TREADMILL","servicedAt",java.time.OffsetDateTime.now().plusDays(1).toString()), headers), Map.class).getStatusCode().value()).isEqualTo(400);
        var second = rest.postForEntity("/api/training/cardio/services", new HttpEntity<>(Map.of("equipment","TREADMILL","servicedAt","2024-03-15T08:00:00Z"), headers), Map.class);
        assertThat(rest.exchange("/api/training/cardio/summary",HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody()).containsEntry("totalDurationMinutes",0);
        Object secondId = second.getBody().get("id");
        var updateBody = Map.of("equipment","TREADMILL","servicedAt","2024-01-15T08:00:00Z","notes","Fecha corregida","version",second.getBody().get("version"));
        var edited = rest.exchange("/api/training/cardio/services/"+secondId,HttpMethod.PUT,new HttpEntity<>(updateBody,headers),Map.class);
        assertThat(edited.getStatusCode().is2xxSuccessful()).as("%s",edited.getBody()).isTrue();
        assertThat(rest.exchange("/api/training/cardio/summary",HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody()).containsEntry("totalDurationMinutes",60);
        assertThat(rest.exchange("/api/training/cardio/services/"+secondId,HttpMethod.PUT,new HttpEntity<>(updateBody,headers),Map.class).getStatusCode().value()).isEqualTo(409);
        String annulFirst = "/api/training/cardio/services/"+first.getBody().get("id")+"/annul";
        assertThat(rest.postForEntity(annulFirst,new HttpEntity<>(Map.of("version",0),authHeaders("maintenance-other-"+UUID.randomUUID())),Map.class).getStatusCode().value()).isEqualTo(404);
        var annulled = rest.postForEntity(annulFirst,new HttpEntity<>(Map.of("version",first.getBody().get("version"),"reason","Error de fecha"),headers),Map.class);
        assertThat(annulled.getStatusCode().is2xxSuccessful()).isTrue();
        var repeated = rest.postForEntity(annulFirst,new HttpEntity<>(Map.of("version",first.getBody().get("version")),headers),Map.class);
        assertThat(repeated.getBody()).containsEntry("version",annulled.getBody().get("version"));
        assertThat(rest.exchange("/api/training/cardio/summary",HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody()).containsEntry("totalDurationMinutes",90);
        rest.postForEntity("/api/training/cardio/services/"+secondId+"/annul",new HttpEntity<>(Map.of("version",edited.getBody().get("version")),headers),Map.class);
        var history = rest.exchange("/api/training/cardio/services",HttpMethod.GET,new HttpEntity<>(headers),Map.class);
        assertThat((List<Map>)history.getBody().get("items")).hasSize(2).allMatch(item -> item.get("annulledAt") != null);
    }
}
