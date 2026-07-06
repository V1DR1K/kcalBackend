package com.vitalitypeak.kcal.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vitalitypeak.kcal.catalog.Food;
import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.catalog.FoodRepository;
import com.vitalitypeak.kcal.catalog.FoodUnit;
import com.vitalitypeak.kcal.common.BadRequestException;
import com.vitalitypeak.kcal.common.NotFoundException;
import com.vitalitypeak.kcal.externalfood.ExternalFoodCandidate;
import com.vitalitypeak.kcal.externalfood.ExternalFoodLookupService;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddMealLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddFoodLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddWaterRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.CreateFoodRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.CreateRecipeRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.DashboardResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.DaySummary;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodLogResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.HistoryResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.MacroProgress;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.MealSummary;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.MealTypeResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.UpdateFoodLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.PageResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.RecipeIngredientResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.RecipeResponse;
import com.vitalitypeak.kcal.recipe.Recipe;
import com.vitalitypeak.kcal.recipe.RecipeIngredient;
import com.vitalitypeak.kcal.recipe.RecipeRepository;
import com.vitalitypeak.kcal.profile.NutritionPlan;
import com.vitalitypeak.kcal.profile.ProfileDtos.NutritionPlanResponse;
import com.vitalitypeak.kcal.profile.ProfileService;
import com.vitalitypeak.kcal.storage.ProductImageStorageService;
import com.vitalitypeak.kcal.storage.StoredImage;
import com.vitalitypeak.kcal.user.AppUser;

@Service
public class NutritionService {
    private final FoodRepository foods;
    private final RecipeRepository recipes;
    private final FoodLogRepository foodLogs;
    private final WaterLogRepository waterLogs;
    private final ProfileService profileService;
    private final ProductImageStorageService imageStorage;
    private final ExternalFoodLookupService externalFoodLookup;

    public NutritionService(FoodRepository foods, RecipeRepository recipes, FoodLogRepository foodLogs,
            WaterLogRepository waterLogs, ProfileService profileService, ProductImageStorageService imageStorage,
            ExternalFoodLookupService externalFoodLookup) {
        this.foods = foods;
        this.recipes = recipes;
        this.foodLogs = foodLogs;
        this.waterLogs = waterLogs;
        this.profileService = profileService;
        this.imageStorage = imageStorage;
        this.externalFoodLookup = externalFoodLookup;
    }

