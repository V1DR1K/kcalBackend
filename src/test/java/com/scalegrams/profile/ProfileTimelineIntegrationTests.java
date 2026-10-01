package com.scalegrams.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.scalegrams.auth.CentralAuthClient;
import com.scalegrams.auth.CentralJwtService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProfileTimelineIntegrationTests extends com.scalegrams.PostgresTestSupport {
    @Autowired TestRestTemplate rest;
    @MockitoBean CentralAuthClient auth;
    @MockitoBean CentralJwtService jwt;
    @BeforeEach void authenticate() {
        when(auth.login(anyString(), anyString())).thenAnswer(call -> {
            String name = call.getArgument(0); String token = "timeline-token-" + name;
            return new CentralAuthClient.TokenResponse(token, "refresh-" + name, "Bearer",
                    new CentralAuthClient.CentralUser(UUID.nameUUIDFromBytes(token.getBytes()), name, false));
        });
        when(jwt.subject(anyString())).thenAnswer(call -> UUID.nameUUIDFromBytes(call.getArgument(0, String.class).getBytes()));
    }
    HttpHeaders headers(String name) {
        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login", Map.of("username", name, "password", "fixture-password"), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        HttpHeaders headers = new HttpHeaders(); headers.add(HttpHeaders.COOKIE, login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .map(cookie -> cookie.substring(0, cookie.indexOf(';'))).collect(Collectors.joining("; "))); return headers;
    }
    Map create(HttpHeaders headers, String name, LocalDate date, boolean alternative) {
        java.util.HashMap<String,Object> payload = new java.util.HashMap<>(Map.of("name", name, "dailyCalories", alternative ? 1800 : 2500,
                "proteinPercent", 25, "carbsPercent", 50, "fatPercent", 25, "startDate", date.toString()));
        if (alternative) payload.put("status", "ALTERNATIVE");
        ResponseEntity<Map> response = rest.postForEntity("/api/profile/nutrition-plans", new HttpEntity<>(payload, headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("create: %s", response.getBody()).isTrue(); return response.getBody();
    }
    Map preview(HttpHeaders headers, Map plan, String action) {
        ResponseEntity<Map> response = rest.postForEntity("/api/profile/nutrition-plans/" + plan.get("id") + "/" + action + "-preview",
                new HttpEntity<>(Map.of("version", plan.get("version")), headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(); return response.getBody();
    }
    Map confirm(HttpHeaders headers, Map plan, String action) {
        Map preview = preview(headers, plan, action);
        ResponseEntity<Map> response = rest.postForEntity("/api/profile/nutrition-plans/" + plan.get("id") + "/" + action,
                new HttpEntity<>(Map.of("version", plan.get("version"), "previewToken", preview.get("previewToken")), headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("confirm: %s", response.getBody()).isTrue(); return response.getBody();
    }
    @Test void savesAlternativesWithoutChangingGoalsAndCancelsWithoutOverwritingDates() {
        HttpHeaders headers = headers("timeline-" + UUID.randomUUID()); LocalDate today = LocalDate.now();
        Map initial = create(headers, "Plan actual", today.minusDays(10), false);
        Map alternative = create(headers, "Alternativa futura", today.plusDays(7), true);
        ResponseEntity<Map> active = rest.exchange("/api/profile/nutrition-plans/active?date=" + today, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(active.getBody().get("id")).isEqualTo(initial.get("id"));
        Map scheduled = confirm(headers, alternative, "schedule");
        Map second = create(headers, "Segunda alternativa", today.plusDays(14), true);
        Map scheduledSecond = confirm(headers, second, "schedule");
        ResponseEntity<List> listing = rest.exchange("/api/profile/nutrition-plans", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        Map preserved = (Map) listing.getBody().stream().filter(item -> ((Map) item).get("id").equals(initial.get("id"))).findFirst().orElseThrow();
        assertThat(preserved.get("endDate")).isNull();
        assertThat(preserved.get("effectiveEndDate")).isEqualTo(today.plusDays(6).toString());
        confirm(headers, scheduled, "cancel"); confirm(headers, scheduledSecond, "cancel");
        active = rest.exchange("/api/profile/nutrition-plans/active?date=" + today.plusDays(20), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(active.getBody().get("id")).isEqualTo(initial.get("id"));
    }
    @Test void rejectsStalePreviewsAndAnotherUsersPlan() {
        HttpHeaders owner = headers("preview-" + UUID.randomUUID());
        Map plan = create(owner, "Alternativa uno", LocalDate.now().plusDays(7), true);
        Map preview = preview(owner, plan, "schedule");
        create(owner, "Alternativa dos", LocalDate.now().plusDays(10), true);
        ResponseEntity<Map> stale = rest.postForEntity("/api/profile/nutrition-plans/" + plan.get("id") + "/schedule",
                new HttpEntity<>(Map.of("version", plan.get("version"), "previewToken", preview.get("previewToken")), owner), Map.class);
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        ResponseEntity<Map> denied = rest.postForEntity("/api/profile/nutrition-plans/" + plan.get("id") + "/schedule-preview",
                new HttpEntity<>(Map.of("version", plan.get("version")), headers("other-" + UUID.randomUUID())), Map.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
    }
}
