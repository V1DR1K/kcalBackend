package com.vitalitypeak.kcal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;

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
import com.vitalitypeak.kcal.catalog.FoodPreparation;
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
	void returnsSafeClientErrorsForUnknownRoutesAndInvalidFilters() {
		ResponseEntity<String> missing = rest.getForEntity("/v3/api-docs/does-not-exist", String.class);
		ResponseEntity<String> invalidCategory = rest.exchange("/api/foods?category=INVALID", HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);

		assertThat(missing.getStatusCode().value()).isEqualTo(404);
		assertThat(missing.getBody()).contains("\"code\":\"NOT_FOUND\"").doesNotContain("Exception");
		assertThat(invalidCategory.getStatusCode().is4xxClientError()).isTrue();
		assertThat(invalidCategory.getStatusCode().is5xxServerError()).isFalse();
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
				new LoginRequest("admin@gmail.com", "admin"), LoginResponse.class);

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
				FoodPreparation.UNSPECIFIED,
				null,
				null,
				null,
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

	@Test
	void userCanDeleteOwnFoodLogAndUndoLatestWaterLog() {
		HttpHeaders headers = authHeaders();
		String date = "2030-01-15";
		Map<String, Object> meal = Map.of("itemType", "FOOD", "itemId", 1, "mealType", "LUNCH",
				"quantity", 100, "unit", "GRAM", "logDate", date);
		ResponseEntity<Map> created = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(meal, headers), Map.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		Object logId = created.getBody().get("id");

		ResponseEntity<Void> deleted = rest.exchange("/api/nutrition/food-logs/" + logId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();

		Map<String, Object> water = Map.of("liters", 0.5, "logDate", date);
		rest.postForEntity("/api/nutrition/water-logs", new HttpEntity<>(water, headers), Void.class);
		ResponseEntity<Void> undone = rest.exchange("/api/nutrition/water-logs/latest?date=" + date, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(undone.getStatusCode().is2xxSuccessful()).isTrue();

		ResponseEntity<String> dashboard = rest.exchange("/api/nutrition/dashboard?date=" + date, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(dashboard.getBody()).contains("\"waterConsumedLiters\":0");
	}

	@Test
	void foodCatalogIsPaginatedAndExposesNextPage() {
		ResponseEntity<String> first = rest.exchange("/api/foods?page=0&size=5", HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);
		ResponseEntity<String> filtered = rest.exchange("/api/foods?category=FRUIT&page=0&size=3", HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);

		assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(first.getBody()).contains("\"page\":0", "\"size\":5", "\"hasNext\":true");
		assertThat(filtered.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(filtered.getBody()).contains("\"size\":3", "\"totalElements\"");
	}

	@Test
	void catalogPaginationIsBoundedAndRecipesUseTheSameContract() {
		HttpHeaders headers = authHeaders();
		ResponseEntity<String> bounded = rest.exchange("/api/foods?page=-4&size=500", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		ResponseEntity<String> recipes = rest.exchange("/api/recipes?page=0&size=2", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(bounded.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(bounded.getBody()).contains("\"page\":0", "\"size\":50", "\"totalElements\"", "\"totalPages\"");
		assertThat(recipes.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(recipes.getBody()).contains("\"items\"", "\"page\":0", "\"size\":2", "\"hasNext\"");
	}

	@Test
	void authenticatedUsersCanContributePendingGlobalFoods() {
		Map<String, Object> food = Map.of("name", "Privado", "category", "OTHER", "baseUnit", "GRAM",
				"baseQuantity", 100, "calories", 100, "proteinGrams", 1, "carbsGrams", 1, "fatGrams", 1,
				"preparation", "UNSPECIFIED", "tags", Set.of());
		ResponseEntity<String> response = rest.postForEntity("/api/foods", new HttpEntity<>(food, authHeaders()), String.class);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).contains("\"moderationStatus\":\"PENDING\"", "\"createdById\"");
	}

	@Test
	void actuatorHealthIsPublicAndMetricsAreNotExposedInTestProfile() {
		assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void userCanEditFoodLogAndNutritionIsRecalculated() {
		HttpHeaders headers = authHeaders();
		String date = "2031-02-10";
		Map<String, Object> meal = Map.of("itemType", "FOOD", "itemId", 1, "mealType", "LUNCH",
				"quantity", 100, "unit", "GRAM", "logDate", date);
		ResponseEntity<Map> created = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(meal, headers), Map.class);
		Object logId = created.getBody().get("id");
		Map<String, Object> update = Map.of("mealType", "DINNER", "quantity", 200, "unit", "GRAM", "logDate", date);

		ResponseEntity<String> updated = rest.exchange("/api/nutrition/food-logs/" + logId, HttpMethod.PUT,
				new HttpEntity<>(update, headers), String.class);

		assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(updated.getBody()).contains("\"mealType\":\"DINNER\"", "\"quantity\":200", "\"calories\":313");
	}

	@Test
	void dashboardDoesNotDuplicateRecipeIngredientsWhenFoodsHaveTags() {
		HttpHeaders headers = authHeaders();
		String date = "2032-03-11";
		Map<String, Object> recipe = Map.of(
				"name", "Receta sin duplicados",
				"description", "",
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<Map> createdRecipe = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, headers), Map.class);
		assertThat(createdRecipe.getStatusCode().is2xxSuccessful()).isTrue();

		Map<String, Object> meal = Map.of("itemType", "RECIPE", "itemId", createdRecipe.getBody().get("id"),
				"mealType", "BREAKFAST", "quantity", 1, "unit", "PORTION", "logDate", date);
		ResponseEntity<Map> createdLog = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(meal, headers), Map.class);
		assertThat(createdLog.getStatusCode().is2xxSuccessful()).isTrue();

		ResponseEntity<String> dashboard = rest.exchange("/api/nutrition/dashboard?date=" + date, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(dashboard.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(countOccurrences(dashboard.getBody(), "\"quantity\":100.00")).isEqualTo(1);
	}

	private static int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
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