    @Transactional
    public PageResponse<FoodResponse> searchFoods(String query, FoodCategory category, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        Page<Food> result;
        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            query = query.trim();
            if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        }
        if (hasQuery && category != null) {
            result = foods.search(query, category, pageable);
        } else if (hasQuery) {
            result = foods.search(query, pageable);
        } else if (category != null) {
            result = foods.findByCategory(category, pageable);
        } else {
            result = foods.findAll(pageable);
        }
        if (hasQuery && page == 0 && result.getTotalElements() < Math.min(size, 10)) {
            externalFoodLookup.searchByText(query, 12).forEach(candidate -> {
                if (candidate.barcode() != null && !foods.existsByBarcode(candidate.barcode())) importExternalFood(candidate);
            });
            result = category == null ? foods.search(query, pageable) : foods.search(query, category, pageable);
        }
        return page(result.map(this::toFoodResponse));
    }

    @Transactional
    public FoodResponse createFood(CreateFoodRequest request, com.vitalitypeak.kcal.user.AppUser creator) {
        String barcode = clean(request.barcode());
        if (barcode != null && foods.existsByBarcode(barcode)) {
            throw new BadRequestException("Ya existe un alimento con ese codigo de barras.");
        }
        if ((clean(request.servingName()) == null) != (request.servingWeightGrams() == null)) {
            throw new BadRequestException("El nombre y el peso de la unidad deben informarse juntos.");
        }
        Food food = new Food();
        food.setName(request.name().trim());
        food.setBrand(clean(request.brand()));
        food.setBarcode(barcode);
        food.setCategory(request.category());
        food.setBaseUnit(request.baseUnit());
        food.setBaseQuantity(request.baseQuantity());
        food.setCalories(request.calories());
        food.setProteinGrams(scale(request.proteinGrams()));
        food.setCarbsGrams(scale(request.carbsGrams()));
        food.setFatGrams(scale(request.fatGrams()));
        food.setPreparation(request.preparation() == null ? com.vitalitypeak.kcal.catalog.FoodPreparation.UNSPECIFIED : request.preparation());
        food.setPreparationSource("Ingresado por el usuario");
        food.setServingName(clean(request.servingName()));
        food.setServingWeightGrams(request.servingWeightGrams());
        food.setImageUrl(clean(request.imageUrl()));
        food.setCreatedBy(creator);
        food.setCreatedAt(OffsetDateTime.now());
        food.setModerationStatus(com.vitalitypeak.kcal.catalog.ModerationStatus.PENDING);
        food.setTags(request.tags() == null ? new LinkedHashSet<>() : request.tags().stream()
                .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        return toFoodResponse(foods.save(food));
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findFoodsCreatedBy(com.vitalitypeak.kcal.user.AppUser creator) {
        return foods.findByCreatedByIdOrderByCreatedAtDesc(creator.getId()).stream().map(this::toFoodResponse).toList();
    }

    @Transactional
    public FoodResponse updateOwnedFood(Long id, CreateFoodRequest request, com.vitalitypeak.kcal.user.AppUser creator) {
        Food food = getFood(id);
        if (food.getCreatedBy() == null || !food.getCreatedBy().getId().equals(creator.getId())) {
            throw new BadRequestException("Solo podés editar alimentos creados por vos.");
        }
        String barcode = clean(request.barcode());
        if (barcode != null && !barcode.equals(food.getBarcode()) && foods.existsByBarcode(barcode)) {
            throw new BadRequestException("Ya existe un alimento con ese código de barras.");
        }
        food.setName(request.name().trim());
        food.setBrand(clean(request.brand()));
        food.setBarcode(barcode);
        food.setCategory(request.category());
        food.setBaseUnit(request.baseUnit());
        food.setBaseQuantity(request.baseQuantity());
        food.setCalories(request.calories());
        food.setProteinGrams(scale(request.proteinGrams()));
        food.setCarbsGrams(scale(request.carbsGrams()));
        food.setFatGrams(scale(request.fatGrams()));
        food.setPreparation(com.vitalitypeak.kcal.catalog.FoodPreparation.UNSPECIFIED);
        food.setPreparationSource("Ingresado por el usuario");
        food.setServingName(null);
        food.setServingWeightGrams(null);
        food.setTags(request.tags() == null ? new LinkedHashSet<>() : request.tags().stream()
                .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        return toFoodResponse(foods.save(food));
    }

    @Transactional
    public FoodResponse uploadFoodImage(Long foodId, org.springframework.web.multipart.MultipartFile image) {
        Food food = getFood(foodId);
        StoredImage stored = imageStorage.storeFoodImage(food.getId(), image);
        String oldKey = food.getImageObjectKey();
        food.setImageObjectKey(stored.objectKey());
        food.setImageUrl(stored.url());
        Food saved = foods.save(food);
        imageStorage.deleteQuietly(oldKey);
        return toFoodResponse(saved);
    }

    @Transactional(readOnly = true)
    public FoodResponse findFood(Long id) {
        return toFoodResponse(getFood(id));
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findPreparationOptions(Long id) {
        Food food = getFood(id);
        if (clean(food.getPreparationGroup()) == null) return List.of(toFoodResponse(food));
        return foods.findByPreparationGroupOrderByPreparationAsc(food.getPreparationGroup()).stream()
                .map(this::toFoodResponse).toList();
    }

    @Transactional
    public FoodResponse findByBarcode(String barcode) {
        String cleanBarcode = clean(barcode);
        if (cleanBarcode == null) {
            throw new NotFoundException("No encontramos un alimento con ese codigo.");
        }
        return foods.findByBarcode(cleanBarcode)
                .map(existing -> shouldEnrich(existing) ? externalFoodLookup.lookupByBarcode(cleanBarcode)
                        .map(candidate -> enrichExistingFood(existing, candidate))
                        .orElse(existing) : existing)
                .map(this::toFoodResponse)
                .orElseGet(() -> externalFoodLookup.lookupByBarcode(cleanBarcode)
                        .map(this::importExternalFood)
                        .map(this::toFoodResponse)
                        .orElseThrow(() -> new NotFoundException("No encontramos un alimento con ese codigo.")));
    }

    private boolean shouldEnrich(Food food) {
        return (food.getSource() == null || "LOCAL".equals(food.getSource()))
                && (food.getPreparation() == null
                    || food.getPreparation() == com.vitalitypeak.kcal.catalog.FoodPreparation.UNSPECIFIED);
    }

    private Food enrichExistingFood(Food food, ExternalFoodCandidate candidate) {
        // Same barcode means same product: enrich the existing row instead of creating a competing record.
        if (food.getPreparation() == null || food.getPreparation() == com.vitalitypeak.kcal.catalog.FoodPreparation.UNSPECIFIED) {
            food.setPreparation(candidate.preparation());
            food.setPreparationSource(clean(candidate.preparationSource()));
        }
        if (food.getServingWeightGrams() == null && candidate.servingWeightGrams() != null) {
            food.setServingName(clean(candidate.servingName()));
            food.setServingWeightGrams(candidate.servingWeightGrams());
        }
        if (clean(food.getImageUrl()) == null) food.setImageUrl(clean(candidate.imageUrl()));
        if (clean(food.getBrand()) == null) food.setBrand(clean(candidate.brand()));
        if (clean(food.getSource()) == null || "LOCAL".equals(food.getSource())) {
            food.setSource(candidate.source());
            food.setSourceId(candidate.sourceId());
        }
        food.setLastSyncedAt(OffsetDateTime.now());
        if (candidate.tags() != null) candidate.tags().stream().map(this::clean).filter(tag -> tag != null)
                .forEach(tag -> { if (food.getTags().size() < 10) food.getTags().add(tag); });
        return foods.save(food);
    }

    private Food importExternalFood(ExternalFoodCandidate candidate) {
        Food food = new Food();
        food.setName(candidate.name());
        food.setBrand(clean(candidate.brand()));
        food.setBarcode(candidate.barcode());
        food.setCategory(candidate.category());
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setCalories(candidate.calories());
        food.setProteinGrams(scale(candidate.proteinGrams()));
        food.setCarbsGrams(scale(candidate.carbsGrams()));
        food.setFatGrams(scale(candidate.fatGrams()));
        food.setPreparation(candidate.preparation());
        food.setPreparationSource(clean(candidate.preparationSource()));
        food.setServingName(clean(candidate.servingName()));
        food.setServingWeightGrams(candidate.servingWeightGrams());
        food.setImageUrl(clean(candidate.imageUrl()));
        food.setSource(candidate.source());
        food.setSourceId(candidate.sourceId());
        food.setLastSyncedAt(OffsetDateTime.now());
        food.setTags(candidate.tags() == null ? new LinkedHashSet<>() : candidate.tags().stream()
                .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        return foods.save(food);
    }

    @Transactional(readOnly = true)
    public NutritionPreviewResponse preview(Long foodId, BigDecimal quantity, FoodUnit unit) {
        return preview(getFood(foodId), quantity);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchRecipes(String query, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        if (query != null && !query.isBlank()) {
            query = query.trim();
            if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        }
        Page<Recipe> result = query != null && !query.isBlank()
                ? recipes.findByNameContainingIgnoreCase(query, pageable)
                : recipes.findAll(pageable);
        return page(result.map(this::toRecipeResponse));
    }

    @Transactional(readOnly = true)
    public RecipeResponse findRecipe(Long id) {
        return toRecipeResponse(getRecipe(id));
    }

    @Transactional
    public RecipeResponse createRecipe(AppUser user, CreateRecipeRequest request) {
        Recipe recipe = new Recipe();
        recipe.setName(request.name().trim());
        recipe.setDescription(clean(request.description()));
        recipe.setTotalWeightGrams(request.totalWeightGrams());
        recipe.setCreatedBy(user);
        for (var item : request.ingredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            ingredient.setFood(getFood(item.foodId()));
            ingredient.setQuantity(item.quantity());
            ingredient.setUnit(item.unit());
            recipe.getIngredients().add(ingredient);
        }
        applyRecipeTotals(recipe);
        return toRecipeResponse(recipes.save(recipe));
    }

    @Transactional(readOnly = true)
    public NutritionPreviewResponse previewRecipe(CreateRecipeRequest request) {
        Recipe recipe = new Recipe();
        recipe.setTotalWeightGrams(request.totalWeightGrams());
        for (var item : request.ingredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            ingredient.setFood(getFood(item.foodId()));
            ingredient.setQuantity(item.quantity());
            ingredient.setUnit(item.unit());
            recipe.getIngredients().add(ingredient);
        }
        applyRecipeTotals(recipe);
        return new NutritionPreviewResponse(recipe.getCalories(), recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams());
    }

    @Transactional
    public FoodLogResponse addFoodLog(AppUser user, AddFoodLogRequest request) {
        return addMealLog(user, new AddMealLogRequest(MealItemType.FOOD, request.foodId(), request.mealType(), request.quantity(), request.unit(), request.logDate()));
    }

    @Transactional
    public FoodLogResponse addMealLog(AppUser user, AddMealLogRequest request) {
        NutritionPreviewResponse preview;
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setItemType(request.itemType());
        if (request.itemType() == MealItemType.FOOD) {
            Food food = getFood(request.itemId());
            preview = preview(food, request.quantity());
            log.setFood(food);
        } else {
            Recipe recipe = getRecipe(request.itemId());
            preview = previewRecipeServing(recipe, request.quantity());
            log.setRecipe(recipe);
        }
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(request.unit());
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        log.setCalories(preview.calories());
        log.setProteinGrams(preview.proteinGrams());
        log.setCarbsGrams(preview.carbsGrams());
        log.setFatGrams(preview.fatGrams());
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public void addWater(AppUser user, AddWaterRequest request) {
        WaterLog log = new WaterLog();
        log.setUser(user);
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        log.setLiters(request.liters());
        waterLogs.save(log);
    }

    @Transactional
    public void deleteFoodLog(AppUser user, Long logId) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        foodLogs.delete(log);
    }

    @Transactional
    public FoodLogResponse updateFoodLog(AppUser user, Long logId, UpdateFoodLogRequest request) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        NutritionPreviewResponse preview = log.getItemType() == MealItemType.RECIPE
                ? previewRecipeServing(log.getRecipe(), request.quantity())
                : preview(log.getFood(), request.quantity());
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(request.unit());
        log.setLogDate(request.logDate() == null ? log.getLogDate() : request.logDate());
        log.setCalories(preview.calories());
        log.setProteinGrams(preview.proteinGrams());
        log.setCarbsGrams(preview.carbsGrams());
        log.setFatGrams(preview.fatGrams());
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public void deleteLatestWaterLog(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        WaterLog log = waterLogs.findFirstByUserAndLogDateOrderByCreatedAtDesc(user, targetDate)
                .orElseThrow(() -> new NotFoundException("No hay registros de agua para descontar."));
        waterLogs.delete(log);
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        NutritionPlan plan = profileService.resolvePlan(user, targetDate);
        List<FoodLog> logs = foodLogs.findByUserAndLogDate(user, targetDate);
        int calories = logs.stream().mapToInt(FoodLog::getCalories).sum();
        BigDecimal protein = sum(logs, FoodLog::getProteinGrams);
        BigDecimal carbs = sum(logs, FoodLog::getCarbsGrams);
        BigDecimal fat = sum(logs, FoodLog::getFatGrams);
        BigDecimal water = waterLogs.findByUserAndLogDate(user, targetDate).stream()
                .map(WaterLog::getLiters).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<MealType, List<FoodLog>> byMeal = logs.stream().collect(Collectors.groupingBy(FoodLog::getMealType));
        List<MealSummary> meals = Arrays.stream(MealType.values()).map(meal -> {
            List<FoodLogResponse> items = byMeal.getOrDefault(meal, List.of()).stream().map(this::toFoodLogResponse).toList();
            int mealCalories = items.stream().mapToInt(FoodLogResponse::calories).sum();
            return new MealSummary(meal, label(meal), mealCalories, sumResponses(items, FoodLogResponse::proteinGrams),
                    sumResponses(items, FoodLogResponse::carbsGrams), sumResponses(items, FoodLogResponse::fatGrams), items);
        }).toList();
        return new DashboardResponse(targetDate, plan.getDailyCalories(), calories,
                Math.max(0, plan.getDailyCalories() - calories),
                List.of(
                        progress("protein", "Proteina", protein, BigDecimal.valueOf(plan.getProteinGoalGrams())),
                        progress("carbs", "Carbohidratos", carbs, BigDecimal.valueOf(plan.getCarbsGoalGrams())),
                        progress("fat", "Grasas", fat, BigDecimal.valueOf(plan.getFatGoalGrams()))),
                meals, water, user.getWaterGoalLiters(),
                new NutritionPlanResponse(plan.getId(), plan.getName(), plan.getDailyCalories(), plan.getProteinPercent(),
                        plan.getCarbsPercent(), plan.getFatPercent(), plan.getProteinGoalGrams(), plan.getCarbsGoalGrams(),
                        plan.getFatGoalGrams(), plan.getStartDate(), plan.getEndDate()));
    }

    @Transactional(readOnly = true)
    public List<MealTypeResponse> mealTypes() {
        return Arrays.stream(MealType.values()).map(meal -> new MealTypeResponse(meal, label(meal))).toList();
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(AppUser user, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<FoodLog> logs = foodLogs.findByUserAndLogDateBetween(user, ym.atDay(1), ym.atEndOfMonth());
        Map<LocalDate, List<FoodLog>> byDate = logs.stream().collect(Collectors.groupingBy(FoodLog::getLogDate));
        List<DaySummary> days = ym.atDay(1).datesUntil(ym.atEndOfMonth().plusDays(1)).map(date -> {
            List<FoodLog> dayLogs = byDate.getOrDefault(date, List.of());
            int calories = dayLogs.stream().mapToInt(FoodLog::getCalories).sum();
            return new DaySummary(date, calories, user.getDailyCalorieGoal(), sum(dayLogs, FoodLog::getProteinGrams),
                    sum(dayLogs, FoodLog::getCarbsGrams), sum(dayLogs, FoodLog::getFatGrams),
                    calories > 0 && calories <= user.getDailyCalorieGoal());
        }).toList();
        int average = days.stream().filter(day -> day.caloriesConsumed() > 0).mapToInt(DaySummary::caloriesConsumed)
                .average().stream().mapToInt(value -> (int) Math.round(value)).findFirst().orElse(0);
        long completed = days.stream().filter(DaySummary::goalReached).count();
        return new HistoryResponse(year, month, days, average, completed);
    }

    private Food getFood(Long foodId) {
        return foods.findById(foodId).orElseThrow(() -> new NotFoundException("Alimento no encontrado."));
    }

    private Recipe getRecipe(Long recipeId) {
        return recipes.findById(recipeId).orElseThrow(() -> new NotFoundException("Receta no encontrada."));
    }

    private NutritionPreviewResponse preview(Food food, BigDecimal quantity) {
        BigDecimal ratio = quantity.divide(food.getBaseQuantity(), 4, RoundingMode.HALF_UP);
        return new NutritionPreviewResponse(
                BigDecimal.valueOf(food.getCalories()).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue(),
                scale(food.getProteinGrams().multiply(ratio)),
                scale(food.getCarbsGrams().multiply(ratio)),
                scale(food.getFatGrams().multiply(ratio)));
    }

    private NutritionPreviewResponse previewRecipeServing(Recipe recipe, BigDecimal grams) {
        BigDecimal ratio = grams.divide(recipe.getTotalWeightGrams(), 4, RoundingMode.HALF_UP);
        return new NutritionPreviewResponse(
                BigDecimal.valueOf(recipe.getCalories()).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue(),
                scale(recipe.getProteinGrams().multiply(ratio)),
                scale(recipe.getCarbsGrams().multiply(ratio)),
                scale(recipe.getFatGrams().multiply(ratio)));
    }

    private void applyRecipeTotals(Recipe recipe) {
        if (recipe.getIngredients().isEmpty()) {
            throw new BadRequestException("La receta debe tener al menos un ingrediente.");
        }
        int calories = 0;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            NutritionPreviewResponse preview = preview(ingredient.getFood(), ingredient.getQuantity());
            calories += preview.calories();
            protein = protein.add(preview.proteinGrams());
            carbs = carbs.add(preview.carbsGrams());
            fat = fat.add(preview.fatGrams());
        }
        recipe.setCalories(calories);
        recipe.setProteinGrams(scale(protein));
        recipe.setCarbsGrams(scale(carbs));
        recipe.setFatGrams(scale(fat));
        recipe.setUpdatedAt(OffsetDateTime.now());
    }

    private MacroProgress progress(String key, String label, BigDecimal consumed, BigDecimal goal) {
        return new MacroProgress(key, label, consumed, goal, goal.subtract(consumed).max(BigDecimal.ZERO));
    }

    private FoodResponse toFoodResponse(Food food) {
        if (food == null) return null;
        return new FoodResponse(food.getId(), food.getName(), food.getBrand(), food.getBarcode(), food.getCategory(),
                food.getBaseUnit(), food.getBaseQuantity(), food.getCalories(), food.getProteinGrams(), food.getCarbsGrams(),
                food.getFatGrams(), food.getPreparation(), food.getPreparationSource(), food.getPreparationGroup(), food.getServingName(), food.getServingWeightGrams(), food.getImageUrl(), food.getSource(), food.getSourceId(), food.getLastSyncedAt(),
                copyTags(food.getTags()), food.getCreatedBy() == null ? null : food.getCreatedBy().getId(),
                food.getCreatedAt(), food.getModerationStatus());
    }

    private FoodLogResponse toFoodLogResponse(FoodLog log) {
        return new FoodLogResponse(log.getId(), log.getLogDate(), log.getMealType(), log.getItemType(),
                toFoodResponse(log.getFood()), log.getRecipe() == null ? null : toRecipeSummary(log.getRecipe()),
                log.getQuantity(), log.getUnit(), log.getCalories(), log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams());
    }

    private RecipeResponse toRecipeResponse(Recipe recipe) {
        return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getTotalWeightGrams(),
                recipe.getCalories(), recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams(),
                recipe.getIngredients().stream()
                        .map(item -> new RecipeIngredientResponse(toFoodResponse(item.getFood()), item.getQuantity(), item.getUnit()))
                        .toList());
    }

    private RecipeResponse toRecipeSummary(Recipe recipe) {
        return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getTotalWeightGrams(),
                recipe.getCalories(), recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams(), List.of());
    }

    private static BigDecimal sum(List<FoodLog> logs, java.util.function.Function<FoodLog, BigDecimal> mapper) {
        return logs.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static String label(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> "Desayuno";
            case LUNCH -> "Almuerzo";
            case AFTERNOON_SNACK -> "Merienda";
            case DINNER -> "Cena";
        };
    }

    private static <T> PageResponse<T> page(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    private static BigDecimal sumResponses(List<FoodLogResponse> logs, java.util.function.Function<FoodLogResponse, BigDecimal> mapper) {
        return logs.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private Set<String> copyTags(Set<String> tags) {
        return tags == null ? Set.of() : new LinkedHashSet<>(tags);
    }
}
