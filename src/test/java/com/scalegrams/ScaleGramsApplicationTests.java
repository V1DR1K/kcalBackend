package com.scalegrams;

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

import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
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

	@MockBean
	ExternalFoodLookupService externalFoodLookup;

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
	void seedsAdminUser() {
		ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
				new LoginRequest("admin@gmail.com", "admin"), LoginResponse.class);

		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(login.getBody().token()).isNotBlank();
	}

	@Test
	void refreshTokenRotatesAndLogoutRevokes() {
		ResponseEntity<LoginResponse> login = rest.postForEntity("/api/auth/login",
				new LoginRequest("admin@gmail.com", "admin"), LoginResponse.class);
		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(login.getBody().refreshToken()).isNotBlank();

		ResponseEntity<LoginResponse> rotated = rest.postForEntity("/api/auth/refresh",
				new RefreshRequest(login.getBody().refreshToken()), LoginResponse.class);
		assertThat(rotated.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(rotated.getBody().token()).isNotBlank();
		assertThat(rotated.getBody().refreshToken()).isNotBlank();

		rest.postForEntity("/api/auth/logout", new RefreshRequest(rotated.getBody().refreshToken()), Void.class);
		ResponseEntity<String> reused = rest.postForEntity("/api/auth/refresh",
				new RefreshRequest(rotated.getBody().refreshToken()), String.class);
		assertThat(reused.getStatusCode().is4xxClientError()).isTrue();
	}

	@Test
	void changePasswordRequiresCurrentPasswordAndIssuesNewSession() {
		String email = "cambio@ejemplo.com";
		ResponseEntity<LoginResponse> registered = rest.postForEntity("/api/auth/register",
				new RegisterRequest("Cambio", email, "claveOriginal", 75, 175, "1995-01-01", "MALE", "MAINTAIN",
						"MODERATELY_ACTIVE"),
				LoginResponse.class);
		assertThat(registered.getStatusCode().is2xxSuccessful()).isTrue();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(registered.getBody().token());

		ResponseEntity<LoginResponse> changed = rest.exchange("/api/auth/change-password", HttpMethod.PUT,
				new HttpEntity<>(new ChangePasswordRequest("claveOriginal", "nuevaClave123"), headers), LoginResponse.class);
		assertThat(changed.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(changed.getBody().refreshToken()).isNotBlank();

		ResponseEntity<LoginResponse> oldLogin = rest.postForEntity("/api/auth/login",
				new LoginRequest(email, "claveOriginal"), LoginResponse.class);
		assertThat(oldLogin.getStatusCode().is4xxClientError()).isTrue();

		ResponseEntity<String> wrongPassword = rest.postForEntity("/api/auth/change-password",
				new HttpEntity<>(new ChangePasswordRequest("mal", "otraClave"), headers), String.class);
		assertThat(wrongPassword.getStatusCode().is4xxClientError()).isTrue();

		ResponseEntity<LoginResponse> newLogin = rest.postForEntity("/api/auth/login",
				new LoginRequest(email, "nuevaClave123"), LoginResponse.class);
		assertThat(newLogin.getStatusCode().is2xxSuccessful()).isTrue();
		rest.postForEntity("/api/auth/logout", new RefreshRequest(newLogin.getBody().refreshToken()), Void.class);
	}

	@Test
	void weightHistoryUpsertsAndDeletesAndProfileWeightCreatesTodaysEntry() {
		String email = "peso@ejemplo.com";
		ResponseEntity<LoginResponse> registered = rest.postForEntity("/api/auth/register",
				new RegisterRequest("Peso", email, "claveOriginal", 82, 175, "1995-01-01", "MALE", "MAINTAIN",
						"MODERATELY_ACTIVE"),
				LoginResponse.class);
		assertThat(registered.getStatusCode().is2xxSuccessful()).isTrue();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(registered.getBody().token());

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
	void usersCanExploreAndCopyRecipesWithoutTakingOwnershipOfTheOriginal() {
		HttpHeaders alexHeaders = authHeaders();
		Map<String, Object> registration = Map.of(
				"fullName", "Recetas Compartidas",
				"email", "recipes-share@example.com",
				"password", "password123",
				"weightKg", 70,
				"heightCm", 170,
				"birthDate", "1995-05-10",
				"gender", "FEMALE",
				"goal", "MAINTAIN",
				"activityLevel", "MODERATELY_ACTIVE");
		ResponseEntity<LoginResponse> registered = rest.postForEntity("/api/auth/register", new HttpEntity<>(registration), LoginResponse.class);
		assertThat(registered.getStatusCode().is2xxSuccessful()).isTrue();
		HttpHeaders ownerHeaders = new HttpHeaders();
		ownerHeaders.setBearerAuth(registered.getBody().token());

		Map<String, Object> recipe = Map.of(
				"name", "Receta para compartir",
				"description", "Una receta comunitaria",
				"ingredients", List.of(Map.of("foodId", 1, "quantity", 100, "unit", "GRAM")));
		ResponseEntity<Map> created = rest.postForEntity("/api/recipes", new HttpEntity<>(recipe, ownerHeaders), Map.class);
		assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
		Object sharedId = created.getBody().get("id");

		ResponseEntity<String> authors = rest.exchange("/api/recipes/explore/users", HttpMethod.GET,
				new HttpEntity<>(alexHeaders), String.class);
		ResponseEntity<String> sharedRecipes = rest.exchange("/api/recipes/explore/users/" + registered.getBody().user().id() + "?size=5",
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
		log.setUser(users.findByEmailIgnoreCase("alex@scalegrams.local").orElseThrow());
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
				new LoginRequest("alex@scalegrams.local", "password123"), LoginResponse.class);
		assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().token());
		return headers;
	}

	record LoginRequest(String email, String password) {
	}

	record LoginResponse(String token, String refreshToken, UserSummary user) {
	}

	record UserSummary(Long id) {
	}

	record RefreshRequest(String refreshToken) {
	}

	record ChangePasswordRequest(String currentPassword, String newPassword) {
	}

	record RegisterRequest(String fullName, String email, String password, Integer weightKg, Integer heightCm,
			String birthDate, String gender, String goal, String activityLevel) {
	}

	record NutritionPlanRequest(String name, Integer dailyCalories, Integer proteinPercent, Integer carbsPercent,
			Integer fatPercent, String startDate, String endDate) {
	}
}
