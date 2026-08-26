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
                        "date", "2040-03-04",
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
        assertThat(calendar.getBody()).contains("2040-03-04", "\"completedCount\":1", "\"durationMinutes\":45");
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
