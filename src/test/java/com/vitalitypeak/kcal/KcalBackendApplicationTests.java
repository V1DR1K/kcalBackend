package com.vitalitypeak.kcal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

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

import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.externalfood.ExternalFoodCandidate;
import com.vitalitypeak.kcal.externalfood.ExternalFoodLookupService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KcalBackendApplicationTests {
	@Autowired
	TestRestTemplate rest;

	@MockBean
	ExternalFoodLookupService externalFoodLookup;

	@BeforeEach
	void resetMocks() {
		reset(externalFoodLookup);
		when(externalFoodLookup.lookupByBarcode(anyString())).thenReturn(Optional.empty());
	}

	@Test
	void contextLoads() {
	}

	@Test
	void dashboardSerializesFoodTags() {
		ResponseEntity<String> dashboard = rest.exchange("/api/nutrition/dashboard", HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);

		assertThat(dashboard.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(dashboard.getBody()).contains("Desayuno", "Almuerzo", "Merienda", "Cena");
	}

	@Test
	void createsNutritionPlanAndDashboardUsesIt() {
		HttpHeaders headers = authHeaders();
		NutritionPlanRequest request = new NutritionPlanRequest("Plan test", 2500, 35, 50, 15, "2026-01-01", null);
		ResponseEntity<String> created = rest.postForEntity("/api/profile/nutrition-plans", new HttpEntity<>(request, headers), String.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(created.getBody()).contains("\"dailyCalories\":2500", "\"proteinGoalGrams\":219", "\"carbsGoalGrams\":313", "\"fatGoalGrams\":42");

		ResponseEntity<String> dashboard = rest.exchange("/api/nutrition/dashboard?date=2026-06-18", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(dashboard.getBody()).contains("\"calorieGoal\":2500", "\"name\":\"Plan test\"");
	}

	@Test
	void rejectsInvalidMacroSum() {
		NutritionPlanRequest request = new NutritionPlanRequest("Mal sumado", 2500, 35, 40, 15, "2026-01-01", null);
		ResponseEntity<String> response = rest.postForEntity("/api/profile/nutrition-plans",
				new HttpEntity<>(request, authHeaders()), String.class);
		assertThat(response.getStatusCode().is4xxClientError()).isTrue();
		assertThat(response.getBody()).contains("100%");
	}

	@Test
	void seedsAdminUser() {
		ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
				new LoginRequest("admin", "admin"), LoginResponse.class);

		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(login.getBody().token()).isNotBlank();
	}

	@Test
	void barcodeLookupUsesLocalFoodBeforeExternalProviders() {
		ResponseEntity<String> response = rest.exchange("/api/foods/barcode/7790000000059", HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).contains("Atun en lata");
		verify(externalFoodLookup, never()).lookupByBarcode(anyString());
	}

	@Test
	void barcodeLookupImportsOpenFoodFactsCandidateAndCachesIt() {
		String barcode = "7622210449283";
		when(externalFoodLookup.lookupByBarcode(barcode)).thenReturn(Optional.of(new ExternalFoodCandidate(
				"Oreo Original",
				"Milka",
				barcode,
				FoodCategory.OTHER,
				480,
				BigDecimal.valueOf(5.2),
				BigDecimal.valueOf(68.0),
				BigDecimal.valueOf(20.0),
				"https://images.openfoodfacts.org/front.jpg",
				Set.of("Open Food Facts", "biscuits"),
				"OPEN_FOOD_FACTS",
				barcode)));

		ResponseEntity<String> first = rest.exchange("/api/foods/barcode/" + barcode, HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);
		ResponseEntity<String> second = rest.exchange("/api/foods/barcode/" + barcode, HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);

		assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(first.getBody()).contains("Oreo Original", "\"barcode\":\"" + barcode + "\"", "\"source\":\"OPEN_FOOD_FACTS\"");
		assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(second.getBody()).contains("Oreo Original");
		verify(externalFoodLookup).lookupByBarcode(barcode);
	}

	private HttpHeaders authHeaders() {
		ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
				new LoginRequest("alex@kazadesarrollos.com", "password123"), LoginResponse.class);
		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().token());
		return headers;
	}

	record LoginRequest(String email, String password) {
	}

	record LoginResponse(String token) {
	}

	record NutritionPlanRequest(String name, Integer dailyCalories, Integer proteinPercent, Integer carbsPercent,
			Integer fatPercent, String startDate, String endDate) {
	}
}
