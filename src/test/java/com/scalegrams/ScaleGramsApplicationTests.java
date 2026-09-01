package com.scalegrams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.auth.CentralAuthClient;
import com.scalegrams.auth.CentralJwtService;
import com.scalegrams.externalfood.ExternalFoodCandidate;
import com.scalegrams.externalfood.ExternalFoodLookupService;
import com.scalegrams.nutrition.FoodLog;
import com.scalegrams.nutrition.FoodLogRepository;
import com.scalegrams.nutrition.MealItemType;
import com.scalegrams.nutrition.MealType;
import com.scalegrams.profile.ProfileDtos.NutritionPlanResponse;
import com.scalegrams.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScaleGramsApplicationTests {
	@Autowired
	TestRestTemplate rest;

	@Autowired
	ObjectMapper objectMapper;

	@MockBean
	ExternalFoodLookupService externalFoodLookup;

	@MockBean
	CentralAuthClient centralAuth;

	@MockBean
	CentralJwtService centralJwt;

	@Autowired
	FoodLogRepository foodLogs;

	@Autowired
	FoodRepository foods;

	@Autowired
	UserRepository users;

	@BeforeEach
	void resetMocks() {
		reset(externalFoodLookup);
		when(externalFoodLookup.lookupByBarcode(anyString())).thenReturn(Optional.empty());
		when(centralAuth.login(anyString(), anyString())).thenAnswer(invocation -> centralToken(invocation.getArgument(0, String.class)));
		when(centralAuth.refresh(anyString())).thenReturn(centralToken("alex"));
		when(centralJwt.subject(anyString())).thenAnswer(invocation -> UUID.nameUUIDFromBytes(invocation.getArgument(0, String.class).getBytes()));
	}

	private CentralAuthClient.TokenResponse centralToken(String username) {
		String token = "central-token-" + username;
		return new CentralAuthClient.TokenResponse(token, "central-refresh-" + username, "Bearer",
				new CentralAuthClient.CentralUser(UUID.nameUUIDFromBytes(token.getBytes()), username, false));
	}

	@Test
	void contextLoads() {
	}

	@Test
	void centralSessionUsesHttpOnlyCookiesAndCanBeRestored() {
		ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
				new LoginRequest("alex", "central-password"), LoginResponse.class);

		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(login.getBody().accessToken()).isNull();
		assertThat(login.getBody().refreshToken()).isNull();
		assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2)
				.allMatch(cookie -> cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict"));

		ResponseEntity<UserSummary> session = rest.exchange("/api/auth/me", HttpMethod.GET,
				new HttpEntity<>(cookieHeaders(login)), UserSummary.class);
		assertThat(session.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(session.getBody().id()).isNotNull();
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
	void updatingCurrentNutritionPlanRefreshesProfileAndDashboardValues() {
		HttpHeaders headers = authHeaders();
		String date = java.time.LocalDate.now().toString();
		ResponseEntity<NutritionPlanResponse> created = rest.postForEntity("/api/profile/nutrition-plans",
				new HttpEntity<>(new NutritionPlanRequest("Plan vigente", 2200, 25, 50, 25, date, null), headers), NutritionPlanResponse.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();

		ResponseEntity<String> updated = rest.exchange("/api/profile/nutrition-plans/" + created.getBody().id(), HttpMethod.PUT,
				new HttpEntity<>(new NutritionPlanRequest("Plan vigente ajustado", 2400, 30, 45, 25, date, null), headers), String.class);
		assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();

		ResponseEntity<String> profile = rest.exchange("/api/profile", HttpMethod.GET, new HttpEntity<>(headers), String.class);
		ResponseEntity<String> dashboard = rest.exchange("/api/nutrition/dashboard?date=" + date, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(profile.getBody()).contains("\"dailyCalorieGoal\":2400", "\"proteinGoalGrams\":180", "\"carbsGoalGrams\":270");
		assertThat(dashboard.getBody()).contains("\"calorieGoal\":2400", "\"name\":\"Plan vigente ajustado\"");
	}

	@Test
	void creatingPlanOnSameDayReplacesActivePlan() {
		HttpHeaders headers = authHeaders();
		NutritionPlanRequest first = new NutritionPlanRequest("Plan A", 2200, 25, 50, 25, "2026-09-01", null);
		ResponseEntity<String> firstResponse = rest.postForEntity("/api/profile/nutrition-plans",
				new HttpEntity<>(first, headers), String.class);
		assertThat(firstResponse.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(firstResponse.getBody()).contains("\"name\":\"Plan A\"");

		NutritionPlanRequest second = new NutritionPlanRequest("Plan B", 1800, 40, 30, 30, "2026-09-01", null);
		ResponseEntity<String> secondResponse = rest.postForEntity("/api/profile/nutrition-plans",
				new HttpEntity<>(second, headers), String.class);
		assertThat(secondResponse.getStatusCode().is2xxSuccessful()).isTrue();

		ResponseEntity<String> list = rest.exchange("/api/profile/nutrition-plans", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(list.getBody()).contains("\"name\":\"Plan B\"").doesNotContain("\"name\":\"Plan A\"");
	}

	@Test
	void deletingNutritionPlanDeactivatesAndHidesIt() {
		HttpHeaders headers = authHeaders();
		NutritionPlanRequest request = new NutritionPlanRequest("Plan para borrar", 1800, 30, 40, 30, "2026-12-01", null);
		ResponseEntity<NutritionPlanResponse> created = rest.postForEntity("/api/profile/nutrition-plans",
				new HttpEntity<>(request, headers), NutritionPlanResponse.class);

		ResponseEntity<Void> deleted = rest.exchange("/api/profile/nutrition-plans/" + created.getBody().id(), HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(deleted.getStatusCode().value()).isEqualTo(200);

		ResponseEntity<String> list = rest.exchange("/api/profile/nutrition-plans", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(list.getBody()).doesNotContain("Plan para borrar");
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
	void weightHistoryUpsertsAndDeletesAndProfileWeightCreatesTodaysEntry() {
		HttpHeaders headers = authHeaders();

		ResponseEntity<List> empty = rest.exchange("/api/profile/weight-entries", HttpMethod.GET,
				new HttpEntity<>(headers), List.class);
		assertThat(empty.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(empty.getBody()).isEmpty();

		ResponseEntity<Map> added = rest.postForEntity("/api/profile/weight-entries",
				new HttpEntity<>(Map.of("entryDate", "2033-01-05", "weightKg", 81.5), headers), Map.class);
		assertThat(added.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(added.getBody().get("weightKg")).hasToString("81.5");

		ResponseEntity<Map> upserted = rest.postForEntity("/api/profile/weight-entries",
				new HttpEntity<>(Map.of("entryDate", "2033-01-05", "weightKg", 81.3), headers), Map.class);
		assertThat(upserted.getStatusCode().is2xxSuccessful()).isTrue();

		ResponseEntity<List> list = rest.exchange("/api/profile/weight-entries", HttpMethod.GET,
				new HttpEntity<>(headers), List.class);
		assertThat(list.getBody()).hasSize(1);
		assertThat(list.getBody().toString()).contains("81.3");

		ResponseEntity<Map> patched = rest.exchange("/api/profile", HttpMethod.PATCH,
				new HttpEntity<>(Map.of("weightKg", 80.0), headers), Map.class);
		assertThat(patched.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(patched.getBody().get("weightKg")).hasToString("80.0");

		ResponseEntity<List> afterPatch = rest.exchange("/api/profile/weight-entries", HttpMethod.GET,
				new HttpEntity<>(headers), List.class);
		assertThat(afterPatch.getBody()).hasSize(2);

		ResponseEntity<String> deleted = rest.exchange("/api/profile/weight-entries/" + upserted.getBody().get("id"),
				HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
		assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
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
	void userCanDeleteAllLogsFromOneMealWithoutAffectingOtherMeals() {
		HttpHeaders headers = authHeaders();
		String date = "2030-01-16";
		Map<String, Object> lunch = Map.of("itemType", "FOOD", "itemId", 1, "mealType", "LUNCH",
				"quantity", 100, "unit", "GRAM", "logDate", date);
		Map<String, Object> dinner = Map.of("itemType", "FOOD", "itemId", 1, "mealType", "DINNER",
				"quantity", 100, "unit", "GRAM", "logDate", date);
		ResponseEntity<Map> firstLunch = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(lunch, headers), Map.class);
		ResponseEntity<Map> secondLunch = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(lunch, headers), Map.class);
		ResponseEntity<Map> dinnerLog = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(dinner, headers), Map.class);

		ResponseEntity<Void> deleted = rest.exchange("/api/nutrition/food-logs?mealType=LUNCH&date=" + date,
				HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

		assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(rest.exchange("/api/nutrition/food-logs/" + firstLunch.getBody().get("id"), HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class).getStatusCode().value()).isEqualTo(404);
		assertThat(rest.exchange("/api/nutrition/food-logs/" + secondLunch.getBody().get("id"), HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class).getStatusCode().value()).isEqualTo(404);
		assertThat(rest.exchange("/api/nutrition/food-logs/" + dinnerLog.getBody().get("id"), HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class).getStatusCode().is2xxSuccessful()).isTrue();
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
	void foodSearchIgnoresIncompleteQueriesWithoutCallingExternalProviders() {
		ResponseEntity<String> response = rest.exchange("/api/foods?q=a&page=0&size=20", HttpMethod.GET,
				new HttpEntity<>(authHeaders()), String.class);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).contains("\"page\":0", "\"totalElements\":0", "\"hasNext\":false");
		verifyNoInteractions(externalFoodLookup);
	}

	@Test
	void usersCanExploreAndCopyRecipesWithoutTakingOwnershipOfTheOriginal() {
		HttpHeaders alexHeaders = authHeaders("alex");
		HttpHeaders ownerHeaders = authHeaders("Recetas Compartidas");
		Long ownerId = users.findAll().stream().filter(user -> "Recetas Compartidas".equals(user.getFullName()))
				.findFirst().orElseThrow().getId();

		Map<String, Object> recipe = Map.of(
				"name", "Receta para compartir",
				"description", "Una receta comunitaria",
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<Map> created = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, ownerHeaders), Map.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		Object sharedId = created.getBody().get("id");

		ResponseEntity<String> authors = rest.exchange("/api/recipes/explore/users", HttpMethod.GET,
				new HttpEntity<>(alexHeaders), String.class);
		ResponseEntity<String> sharedRecipes = rest.exchange("/api/recipes/explore/users/" + ownerId + "?size=5",
				HttpMethod.GET, new HttpEntity<>(alexHeaders), String.class);
		ResponseEntity<Map> copied = rest.exchange("/api/recipes/" + sharedId + "/copy", HttpMethod.POST,
				new HttpEntity<>(alexHeaders), Map.class);

		assertThat(authors.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(authors.getBody()).contains("Recetas Compartidas");
		assertThat(sharedRecipes.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(sharedRecipes.getBody()).contains("Receta para compartir");
		assertThat(copied.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(copied.getBody().get("name")).isEqualTo("Receta para compartir");

		Object copiedId = copied.getBody().get("id");
		ResponseEntity<String> forbiddenDelete = rest.exchange("/api/recipes/" + copiedId, HttpMethod.DELETE,
				new HttpEntity<>(ownerHeaders), String.class);
		assertThat(forbiddenDelete.getStatusCode().value()).isEqualTo(400);
	}

	@Test
	void authenticatedUsersCanContributeApprovedGlobalFoods() {
		Map<String, Object> food = Map.of("name", "Privado", "category", "OTHER", "baseUnit", "GRAM",
				"baseQuantity", 100, "calories", 100, "proteinGrams", 1, "carbsGrams", 1, "fatGrams", 1,
				"preparation", "UNSPECIFIED", "tags", Set.of());
		ResponseEntity<String> response = rest.postForEntity("/api/foods", new HttpEntity<>(food, authHeaders()), String.class);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).contains("\"moderationStatus\":\"APPROVED\"", "\"createdById\"");
	}

	@Test
	void actuatorHealthIsPublicAndMetricsAreNotExposedInTestProfile() {
		assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void userCanEditFoodLogAndNutritionIsRecalculatedWithoutDuplicateSnapshots() {
		HttpHeaders headers = authHeaders();
		String date = "2031-02-10";
		Map<String, Object> meal = Map.of("itemType", "FOOD", "itemId", 1, "mealType", "LUNCH",
				"quantity", 100, "unit", "GRAM", "logDate", date);
		ResponseEntity<Map> created = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(meal, headers), Map.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		Object logId = created.getBody().get("id");
		Map<String, Object> update = Map.of("mealType", "DINNER", "quantity", 200, "unit", "GRAM", "logDate", date);

		ResponseEntity<String> updated = rest.exchange("/api/nutrition/food-logs/" + logId, HttpMethod.PUT,
				new HttpEntity<>(update, headers), String.class);

		assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(updated.getBody()).contains("\"mealType\":\"DINNER\"", "\"quantity\":200", "\"calories\":313");
	}

	@Test
	void rejectsNonNumericMealItemIdsAsValidationErrors() {
		HttpHeaders headers = authHeaders();
		Map<String, Object> meal = Map.of("itemType", "FOOD", "itemId", "DINNER:FOOD:43:10:1786537556471",
				"mealType", "DINNER", "quantity", 10, "unit", "GRAM", "logDate", "2031-02-12");

		ResponseEntity<String> response = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(meal, headers), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody()).contains("\"code\":\"VALIDATION_ERROR\"").doesNotContain("INVALID_JSON");
	}

	@Test
	void confirmsAiEstimateAsOneFoodLogPerSelectedFood() {
		HttpHeaders headers = authHeaders();
		Map<String, Object> estimate = Map.of(
				"mealType", "DINNER",
				"logDate", "2031-02-11",
				"items", List.of(
						Map.of("foodId", 1, "servedGrams", 150),
						Map.of("proposal", Map.of("name", "Hamburguesa de lentejas", "category", "LEGUME",
								"preparation", "COOKED", "proteinGrams", 20, "carbsGrams", 30, "fatGrams", 10),
								"servedGrams", 250)));

		ResponseEntity<String> response = rest.postForEntity("/api/nutrition/ai-estimates/confirm", new HttpEntity<>(estimate, headers), String.class);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).startsWith("[").contains("\"itemType\":\"FOOD\"", "\"quantity\":150",
				"\"quantity\":250", "\"unit\":\"GRAM\"", "\"proteinGrams\":50.0", "\"carbsGrams\":75.0",
				"\"fatGrams\":25.0", "\"calories\":725", "\"source\":\"AI_ESTIMATE\"",
				"\"sourceId\":\"food-log:", "\"moderationStatus\":\"APPROVED\"");
		assertThat(countOccurrences(response.getBody(), "\"itemType\":\"FOOD\"")).isEqualTo(2);
	}

	@Test
	void confirmAiEstimateValidatesReferencesAndRollsBackCatalogProposals() {
		HttpHeaders headers = authHeaders();
		Map<String, Object> proposal = Map.of("name", "No debe persistir", "category", "OTHER",
				"proteinGrams", 1, "carbsGrams", 2, "fatGrams", 3);
		Map<String, Object> request = Map.of("mealType", "LUNCH", "logDate", "2031-02-12", "items", List.of(
				Map.of("proposal", proposal, "servedGrams", 100),
				Map.of("foodId", 999999, "servedGrams", 100)));

		ResponseEntity<String> rollback = rest.postForEntity("/api/nutrition/ai-estimates/confirm", new HttpEntity<>(request, headers), String.class);
		ResponseEntity<String> ambiguous = rest.postForEntity("/api/nutrition/ai-estimates/confirm", new HttpEntity<>(Map.of(
				"mealType", "LUNCH", "items", List.of(Map.of("foodId", 1, "proposal", proposal, "servedGrams", 100))), headers), String.class);
		ResponseEntity<String> oversized = rest.postForEntity("/api/nutrition/ai-estimates/confirm", new HttpEntity<>(Map.of(
				"mealType", "LUNCH", "items", List.of(Map.of("foodId", 1, "servedGrams", 3001))), headers), String.class);

		assertThat(rollback.getStatusCode().value()).isEqualTo(404);
		assertThat(foods.findAll()).noneMatch(food -> food.getName().equals("No debe persistir"));
		assertThat(ambiguous.getStatusCode().is4xxClientError()).isTrue();
		assertThat(oversized.getStatusCode().is4xxClientError()).isTrue();
	}

	@Test
	void userCanEditHistoricalAiEstimateAndSaveOneItemAsApprovedGlobalFood() {
		HttpHeaders headers = authHeaders();
		FoodLog log = new FoodLog();
		log.setUser(users.findByAuthUserId(UUID.nameUUIDFromBytes("central-token-alex".getBytes())).orElseThrow());
		log.setItemType(MealItemType.AI_ESTIMATE);
		log.setMealType(MealType.DINNER);
		log.setLogDate(java.time.LocalDate.parse("2031-02-12"));
		log.setQuantity(BigDecimal.ONE);
		log.setUnit(FoodUnit.PORTION);
		log.setCalories(250);
		log.setProteinGrams(BigDecimal.valueOf(30));
		log.setCarbsGrams(BigDecimal.ZERO);
		log.setFatGrams(BigDecimal.valueOf(9));
		log.setAiEstimateName("Cena estimada");
		log.setAiEstimateConfidence(50);
		log.setAiEstimateDetails("{\"description\":\"legacy\",\"context\":\"legacy\",\"items\":[{\"name\":\"Pollo grillado\",\"estimatedGrams\":150,\"proteinGrams\":30,\"carbsGrams\":0,\"fatGrams\":9}]}");
		long id = foodLogs.save(log).getId();
		Map<String, Object> update = Map.of(
				"name", "Cena corregida", "description", "Sin salsa", "context", "casero", "confidence", 75,
				"assumptions", List.of("Peso cocido"), "mealType", "LUNCH", "logDate", "2031-02-13",
				"items", List.of(Map.of("name", "Pollo grillado", "estimatedGrams", 200, "proteinGrams", 40, "carbsGrams", 0, "fatGrams", 10)));

		ResponseEntity<String> updated = rest.exchange("/api/nutrition/food-logs/" + id + "/ai-estimate", HttpMethod.PUT,
				new HttpEntity<>(update, headers), String.class);
		String snapshot = foodLogs.findById(id).orElseThrow().getAiEstimateDetails();
		ResponseEntity<String> cataloged = rest.postForEntity("/api/nutrition/food-logs/" + id + "/ai-estimate/items/0/catalog",
				new HttpEntity<>(Map.of("category", "MEAT", "preparation", "COOKED", "tags", Set.of("casero")), headers), String.class);

		assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(updated.getBody()).contains("\"mealType\":\"LUNCH\"", "\"calories\":250", "\\\"assumptions\\\":[\\\"Peso cocido\\\"]");
		assertThat(cataloged.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(cataloged.getBody()).contains("\"baseQuantity\":100", "\"proteinGrams\":20.0", "\"fatGrams\":5.0",
				"\"source\":\"AI_ESTIMATE\"", "\"moderationStatus\":\"APPROVED\"");
		assertThat(foodLogs.findById(id).orElseThrow().getAiEstimateDetails()).isEqualTo(snapshot);
	}

	@Test
	void batchAcceptsFoodAndRecipeReferencesUsedByMealCopy() {
		HttpHeaders headers = authHeaders();
		Map<String, Object> recipe = Map.of(
				"name", "Receta para copiar",
				"description", "",
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<Map> createdRecipe = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, headers), Map.class);
		assertThat(createdRecipe.getStatusCode().is2xxSuccessful()).isTrue();

		Map<String, Object> request = Map.of("logs", List.of(
				Map.of("itemType", "FOOD", "itemId", 1, "mealType", "DINNER", "quantity", 100, "unit", "GRAM", "logDate", "2032-03-10"),
				Map.of("itemType", "RECIPE", "itemId", createdRecipe.getBody().get("id"), "mealType", "DINNER", "quantity", 1, "unit", "PORTION", "logDate", "2032-03-10")));
		ResponseEntity<String> response = rest.postForEntity("/api/nutrition/meal-logs/batch", new HttpEntity<>(request, headers), String.class);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).contains("\"itemType\":\"FOOD\"", "\"itemType\":\"RECIPE\"");
	}

	@Test
	void userCanLogicallyDeleteOwnFoodAndDeletedFoodsStayOutOfNewSelections() {
		HttpHeaders headers = authHeaders();
		Map<String, Object> food = Map.of("name", "Alimento lógico para borrar", "category", "OTHER", "baseUnit", "GRAM",
				"baseQuantity", 100, "calories", 100, "proteinGrams", 10, "carbsGrams", 10, "fatGrams", 0,
				"preparation", "UNSPECIFIED", "tags", Set.of());
		ResponseEntity<Map> created = rest.postForEntity("/api/foods", new HttpEntity<>(food, headers), Map.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		Object foodId = created.getBody().get("id");

		ResponseEntity<Void> deleted = rest.exchange("/api/foods/" + foodId, HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		ResponseEntity<String> deletedMine = rest.exchange("/api/foods/mine/deleted", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		ResponseEntity<String> forbiddenRestore = rest.exchange("/api/foods/" + foodId + "/restore", HttpMethod.POST,
				new HttpEntity<>(authHeaders("Recetas Compartidas")), String.class);
		ResponseEntity<String> mine = rest.exchange("/api/foods/mine", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		ResponseEntity<String> search = rest.exchange("/api/foods?q=lógico&page=0&size=20", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		Map<String, Object> recipe = Map.of("name", "No usa alimento borrado", "description", "",
				"ingredients", List.of(Map.of("foodId", foodId, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<String> newRecipe = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, headers), String.class);
		Map<String, Object> copiedDeletedFood = Map.of("logs", List.of(
				Map.of("itemType", "FOOD", "itemId", foodId, "mealType", "DINNER", "quantity", 100, "unit", "GRAM", "logDate", "2032-03-11")));
		ResponseEntity<String> copiedDeleted = rest.postForEntity("/api/nutrition/meal-logs/batch",
				new HttpEntity<>(copiedDeletedFood, headers), String.class);
		ResponseEntity<String> directNewLog = rest.postForEntity("/api/nutrition/meal-logs",
				new HttpEntity<>(Map.of("itemType", "FOOD", "itemId", foodId, "mealType", "DINNER", "quantity", 100,
						"unit", "GRAM", "logDate", "2032-03-12"), headers), String.class);

		assertThat(deleted.getStatusCode().value()).isEqualTo(204);
		assertThat(deletedMine.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(deletedMine.getBody()).contains("Alimento lógico para borrar");
		assertThat(forbiddenRestore.getStatusCode().value()).isEqualTo(400);
		assertThat(search.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(mine.getBody()).doesNotContain("Alimento lógico para borrar");
		assertThat(search.getBody()).doesNotContain("Alimento lógico para borrar");
		assertThat(newRecipe.getStatusCode().value()).isEqualTo(404);
		assertThat(copiedDeleted.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(copiedDeleted.getBody()).contains("\"itemType\":\"FOOD\"");
		assertThat(directNewLog.getStatusCode().value()).isEqualTo(404);
		assertThat(foods.findById(((Number) foodId).longValue()).orElseThrow().getDeletedAt()).isNotNull();

		ResponseEntity<String> restored = rest.exchange("/api/foods/" + foodId + "/restore", HttpMethod.POST,
				new HttpEntity<>(headers), String.class);
		ResponseEntity<String> restoredMine = rest.exchange("/api/foods/mine", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		ResponseEntity<String> restoredSearch = rest.exchange("/api/foods?q=lógico&page=0&size=20",
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
		ResponseEntity<String> deletedMineAfterRestore = rest.exchange("/api/foods/mine/deleted", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		assertThat(restored.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(restored.getBody()).contains("Alimento lógico para borrar");
		assertThat(restoredMine.getBody()).contains("Alimento lógico para borrar");
		assertThat(restoredSearch.getBody()).contains("Alimento lógico para borrar");
		assertThat(deletedMineAfterRestore.getBody()).doesNotContain("Alimento lógico para borrar");
		assertThat(foods.findById(((Number) foodId).longValue()).orElseThrow().getDeletedAt()).isNull();
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

	@Test
	void userCanAdjustRecipeIngredientsForOneDayWithoutChangingTheBaseRecipe() {
		HttpHeaders headers = authHeaders();
		String date = "2032-03-12";
		Map<String, Object> recipe = Map.of(
				"name", "Receta diaria",
				"description", "",
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<Map> createdRecipe = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, headers), Map.class);
		Object recipeId = createdRecipe.getBody().get("id");
		Map<String, Object> meal = Map.of("itemType", "RECIPE", "itemId", recipeId,
				"mealType", "LUNCH", "quantity", 1, "unit", "PORTION", "logDate", date);
		ResponseEntity<Map> createdLog = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(meal, headers), Map.class);
		Object logId = createdLog.getBody().get("id");
		Map<String, Object> otherMeal = Map.of("itemType", "RECIPE", "itemId", recipeId,
				"mealType", "DINNER", "quantity", 1, "unit", "PORTION", "logDate", date);
		ResponseEntity<Map> otherLog = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(otherMeal, headers), Map.class);

		Map<String, Object> adjustment = Map.of("ingredients", List.of(Map.of("foodId", 1, "quantity", 200, "unit", "GRAM")));
		ResponseEntity<String> updated = rest.exchange("/api/nutrition/food-logs/" + logId + "/recipe-ingredients", HttpMethod.PUT,
				new HttpEntity<>(adjustment, headers), String.class);
		ResponseEntity<String> baseRecipe = rest.exchange("/api/recipes/" + recipeId, HttpMethod.GET, new HttpEntity<>(headers), String.class);
		ResponseEntity<String> dashboard = rest.exchange("/api/nutrition/dashboard?date=" + date, HttpMethod.GET, new HttpEntity<>(headers), String.class);

		assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(otherLog.getBody().get("calories")).isEqualTo(156);
		assertThat(updated.getBody()).contains("\"recipeAdjusted\":true", "\"totalWeightGrams\":200.0");
		assertThat(baseRecipe.getBody()).contains("\"quantity\":100.00");
		assertThat(dashboard.getBody()).contains("\"recipeAdjusted\":true", "\"recipeAdjusted\":false");

		ResponseEntity<Void> reset = rest.exchange("/api/nutrition/food-logs/" + logId + "/recipe-ingredients", HttpMethod.DELETE,
				new HttpEntity<>(headers), Void.class);
		assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();
	}

	@Test
	void recipeCookedGramsUseTheCapturedCookedWeightAndRejectAdjustedIngredients() {
		HttpHeaders headers = authHeaders();
		Map<String, Object> recipe = Map.of("name", "Pollo al horno", "cookedTotalWeightGrams", 80,
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<Map> created = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, headers), Map.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(created.getBody().get("totalWeightGrams")).hasToString("100.0");
		assertThat(created.getBody().get("rawTotalWeightGrams")).hasToString("100.0");
		assertThat(created.getBody().get("cookedTotalWeightGrams")).hasToString("80.0");

		Map<String, Object> gramLog = Map.of("itemType", "RECIPE", "itemId", created.getBody().get("id"),
				"mealType", "LUNCH", "quantity", 40, "unit", "GRAM", "logDate", "2033-04-10");
		ResponseEntity<Map> logged = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(gramLog, headers), Map.class);
		assertThat(logged.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(logged.getBody().get("proteinGrams")).hasToString("15.5");
		assertThat(logged.getBody().get("recipeRawTotalWeightGrams")).hasToString("100.0");
		assertThat(logged.getBody().get("recipeCookedTotalWeightGrams")).hasToString("80.0");

		Map<String, Object> changedYield = Map.of("name", "Pollo al horno", "cookedTotalWeightGrams", 50,
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		rest.exchange("/api/recipes/" + created.getBody().get("id"), HttpMethod.PUT,
				new HttpEntity<>(changedYield, headers), String.class);
		ResponseEntity<String> clearedYield = rest.exchange("/api/recipes/" + created.getBody().get("id"), HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Pollo al horno", "clearCookedTotalWeight", true,
						"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM"))), headers), String.class);
		ResponseEntity<String> updatedLog = rest.exchange("/api/nutrition/food-logs/" + logged.getBody().get("id"), HttpMethod.PUT,
				new HttpEntity<>(Map.of("mealType", "DINNER", "quantity", 40, "unit", "GRAM", "logDate", "2033-04-10"), headers), String.class);
		ResponseEntity<String> adjusted = rest.exchange("/api/nutrition/food-logs/" + logged.getBody().get("id") + "/recipe-ingredients",
				HttpMethod.PUT, new HttpEntity<>(Map.of("ingredients", List.of(Map.of("foodId", 1, "quantity", 120, "unit", "GRAM"))), headers), String.class);

		assertThat(updatedLog.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(updatedLog.getBody()).contains("\"proteinGrams\":15.5", "\"recipeCookedTotalWeightGrams\":80.0");
		assertThat(clearedYield.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(clearedYield.getBody()).contains("\"cookedTotalWeightGrams\":null");
		assertThat(adjusted.getStatusCode().is4xxClientError()).isTrue();
	}

	@Test
	void recipePortionsStayCompatibleAndGramsRequireACookedWeight() {
		HttpHeaders headers = authHeaders();
		Map<String, Object> recipe = Map.of("name", "Pollo en porciones", "cookedTotalWeightGrams", 80,
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<Map> cooked = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, headers), Map.class);
		ResponseEntity<String> portions = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(Map.of(
				"itemType", "RECIPE", "itemId", cooked.getBody().get("id"), "mealType", "DINNER", "quantity", 2,
				"unit", "PORTION", "logDate", "2033-04-11"), headers), String.class);
		ResponseEntity<Map> rawOnly = rest.postForEntity("/api/recipes", new HttpEntity<>(Map.of("name", "Pollo crudo",
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM"))), headers), Map.class);
		ResponseEntity<String> invalidGrams = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(Map.of(
				"itemType", "RECIPE", "itemId", rawOnly.getBody().get("id"), "mealType", "DINNER", "quantity", 40,
				"unit", "GRAM", "logDate", "2033-04-11"), headers), String.class);

		assertThat(portions.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(portions.getBody()).contains("\"proteinGrams\":62.0", "\"unit\":\"PORTION\"");
		assertThat(invalidGrams.getStatusCode().is4xxClientError()).isTrue();
		assertThat(invalidGrams.getBody()).contains("peso total cocido");
	}

	@Test
	void preservesDecimalFoodAndRecipeQuantities() throws Exception {
		HttpHeaders headers = authHeaders();
		String date = "2033-04-12";

		ResponseEntity<String> foodLog = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(Map.of(
				"itemType", "FOOD", "itemId", 1, "mealType", "LUNCH", "quantity", 42.5,
				"unit", "GRAM", "logDate", date), headers), String.class);
		ResponseEntity<String> recipe = rest.postForEntity("/api/recipes", new HttpEntity<>(Map.of(
				"name", "Receta decimal", "cookedTotalWeightGrams", 84.25,
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 42.5, "unit", "GRAM"))), headers), String.class);

		assertThat(foodLog.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(foodLog.getBody()).contains("\"quantity\":42.5");
		assertThat(recipe.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(recipe.getBody()).contains("\"quantity\":42.5", "\"cookedTotalWeightGrams\":84.25");

		Object recipeId = objectMapper.readValue(recipe.getBody(), Map.class).get("id");
		ResponseEntity<String> halfPortion = rest.postForEntity("/api/nutrition/meal-logs", new HttpEntity<>(Map.of(
				"itemType", "RECIPE", "itemId", recipeId, "mealType", "DINNER", "quantity", 0.5,
				"unit", "PORTION", "logDate", date), headers), String.class);

		assertThat(halfPortion.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(halfPortion.getBody()).contains("\"quantity\":0.5");
	}

	@Test
	void recipeIngredientChangesClearCookedWeightAndMillilitersDoNotInflateRawGrams() {
		HttpHeaders headers = authHeaders();
		ResponseEntity<Map> liquid = rest.postForEntity("/api/foods", new HttpEntity<>(Map.of(
				"name", "Caldo de prueba", "category", "BEVERAGE", "baseUnit", "MILLILITER", "baseQuantity", 100,
				"proteinGrams", 0, "carbsGrams", 1, "fatGrams", 0, "preparation", "AS_SOLD"), headers), Map.class);
		assertThat(liquid.getStatusCode().is2xxSuccessful()).isTrue();
		Map<String, Object> recipe = Map.of("name", "Pollo con caldo", "cookedTotalWeightGrams", 180,
				"ingredients", List.of(
						Map.of("foodId", 1, "quantity", 100, "unit", "GRAM"),
						Map.of("foodId", liquid.getBody().get("id"), "quantity", 100, "unit", "MILLILITER")));
		ResponseEntity<Map> created = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, headers), Map.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(created.getBody().get("rawTotalWeightGrams")).hasToString("100.0");

		ResponseEntity<String> updated = rest.exchange("/api/recipes/" + created.getBody().get("id"), HttpMethod.PUT,
				new HttpEntity<>(Map.of("name", "Pollo con caldo", "ingredients", List.of(
						Map.of("foodId", 1, "quantity", 200, "unit", "GRAM"),
						Map.of("foodId", liquid.getBody().get("id"), "quantity", 100, "unit", "MILLILITER"))), headers), String.class);

		assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(updated.getBody()).contains("\"totalWeightGrams\":200.0", "\"rawTotalWeightGrams\":200.0",
				"\"cookedTotalWeightGrams\":null");
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
				new LoginRequest("alex", "central-password"), LoginResponse.class);
		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		return cookieHeaders(login);
	}

	private HttpHeaders authHeaders(String username) {
		ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
				new LoginRequest(username, "central-password"), LoginResponse.class);
		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		return cookieHeaders(login);
	}

	private HttpHeaders cookieHeaders(ResponseEntity<?> login) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
				.map(cookie -> cookie.substring(0, cookie.indexOf(';'))).collect(Collectors.joining("; ")));
		return headers;
	}

	record LoginRequest(String username, String password) {
	}

	record LoginResponse(String accessToken, String refreshToken, UserSummary user) {
	}

	record UserSummary(Long id) {
	}

	record NutritionPlanRequest(String name, Integer dailyCalories, Integer proteinPercent, Integer carbsPercent,
			Integer fatPercent, String startDate, String endDate) {
	}
}
