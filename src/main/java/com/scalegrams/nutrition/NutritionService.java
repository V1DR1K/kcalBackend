package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.util.Objects;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.CookedYieldSource;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.common.ForbiddenException;
import com.scalegrams.common.SearchTextNormalizer;
import com.scalegrams.externalfood.ExternalFoodCandidate;
import com.scalegrams.externalfood.ExternalFoodLookupService;
import com.scalegrams.externalfood.UsdaFoodDataProvider;
import com.scalegrams.nutrition.NutritionDtos.AddMealLogRequest;
import com.scalegrams.nutrition.NutritionDtos.AddFoodLogRequest;
import com.scalegrams.nutrition.NutritionDtos.ApplyDayPresetRequest;
import com.scalegrams.nutrition.NutritionDtos.BatchAddMealLogsRequest;
import com.scalegrams.nutrition.NutritionDtos.BatchAddMealLogRequest;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;
import com.scalegrams.nutrition.NutritionDtos.ConfirmAiEstimateRequest;
import com.scalegrams.nutrition.NutritionDtos.ConfirmAiEstimateItem;
import com.scalegrams.nutrition.NutritionDtos.ConfirmAiRegistrationRequest;
import com.scalegrams.nutrition.NutritionDtos.AiRegistrationResponse;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateFoodProposal;
import com.scalegrams.nutrition.NutritionDtos.CreateFoodRequest;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeRequest;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeFromMealRequest;
import com.scalegrams.nutrition.NutritionDtos.AddRecipeMealLogRequest;
import com.scalegrams.nutrition.NutritionDtos.DashboardResponse;
import com.scalegrams.nutrition.NutritionDtos.CreateDayPresetRequest;
import com.scalegrams.nutrition.NutritionDtos.DayPresetItemRequest;
import com.scalegrams.nutrition.NutritionDtos.DayPresetResponse;
import com.scalegrams.nutrition.NutritionDtos.DaySummary;
import com.scalegrams.nutrition.NutritionDtos.FoodLogResponse;
import com.scalegrams.nutrition.NutritionDtos.FoodResponse;
import com.scalegrams.nutrition.NutritionDtos.FoodSummaryResponse;
import com.scalegrams.nutrition.NutritionDtos.HistoryResponse;
import com.scalegrams.nutrition.NutritionDtos.MacroProgress;
import com.scalegrams.nutrition.NutritionDtos.MealSummary;
import com.scalegrams.nutrition.NutritionDtos.MealTypeResponse;
import com.scalegrams.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.scalegrams.nutrition.NutritionDtos.UpdateFoodLogRequest;
import com.scalegrams.nutrition.NutritionDtos.UpdateAiEstimateRequest;
import com.scalegrams.nutrition.NutritionDtos.UpdateDayPresetRequest;
import com.scalegrams.nutrition.NutritionDtos.UpdateRecipeLogIngredientsRequest;
import com.scalegrams.nutrition.NutritionDtos.UpdateRecipeFoodLogRequest;
import com.scalegrams.nutrition.NutritionDtos.SaveAiEstimateItemRequest;
import com.scalegrams.nutrition.NutritionDtos.PageResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeIngredientResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeIngredientRequest;
import com.scalegrams.nutrition.NutritionDtos.RecipeReferenceResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeFromMealResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeOwnerResponse;
import com.scalegrams.nutrition.NutritionDtos.RecentMealResponse;
import com.scalegrams.nutrition.NutritionDtos.NutrientValueResponse;
import com.scalegrams.nutrition.NutritionDtos.NutrientInput;
import com.scalegrams.nutrition.NutritionDtos.NutrientUpdateRequest;
import com.scalegrams.recipe.Recipe;
import com.scalegrams.recipe.RecipeIngredient;
import com.scalegrams.recipe.RecipeRepository;
import com.scalegrams.profile.NutritionPlan;
import com.scalegrams.profile.ProfileDtos.NutritionPlanResponse;
import com.scalegrams.profile.ProfileService;
import com.scalegrams.user.AppUser;
import com.scalegrams.user.Role;

@Service
public class NutritionService {
    public record EnrichmentReport(int examined, int updated, int skipped, int noMatch) {}
    private final FoodRepository foods;
    private final RecipeRepository recipes;
    private final FoodLogRepository foodLogs;
    private final DayPresetRepository dayPresets;
    private final ProfileService profileService;
    private final ExternalFoodLookupService externalFoodLookup;
    private final ObjectMapper objectMapper;
    private final NutrientDefinitionRepository nutrientDefinitions;
    private final UsdaFoodDataProvider usda;
    private final AiFoodMatcher aiFoodMatcher;
    private final FoodSemanticSearchService semanticFoods;
    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    public NutritionService(FoodRepository foods, RecipeRepository recipes, FoodLogRepository foodLogs,
            DayPresetRepository dayPresets,
            ProfileService profileService,
            ExternalFoodLookupService externalFoodLookup, ObjectMapper objectMapper,
            NutrientDefinitionRepository nutrientDefinitions, UsdaFoodDataProvider usda, AiFoodMatcher aiFoodMatcher,
            JdbcTemplate jdbcTemplate, FoodSemanticSearchService semanticFoods,
            @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}") String driver) {
        this.foods = foods;
        this.recipes = recipes;
        this.foodLogs = foodLogs;
        this.dayPresets = dayPresets;
        this.profileService = profileService;
        this.externalFoodLookup = externalFoodLookup;
        this.objectMapper = objectMapper;
        this.nutrientDefinitions = nutrientDefinitions;
        this.usda = usda;
        this.aiFoodMatcher = aiFoodMatcher;
        this.semanticFoods = semanticFoods;
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = driver.toLowerCase().contains("postgres");
    }

    @Transactional(readOnly = true)
    public List<DayPresetResponse> dayPresets(AppUser user) {
        return dayPresets.findByUserAndDeletedAtIsNullOrderByUpdatedAtDesc(user).stream()
                .map(this::toDayPresetResponse).toList();
    }

    @Transactional
    public DayPresetResponse createDayPreset(AppUser user, CreateDayPresetRequest request) {
        checkArchived(templateFoods(request.items()), request.acknowledgedArchivedFoodIds());
        String name = normalizedPresetName(request.name());
        ensurePresetNameAvailable(user, name, null);
        DayPreset preset = new DayPreset();
        preset.setUser(user);
        preset.setName(name);
        preset.setDescription(cleanPresetDescription(request.description()));
        preset.setItemsJson(writePresetItems(validatePresetItems(request.items())));
        preset.setCreatedAt(OffsetDateTime.now());
        preset.setUpdatedAt(OffsetDateTime.now());
        return toDayPresetResponse(dayPresets.save(preset));
    }

    @Transactional
    public DayPresetResponse updateDayPreset(AppUser user, Long id, UpdateDayPresetRequest request) {
        checkArchived(templateFoods(request.items()), request.acknowledgedArchivedFoodIds());
        DayPreset preset = ownedDayPreset(user, id);
        String name = normalizedPresetName(request.name());
        ensurePresetNameAvailable(user, name, id);
        preset.setName(name);
        preset.setDescription(cleanPresetDescription(request.description()));
        preset.setItemsJson(writePresetItems(validatePresetItems(request.items())));
        preset.setUpdatedAt(OffsetDateTime.now());
        return toDayPresetResponse(dayPresets.save(preset));
    }

    @Transactional
    public void deleteDayPreset(AppUser user, Long id) {
        DayPreset preset = ownedDayPreset(user, id);
        preset.setDeletedAt(OffsetDateTime.now());
        preset.setUpdatedAt(OffsetDateTime.now());
        dayPresets.save(preset);
    }

    @Transactional
    public void applyDayPreset(AppUser user, Long id, ApplyDayPresetRequest request) {
        DayPreset preset = ownedDayPreset(user, id);
        LocalDate date = request.logDate();
        List<DayPresetItemRequest> items = readPresetItems(preset.getItemsJson());
        checkArchived(templateFoods(items), request.acknowledgedArchivedFoodIds());
        if (request.replace()) foodLogs.deleteAll(foodLogs.findByUserAndLogDate(user, date));
        for (int presetIndex = 0; presetIndex < items.size(); presetIndex++) {
            DayPresetItemRequest item = items.get(presetIndex);
            if (item.itemType() == MealItemType.AI_ESTIMATE) {
                List<AiEstimateItem> aiItems = aiEstimateItems(item);
                for (int itemIndex = 0; itemIndex < aiItems.size(); itemIndex++) {
                    AiEstimateItem aiItem = aiItems.get(itemIndex);
                    Food food = materializeAiEstimateItem(user,
                            "day-preset:" + preset.getId() + ":item:" + presetIndex + ":ai:" + itemIndex, aiItem, request.acknowledgedArchivedFoodIds());
                    BigDecimal quantity = aiItem.estimatedGrams().multiply(item.quantity());
                    addMealLog(user, new AddMealLogRequest(MealItemType.FOOD, food.getId(), item.mealType(),
                            quantity, FoodUnit.GRAM, date), true);
                }
            } else {
                addMealLog(user, new AddMealLogRequest(item.itemType(), item.itemId(), item.mealType(),
                        item.quantity(), item.unit(), date), true);
            }
        }
    }

    private DayPreset ownedDayPreset(AppUser user, Long id) {
        return dayPresets.findByIdAndUserAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("El preset no existe."));
    }

    private String normalizedPresetName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) throw new BadRequestException("El nombre del preset es obligatorio.");
        return name;
    }

    private String cleanPresetDescription(String value) {
        String description = value == null ? "" : value.trim();
        return description.isBlank() ? null : description;
    }

    private void ensurePresetNameAvailable(AppUser user, String name, Long ignoredId) {
        boolean taken = dayPresets.existsActiveName(user, name);
        if (taken && (ignoredId == null || dayPresets.findByIdAndUserAndDeletedAtIsNull(ignoredId, user)
                .map(preset -> !preset.getName().equalsIgnoreCase(name)).orElse(true))) {
            throw new BadRequestException("Ya existe un preset con ese nombre.");
        }
    }

    private List<DayPresetItemRequest> validatePresetItems(List<DayPresetItemRequest> items) {
        if (items == null || items.isEmpty()) throw new BadRequestException("El día no tiene alimentos para guardar.");
        for (DayPresetItemRequest item : items) {
            if (item.itemType() == MealItemType.AI_ESTIMATE) {
                if (item.itemId() != null) throw new BadRequestException("La estimación no puede tener alimento asociado.");
            } else if (item.itemId() == null || item.itemId() <= 0) {
                throw new BadRequestException("El preset contiene un alimento inválido.");
            }
        }
        return items;
    }

    private String writePresetItems(List<DayPresetItemRequest> items) {
        try { return objectMapper.writeValueAsString(items); }
        catch (JsonProcessingException error) { throw new BadRequestException("No se pudo guardar el contenido del preset."); }
    }

    private List<DayPresetItemRequest> readPresetItems(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<DayPresetItemRequest>>() {}); }
        catch (JsonProcessingException error) { throw new BadRequestException("El contenido del preset no es válido."); }
    }

    private DayPresetResponse toDayPresetResponse(DayPreset preset) {
        List<DayPresetItemRequest> items = readPresetItems(preset.getItemsJson());
        Map<String, Integer> mealCounts = items.stream().collect(Collectors.groupingBy(item -> item.mealType().name(),
                LinkedHashMap::new, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        return new DayPresetResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getCreatedAt(), preset.getUpdatedAt(),
                items, items.size(), mealCounts);
    }

    @Transactional
    public PageResponse<FoodSummaryResponse> searchFoods(String query, FoodCategory category, int page, int size) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        Page<Food> result;
        query = SearchTextNormalizer.normalize(query);
        boolean hasQuery = !query.isBlank();
        if (hasQuery) pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50));
        if (hasQuery) {
            if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
            if (query.length() < 2) return page(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));
        }
        if (hasQuery && category != null) {
            result = postgres
                    ? searchFoodsHybrid(query, category, normalizedPage, normalizedSize)
                    : foods.search(query, category, ModerationStatus.APPROVED, pageable);
        } else if (hasQuery) {
            result = postgres
                    ? searchFoodsHybrid(query, null, normalizedPage, normalizedSize)
                    : foods.search(query, ModerationStatus.APPROVED, pageable);
        } else if (category != null) {
            result = foods.findByModerationStatusAndCategoryAndDeletedAtIsNull(ModerationStatus.APPROVED, category, pageable);
        } else {
            result = foods.findByModerationStatusAndDeletedAtIsNull(ModerationStatus.APPROVED, pageable);
        }
        return page(result.map(this::toFoodSummaryResponse));
    }

    private Page<Food> searchFoodsHybrid(String query, FoodCategory category, int page, int size) {
        int offset = page * size;
        if (offset >= 500) {
            return category == null
                    ? foods.semanticSearch(query, ModerationStatus.APPROVED.name(), PageRequest.of(page, size))
                    : foods.semanticSearch(query, category.name(), ModerationStatus.APPROVED.name(),
                            PageRequest.of(page, size));
        }
        int poolSize = Math.min(500, Math.max(size, offset + size));
        PageRequest poolPage = PageRequest.of(0, poolSize);
        List<FoodSemanticSearchService.FoodMatch> semanticMatches = semanticFoods.search(query, category, null, poolSize);
        if (semanticMatches.isEmpty()) {
            return category == null
                    ? foods.semanticSearch(query, ModerationStatus.APPROVED.name(), PageRequest.of(page, size))
                    : foods.semanticSearch(query, category.name(), ModerationStatus.APPROVED.name(),
                            PageRequest.of(page, size));
        }
        Page<Food> lexical = category == null
                ? foods.semanticSearch(query, ModerationStatus.APPROVED.name(), poolPage)
                : foods.semanticSearch(query, category.name(), ModerationStatus.APPROVED.name(), poolPage);
        Map<Long, RankedFood> ranked = new LinkedHashMap<>();
        for (int index = 0; index < lexical.getContent().size(); index++) {
            Food food = lexical.getContent().get(index);
            ranked.computeIfAbsent(food.getId(), ignored -> new RankedFood(food))
                    .addLexicalRank(index);
        }
        for (int index = 0; index < semanticMatches.size(); index++) {
            FoodSemanticSearchService.FoodMatch match = semanticMatches.get(index);
            ranked.computeIfAbsent(match.food().getId(), ignored -> new RankedFood(match.food()))
                    .addSemanticRank(index, match.similarity());
        }
        List<Food> ordered = ranked.values().stream().sorted(RankedFood.ORDER)
                .map(RankedFood::food).toList();
        int from = Math.min(offset, ordered.size());
        int to = Math.min(from + size, ordered.size());
        long total = Math.max(lexical.getTotalElements(), ordered.size());
        return new PageImpl<>(ordered.subList(from, to), PageRequest.of(page, size), total);
    }

    private static final class RankedFood {
        private static final java.util.Comparator<RankedFood> ORDER = java.util.Comparator
                .comparingDouble(RankedFood::score).reversed()
                .thenComparing(java.util.Comparator.comparingDouble(RankedFood::similarity).reversed())
                .thenComparing(rank -> rank.food.getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(rank -> rank.food.getId());
        private final Food food;
        private double score;
        private double similarity;

        private RankedFood(Food food) { this.food = food; }
        private void addLexicalRank(int rank) { score += 1.0 / (rank + 1.0); }
        private void addSemanticRank(int rank, double similarity) {
            this.similarity = similarity;
            score += 0.5 * similarity / (rank + 1.0);
        }
        private double score() { return score; }
        private double similarity() { return similarity; }
        private Food food() { return food; }
    }

    @Transactional
    public FoodResponse createFood(CreateFoodRequest request, com.scalegrams.user.AppUser creator) {
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
        food.setProteinGrams(scale(request.proteinGrams()));
        food.setCarbsGrams(scale(request.carbsGrams()));
        food.setFatGrams(scale(request.fatGrams()));
        food.setCalories(food.getProteinGrams() == null || food.getCarbsGrams() == null || food.getFatGrams() == null ? request.calories() : macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        food.setPreparation(request.preparation() == null ? com.scalegrams.catalog.FoodPreparation.UNSPECIFIED : request.preparation());
        food.setPreparationSource("Ingresado por el usuario");
        applyRequestedCookedYield(food, request);
        food.setServingName(clean(request.servingName()));
        food.setServingWeightGrams(request.servingWeightGrams());
        food.setCreatedBy(creator);
        food.setCreatedAt(OffsetDateTime.now());
        food.setModerationStatus(com.scalegrams.catalog.ModerationStatus.APPROVED);
        if (request.tags() != null) {
            food.setTags(request.tags().stream()
                    .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        Food saved = foods.saveAndFlush(food);
        semanticFoods.indexIfAvailable(saved);
        return toFoodResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findFoodsCreatedBy(com.scalegrams.user.AppUser creator) {
        return foods.findByCreatedByIdAndDeletedAtIsNullOrderByCreatedAtDesc(creator.getId()).stream().map(this::toFoodResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findDeletedFoodsCreatedBy(com.scalegrams.user.AppUser creator) {
        return foods.findByCreatedByIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(creator.getId()).stream().map(this::toFoodResponse).toList();
    }

    @Transactional
    public FoodResponse updateOwnedFood(Long id, CreateFoodRequest request, com.scalegrams.user.AppUser creator) {
        Food food = getFood(id);
        boolean ownsFood = food.getCreatedBy() != null && food.getCreatedBy().getId().equals(creator.getId());
        if (!ownsFood && creator.getRole() != Role.ADMIN) {
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
        food.setProteinGrams(scale(request.proteinGrams()));
        food.setCarbsGrams(scale(request.carbsGrams()));
        food.setFatGrams(scale(request.fatGrams()));
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        if (request.preparation() != null) {
            food.setPreparation(request.preparation());
            food.setPreparationSource("Ingresado por el usuario");
        }
        applyRequestedCookedYield(food, request);
        if (request.servingName() != null || request.servingWeightGrams() != null) {
            food.setServingName(clean(request.servingName()));
            food.setServingWeightGrams(request.servingWeightGrams());
        }
        if (request.tags() != null) {
            food.setTags(request.tags().stream()
                    .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        Food saved = foods.saveAndFlush(food);
        semanticFoods.indexIfAvailable(saved);
        return toFoodResponse(saved);
    }

    private void applyRequestedCookedYield(Food food, CreateFoodRequest request) {
        if (request.cookedYieldFactor() != null) {
            food.setCookedYieldFactor(request.cookedYieldFactor().setScale(4, RoundingMode.HALF_UP));
            food.setCookedYieldSource(CookedYieldSource.MANUAL);
            String assumption = clean(request.cookedYieldAssumption());
            food.setCookedYieldAssumption(assumption == null ? "Factor ingresado manualmente." : assumption);
            return;
        }
        initializeIdentityCookedYield(food);
    }

    private void initializeIdentityCookedYield(Food food) {
        if (food.getCookedYieldFactor() != null || food.getPreparation() == null) return;
        if (food.getPreparation() == FoodPreparation.COOKED || food.getPreparation() == FoodPreparation.AS_SOLD) {
            food.setCookedYieldFactor(BigDecimal.ONE.setScale(4));
            food.setCookedYieldSource(CookedYieldSource.IDENTITY);
            food.setCookedYieldAssumption("Sin cambio de peso: el alimento ya está cocido o listo para consumir.");
        }
    }

    @Transactional
    public void deleteOwnedFood(Long id, com.scalegrams.user.AppUser creator) {
        Food food = getFood(id);
        if (food.getCreatedBy() == null || !food.getCreatedBy().getId().equals(creator.getId())) {
            throw new BadRequestException("Solo podés borrar alimentos creados por vos.");
        }
        if (food.getDeletedAt() == null) food.setDeletedAt(OffsetDateTime.now());
        foods.save(food);
    }

    @Transactional
    public FoodResponse restoreOwnedFood(Long id, com.scalegrams.user.AppUser creator) {
        Food food = getFood(id);
        if (food.getCreatedBy() == null || !food.getCreatedBy().getId().equals(creator.getId())) {
            throw new BadRequestException("Solo podés recuperar alimentos creados por vos.");
        }
        food.setDeletedAt(null);
        return toFoodResponse(foods.save(food));
    }

    @Transactional(readOnly = true)
    public FoodResponse findFood(Long id) {
        return toFoodResponse(getActiveFood(id));
    }

    @Transactional(readOnly = true)
    public List<NutrientValueResponse> nutrientDefinitions() {
        return nutrientDefinitions.findAll().stream().filter(NutrientDefinition::isVisible)
                .sorted(java.util.Comparator.comparing(NutrientDefinition::getDisplayOrder))
                .map(item -> new NutrientValueResponse(item.getCode(), item.getName(), item.getNutrientGroup(), item.getUnit(), null, null, "MISSING"))
                .toList();
    }

    @Transactional
    public FoodResponse enrichFood(Long id, AppUser user) {
        Food food = getFood(id);
        requireFoodWriteAccess(food, user);
        Optional<ExternalFoodCandidate> candidate = food.getBarcode() == null
                ? externalFoodLookup.searchByText(food.getName(), 1).stream().findFirst()
                : externalFoodLookup.lookupByBarcode(food.getBarcode());
        if (candidate.isEmpty()) {
            throw new BadRequestException("No encontramos una fuente nutricional para este alimento. Configurá USDA_FOOD_DATA_API_KEY para alimentos genéricos o usá Open Food Facts para productos con código de barras.");
        }
        return toFoodResponse(enrichExistingFood(food, candidate.get()));
    }

    @Transactional
    public FoodResponse updateNutrients(Long id, NutrientUpdateRequest request, AppUser user) {
        Food food = getFood(id);
        requireFoodWriteAccess(food, user);
        for (NutrientInput input : request.nutrients()) {
            NutrientDefinition definition = nutrientDefinitions.findById(input.code().trim().toUpperCase())
                    .orElseThrow(() -> new BadRequestException("Nutriente desconocido: " + input.code()));
            FoodNutrient nutrient = food.getNutrients().stream().filter(item -> item.getDefinition().getCode().equals(definition.getCode()))
                    .findFirst().orElseGet(() -> { FoodNutrient next = new FoodNutrient(); next.setFood(food); next.setDefinition(definition); food.getNutrients().add(next); return next; });
            nutrient.setValue(scale(input.value()));
            nutrient.setSource(NutrientSource.MANUAL);
            nutrient.setStatus(NutrientStatus.VERIFIED);
            nutrient.setUpdatedAt(OffsetDateTime.now());
            if ("PROTEIN".equals(definition.getCode())) food.setProteinGrams(scale(input.value()));
            if ("CARBOHYDRATE".equals(definition.getCode())) food.setCarbsGrams(scale(input.value()));
            if ("FAT".equals(definition.getCode())) food.setFatGrams(scale(input.value()));
            if ("CALORIES".equals(definition.getCode())) food.setCalories(input.value().setScale(0, RoundingMode.HALF_UP).intValue());
        }
        if (food.getCalories() == null || request.nutrients().stream().noneMatch(item -> "CALORIES".equalsIgnoreCase(item.code()))) {
            food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        }
        return toFoodResponse(foods.save(food));
    }

    private void requireFoodWriteAccess(Food food, AppUser user) {
        boolean administrator = user.getRole() == Role.ADMIN;
        boolean owner = food.getCreatedBy() != null && food.getCreatedBy().getId().equals(user.getId());
        if (!administrator && !owner) {
            throw new ForbiddenException(food.getCreatedBy() == null
                    ? "Solo un administrador puede editar alimentos compartidos."
                    : "Solo podés editar alimentos creados por vos.");
        }
    }

    @Transactional
    public EnrichmentReport enrichFoodCatalog(AppUser user, int limit) {
        if (user.getRole() != Role.ADMIN) throw new BadRequestException("Solo un administrador puede enriquecer el catálogo.");
        int examined = 0;
        int updated = 0;
        int skipped = 0;
        int noMatch = 0;
        for (Food food : foods.findAll(PageRequest.of(0, Math.min(Math.max(limit, 1), 100), Sort.by(Sort.Direction.ASC, "id")))) {
            examined++;
            if (hasDetailedNutrients(food)) { skipped++; continue; }
            Optional<ExternalFoodCandidate> candidate = food.getBarcode() == null
                    ? externalFoodLookup.searchByText(food.getName(), 1).stream().findFirst()
                    : externalFoodLookup.lookupByBarcode(food.getBarcode());
            if (candidate.isPresent()) { enrichExistingFood(food, candidate.get()); updated++; }
            else noMatch++;
        }
        return new EnrichmentReport(examined, updated, skipped, noMatch);
    }

    private boolean hasDetailedNutrients(Food food) {
        return food.getNutrients().stream().anyMatch(item -> item.getDefinition() != null
                && item.getValue() != null
                && !List.of("CALORIES", "PROTEIN", "CARBOHYDRATE", "FAT").contains(item.getDefinition().getCode()));
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findPreparationOptions(Long id) {
        Food food = getActiveFood(id);
        if (clean(food.getPreparationGroup()) == null) return List.of(toFoodResponse(food));
        return foods.findByPreparationGroupAndDeletedAtIsNullOrderByPreparationAsc(food.getPreparationGroup()).stream()
                .map(this::toFoodResponse).toList();
    }

    @Transactional
    public FoodResponse findByBarcode(String barcode) {
        String cleanBarcode = clean(barcode);
        if (cleanBarcode == null) {
            throw new NotFoundException("No encontramos un alimento con ese codigo.");
        }
        return foods.findByBarcodeAndDeletedAtIsNull(cleanBarcode)
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
                    || food.getPreparation() == com.scalegrams.catalog.FoodPreparation.UNSPECIFIED);
    }

    private Food enrichExistingFood(Food food, ExternalFoodCandidate candidate) {
        // Same barcode means same product: enrich the existing row instead of creating a competing record.
        if (food.getPreparation() == null || food.getPreparation() == com.scalegrams.catalog.FoodPreparation.UNSPECIFIED) {
            food.setPreparation(candidate.preparation());
            food.setPreparationSource(clean(candidate.preparationSource()));
        }
        if (food.getServingWeightGrams() == null && candidate.servingWeightGrams() != null) {
            food.setServingName(clean(candidate.servingName()));
            food.setServingWeightGrams(candidate.servingWeightGrams());
        }
        if (clean(food.getBrand()) == null) food.setBrand(clean(candidate.brand()));
        if (clean(food.getSource()) == null || "LOCAL".equals(food.getSource())) {
            food.setSource(candidate.source());
            food.setSourceId(candidate.sourceId());
        }
        applyExternalNutrients(food, candidate);
        initializeIdentityCookedYield(food);
        food.setLastSyncedAt(OffsetDateTime.now());
        if (candidate.tags() != null) candidate.tags().stream().map(this::clean).filter(tag -> tag != null)
                .forEach(tag -> { if (food.getTags().size() < 10) food.getTags().add(tag); });
        food.refreshSearchIndex();
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
        food.setProteinGrams(scale(candidate.proteinGrams()));
        food.setCarbsGrams(scale(candidate.carbsGrams()));
        food.setFatGrams(scale(candidate.fatGrams()));
        food.setCalories(candidate.calories() == null ? macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()) : candidate.calories());
        food.setPreparation(candidate.preparation());
        food.setPreparationSource(clean(candidate.preparationSource()));
        initializeIdentityCookedYield(food);
        food.setServingName(clean(candidate.servingName()));
        food.setServingWeightGrams(candidate.servingWeightGrams());
        food.setSource(candidate.source());
        food.setSourceId(candidate.sourceId());
        food.setLastSyncedAt(OffsetDateTime.now());
        applyExternalNutrients(food, candidate);
        food.setTags(candidate.tags() == null ? new LinkedHashSet<>() : candidate.tags().stream()
                .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        return foods.save(food);
    }

    @Transactional(readOnly = true)
    public NutritionPreviewResponse preview(Long foodId, BigDecimal quantity, FoodUnit unit) {
        return preview(getFood(foodId), quantity, unit);
    }

    private void applyExternalNutrients(Food food, ExternalFoodCandidate candidate) {
        if (candidate.nutrients() == null || candidate.nutrients().isEmpty()) return;
        candidate.nutrients().forEach((code, value) -> nutrientDefinitions.findById(code).ifPresent(definition -> {
            FoodNutrient nutrient = food.getNutrients().stream()
                    .filter(item -> item.getDefinition().getCode().equals(code)).findFirst().orElseGet(() -> {
                        FoodNutrient next = new FoodNutrient();
                        next.setFood(food);
                        next.setDefinition(definition);
                        food.getNutrients().add(next);
                        return next;
                    });
            nutrient.setValue(scale(value));
            nutrient.setSource(parseSource(candidate.source()));
            nutrient.setStatus(NutrientStatus.VERIFIED);
            nutrient.setExternalReference(candidate.sourceId());
            nutrient.setUpdatedAt(OffsetDateTime.now());
        }));
        syncMacrosFromNutrients(food);
    }

    private void syncMacrosFromNutrients(Food food) {
        food.getNutrients().stream().filter(item -> item.getValue() != null).forEach(item -> {
            switch (item.getDefinition().getCode()) {
                case "PROTEIN" -> food.setProteinGrams(scale(item.getValue()));
                case "CARBOHYDRATE" -> food.setCarbsGrams(scale(item.getValue()));
                case "FAT" -> food.setFatGrams(scale(item.getValue()));
                case "CALORIES" -> food.setCalories(item.getValue().setScale(0, RoundingMode.HALF_UP).intValue());
                default -> { }
            }
        });
        if (food.getCalories() == null) {
            food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        }
    }

    private void addAiNutrients(Food food, Map<String, BigDecimal> values, BigDecimal ratio) {
        if (values == null) return;
        values.forEach((code, value) -> nutrientDefinitions.findById(code).ifPresent(definition -> {
            FoodNutrient nutrient = new FoodNutrient();
            nutrient.setFood(food);
            nutrient.setDefinition(definition);
            nutrient.setValue(scale(value.multiply(ratio)));
            nutrient.setSource(NutrientSource.AI);
            nutrient.setStatus(NutrientStatus.ESTIMATED);
            food.getNutrients().add(nutrient);
        }));
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchRecipes(String query, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        query = SearchTextNormalizer.normalize(query);
        if (!query.isBlank()) {
            if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        }
        Page<Recipe> result = !query.isBlank()
                ? recipes.findBySearchNameContainingAndDeletedAtIsNull(query, pageable)
                : recipes.findAllByDeletedAtIsNull(pageable);
        return recipeSummaryPage(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchOwnedRecipes(AppUser user, String query, int page, int size) {
        Pageable pageable = recipePageable(page, size);
        query = SearchTextNormalizer.normalize(query);
        Page<Recipe> result = hasRecipeQuery(query)
                ? recipes.findByCreatedByIdAndSearchNameContainingAndDeletedAtIsNull(user.getId(), query, pageable)
                : recipes.findByCreatedByIdAndDeletedAtIsNull(user.getId(), pageable);
        return recipeSummaryPage(result);
    }

    @Transactional(readOnly = true)
    public List<RecipeOwnerResponse> recipeAuthors(AppUser user) {
        return recipes.findAuthorCountsExcluding(user.getId()).stream()
                .map(author -> new RecipeOwnerResponse(author.getOwnerId(), author.getOwnerName(), author.getRecipeCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchRecipesByOwner(Long ownerId, String query, int page, int size) {
        Pageable pageable = recipePageable(page, size);
        query = SearchTextNormalizer.normalize(query);
        Page<Recipe> result = hasRecipeQuery(query)
                ? recipes.findByCreatedByIdAndSearchNameContainingAndDeletedAtIsNull(ownerId, query, pageable)
                : recipes.findByCreatedByIdAndDeletedAtIsNull(ownerId, pageable);
        return recipeSummaryPage(result);
    }

    @Transactional(readOnly = true)
    public RecipeResponse findRecipe(Long id) {
        Recipe recipe = recipes.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Receta no encontrada."));
        return toRecipeResponse(recipe);
    }

    @Transactional
    public RecipeResponse createRecipe(AppUser user, CreateRecipeRequest request) {
        checkArchived(ingredientFoods(request.ingredients()), request.acknowledgedArchivedFoodIds());
        Recipe recipe = new Recipe();
        recipe.setName(request.name().trim());
        recipe.setDescription(clean(request.description()));
        recipe.setCreatedBy(user);
        replaceRecipeIngredients(recipe, request, true);
        applyRecipeWeights(recipe, request, true);
        applyRecipeTotals(recipe);
        return toRecipeResponse(recipes.save(recipe));
    }

    @Transactional
    public RecipeFromMealResponse createRecipeFromMeal(AppUser user, CreateRecipeFromMealRequest request) {
        LocalDate logDate = request.logDate() == null ? LocalDate.now() : request.logDate();
        List<FoodLog> logs = foodLogsForRecipeCreation(user, request.mealType(), logDate);
        checkArchived(logs.stream().flatMap(log -> logFoods(log).stream()).toList(), request.acknowledgedArchivedFoodIds());
        if (logs.isEmpty()) {
            throw new BadRequestException("No hay registros para esa comida y fecha.");
        }

        Map<RecipeIngredientKey, AggregatedRecipeIngredient> aggregated = new LinkedHashMap<>();
        List<String> skippedItems = new ArrayList<>();
        for (FoodLog log : logs) {
            if (log.getItemType() == MealItemType.AI_ESTIMATE && log.getFood() == null) {
                List<AiEstimateItem> aiItems = aiEstimateItems(log);
                for (int itemIndex = 0; itemIndex < aiItems.size(); itemIndex++) {
                    AiEstimateItem aiItem = aiItems.get(itemIndex);
                    Food food = materializeAiEstimateItem(user,
                            "food-log:" + log.getId() + ":item:" + itemIndex, aiItem, request.acknowledgedArchivedFoodIds());
                    BigDecimal multiplier = log.getQuantity() == null ? BigDecimal.ONE : log.getQuantity();
                    addAggregatedIngredient(aggregated, food,
                            aiItem.estimatedGrams().multiply(multiplier), FoodUnit.GRAM);
                }
                continue;
            }
            if (log.getItemType() == MealItemType.FOOD || log.getItemType() == MealItemType.AI_ESTIMATE) {
                addAggregatedIngredient(aggregated, requireActiveFood(log.getFood()), log.getQuantity(), log.getUnit());
            } else if (log.getItemType() == MealItemType.RECIPE) {
                flattenRecipeLog(log, aggregated);
            } else {
                throw new BadRequestException("El registro de comida tiene un tipo inválido.");
            }
        }
        if (aggregated.isEmpty()) {
            throw new BadRequestException("No quedan ingredientes válidos para crear la receta.");
        }

        List<RecipeIngredientRequest> ingredients = aggregated.values().stream()
                .map(item -> new RecipeIngredientRequest(item.food().getId(), item.quantity(), item.unit()))
                .toList();
        CreateRecipeRequest recipeRequest = new CreateRecipeRequest(request.name(), request.description(), null,
                request.cookedTotalWeightGrams(), false, ingredients);
        Recipe recipe = new Recipe();
        recipe.setName(request.name().trim());
        recipe.setDescription(clean(request.description()));
        recipe.setCreatedBy(user);
        replaceRecipeIngredients(recipe, recipeRequest, true);
        applyRecipeWeights(recipe, recipeRequest, true);
        applyRecipeTotals(recipe);
        RecipeResponse response = toRecipeResponse(recipes.save(recipe));
        return new RecipeFromMealResponse(response, List.copyOf(skippedItems));
    }

    private List<FoodLog> foodLogsForRecipeCreation(AppUser user, MealType mealType, LocalDate logDate) {
        List<FoodLog> logs = foodLogs.findByUserAndMealTypeAndLogDateWithRecipeIngredients(user, mealType, logDate);
        if (!logs.isEmpty()) {
            // Hibernate cannot fetch two List associations in one query. The second graph initializes
            // adjusted ingredients in the same persistence context without dropping recipe ingredients.
            foodLogs.findByUserAndMealTypeAndLogDateWithAdjustedIngredients(user, mealType, logDate);
        }
        return logs;
    }

    private void flattenRecipeLog(FoodLog log, Map<RecipeIngredientKey, AggregatedRecipeIngredient> target) {
        Recipe recipe = log.getRecipe();
        if (recipe == null) {
            throw new BadRequestException("El registro de receta no tiene una receta asociada.");
        }
        if (!log.getRecipeIngredients().isEmpty()) {
            if (log.getUnit() != FoodUnit.PORTION) {
                throw new BadRequestException("No se pueden usar recetas ajustadas registradas en gramos cocidos.");
            }
            for (FoodLogRecipeIngredient ingredient : log.getRecipeIngredients()) {
                addAggregatedIngredient(target, requireActiveFood(ingredient.getFood()),
                        multiplyIngredientQuantity(ingredient.getQuantity(), log.getQuantity()), ingredient.getUnit());
            }
            return;
        }

        BigDecimal multiplier;
        if (log.getUnit() == FoodUnit.PORTION) {
            multiplier = log.getQuantity();
        } else if (log.getUnit() == FoodUnit.GRAM) {
            BigDecimal cookedWeight = log.getRecipeCookedTotalWeightGrams();
            if (cookedWeight == null || cookedWeight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("La receta necesita un peso total cocido capturado para aplanarse en gramos.");
            }
            multiplier = log.getQuantity().divide(cookedWeight, 8, RoundingMode.HALF_UP);
        } else {
            throw new BadRequestException("Las recetas solo se pueden aplanar por porción o por gramos cocidos.");
        }
        flattenRecipeIngredients(recipe, multiplier, target, new HashSet<>());
    }

    private void flattenRecipeIngredients(Recipe recipe, BigDecimal multiplier,
            Map<RecipeIngredientKey, AggregatedRecipeIngredient> target, Set<Long> visited) {
        if (recipe == null || recipe.getId() == null || !visited.add(recipe.getId())) {
            throw new BadRequestException("No se puede aplanar una receta con referencias circulares.");
        }
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            BigDecimal scaledQuantity = multiplyIngredientQuantity(ingredient.getQuantity(), multiplier);
            if (ingredient.getFood() != null) {
                addAggregatedIngredient(target, requireActiveFood(ingredient.getFood()), scaledQuantity, ingredient.getUnit());
                continue;
            }
            Recipe nested = ingredient.getIngredientRecipe();
            if (nested == null) throw new BadRequestException("La receta contiene un ingrediente sin referencia.");
            BigDecimal nestedWeight = recipeIngredientWeight(nested);
            BigDecimal nestedMultiplier = ingredient.getQuantity().multiply(multiplier)
                    .divide(nestedWeight, 8, RoundingMode.HALF_UP);
            flattenRecipeIngredients(nested, nestedMultiplier, target, new HashSet<>(visited));
        }
    }

    private BigDecimal recipeIngredientWeight(Recipe recipe) {
        BigDecimal weight = recipeIngredientWeight(recipe);
        return weight;
    }

    private void addAggregatedIngredient(Map<RecipeIngredientKey, AggregatedRecipeIngredient> target, Food food,
            BigDecimal quantity, FoodUnit unit) {
        if (quantity == null || unit == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Un registro de comida contiene una cantidad o unidad inválida.");
        }
        RecipeIngredientKey key = new RecipeIngredientKey(food.getId(), unit);
        AggregatedRecipeIngredient current = target.get(key);
        if (current == null) {
            target.put(key, new AggregatedRecipeIngredient(food, quantity, unit));
        } else {
            target.put(key, new AggregatedRecipeIngredient(food, current.quantity().add(quantity), unit));
        }
    }

    private BigDecimal multiplyIngredientQuantity(BigDecimal quantity, BigDecimal multiplier) {
        if (quantity == null || multiplier == null || quantity.compareTo(BigDecimal.ZERO) <= 0
                || multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Una receta registrada contiene una cantidad inválida.");
        }
        return quantity.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private Food requireActiveFood(Food food) {
        if (food == null) throw new NotFoundException("El registro de comida no tiene un alimento asociado.");
        return food;
    }

    private List<AiEstimateItem> aiEstimateItems(FoodLog log) {
        AiEstimateDetails details = readAiEstimateDetails(log);
        if (details.items() != null && !details.items().isEmpty()) return details.items();
        return List.of(fallbackAiEstimateItem(log));
    }

    private List<AiEstimateItem> aiEstimateItems(DayPresetItemRequest item) {
        try {
            if (item.aiEstimateDetails() != null && !item.aiEstimateDetails().isBlank()) {
                AiEstimateDetails details = objectMapper.readValue(item.aiEstimateDetails(), AiEstimateDetails.class);
                if (details.items() != null && !details.items().isEmpty()) return details.items();
            }
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("No se pudo leer la estimación guardada en el preset.");
        }
        Map<String, BigDecimal> nutrients = (item.nutrients() == null ? List.<NutrientValueResponse>of() : item.nutrients()).stream()
                .filter(nutrient -> nutrient.code() != null && nutrient.value() != null)
                .collect(Collectors.toMap(NutrientValueResponse::code, NutrientValueResponse::value, (left, right) -> right,
                        LinkedHashMap::new));
        return List.of(new AiEstimateItem(item.displayName() == null ? "Comida estimada" : item.displayName(),
                BigDecimal.valueOf(100), FoodCategory.OTHER, FoodPreparation.UNSPECIFIED,
                item.proteinGrams(), item.carbsGrams(), item.fatGrams(), nutrients));
    }

    private AiEstimateItem fallbackAiEstimateItem(FoodLog log) {
        Map<String, BigDecimal> nutrients = log.getNutrientSnapshot().stream()
                .filter(nutrient -> nutrient.getDefinition() != null && nutrient.getValue() != null)
                .collect(Collectors.toMap(nutrient -> nutrient.getDefinition().getCode(), FoodLogNutrient::getValue,
                        (left, right) -> right, LinkedHashMap::new));
        return new AiEstimateItem(log.getAiEstimateName() == null ? "Comida estimada" : log.getAiEstimateName(),
                BigDecimal.valueOf(100), FoodCategory.OTHER, FoodPreparation.UNSPECIFIED,
                log.getProteinGrams() == null ? BigDecimal.ZERO : log.getProteinGrams(),
                log.getCarbsGrams() == null ? BigDecimal.ZERO : log.getCarbsGrams(),
                log.getFatGrams() == null ? BigDecimal.ZERO : log.getFatGrams(), nutrients);
    }

    private record RecipeIngredientKey(Long foodId, FoodUnit unit) { }

    private record AggregatedRecipeIngredient(Food food, BigDecimal quantity, FoodUnit unit) { }

    @Transactional
    public RecipeResponse copyRecipe(AppUser user, Long id) { return copyRecipe(user, id, Set.of()); }

    @Transactional
    public RecipeResponse copyRecipe(AppUser user, Long id, Set<Long> acknowledged) {
        Recipe source = getActiveRecipe(id);
        checkArchived(recipeFoods(source, new LinkedHashSet<>()), acknowledged);
        Recipe copy = new Recipe();
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setCreatedBy(user);
        copy.setTotalWeightGrams(source.getTotalWeightGrams());
        copy.setRawTotalWeightGrams(source.getRawTotalWeightGrams());
        copy.setCookedTotalWeightGrams(source.getCookedTotalWeightGrams());
        copy.setCalories(source.getCalories());
        copy.setProteinGrams(source.getProteinGrams());
        copy.setCarbsGrams(source.getCarbsGrams());
        copy.setFatGrams(source.getFatGrams());
        for (RecipeIngredient sourceIngredient : source.getIngredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(copy);
            ingredient.setFood(sourceIngredient.getFood());
            ingredient.setIngredientRecipe(sourceIngredient.getIngredientRecipe());
            ingredient.setQuantity(sourceIngredient.getQuantity());
            ingredient.setUnit(sourceIngredient.getUnit());
            copy.getIngredients().add(ingredient);
        }
        return toRecipeResponse(recipes.save(copy));
    }

    @Transactional
    public RecipeResponse updateOwnedRecipe(AppUser user, Long id, CreateRecipeRequest request) {
        Recipe recipe = getActiveRecipe(id);
        if (recipe.getCreatedBy() == null || !recipe.getCreatedBy().getId().equals(user.getId())) {
            throw new BadRequestException("Solo podes editar recetas creadas por vos.");
        }
        recipe.setName(request.name().trim());
        recipe.setDescription(clean(request.description()));
        Set<Long> retainedFoodIds = recipeFoods(recipe, new LinkedHashSet<>()).stream().map(Food::getId).collect(Collectors.toSet());
        checkArchived(ingredientFoods(request.ingredients()).stream().filter(food -> !retainedFoodIds.contains(food.getId())).toList(), request.acknowledgedArchivedFoodIds());
        boolean ingredientsChanged = recipeIngredientsChanged(recipe, request.ingredients());
        replaceRecipeIngredients(recipe, request, true);
        applyRecipeWeights(recipe, request, ingredientsChanged);
        applyRecipeTotals(recipe);
        return toRecipeResponse(recipes.save(recipe));
    }

    @Transactional
    public void deleteOwnedRecipe(AppUser user, Long id) {
        Recipe recipe = getActiveRecipe(id);
        if (recipe.getCreatedBy() == null || !recipe.getCreatedBy().getId().equals(user.getId())) {
            throw new BadRequestException("Solo podes borrar recetas creadas por vos.");
        }
        if (foodLogs.existsByRecipeId(id)) {
            throw new BadRequestException("No se puede borrar una receta que ya tiene registros en comidas.");
        }
        if (recipes.existsReferencingRecipe(id)) {
            throw new BadRequestException("No se puede borrar una receta usada como ingrediente en otra receta.");
        }
        recipes.delete(recipe);
    }

    private void replaceRecipeIngredients(Recipe recipe, CreateRecipeRequest request, boolean allowDeletedFoods) {
        recipe.getIngredients().clear();
        for (var item : request.ingredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            if (item.foodId() != null) {
                ingredient.setFood(allowDeletedFoods ? getFood(item.foodId()) : getActiveFood(item.foodId()));
            } else {
                Recipe ingredientRecipe = getActiveRecipe(item.recipeId());
                validateRecipeIngredient(recipe, ingredientRecipe, item.unit());
                ingredient.setIngredientRecipe(ingredientRecipe);
            }
            ingredient.setQuantity(item.quantity());
            ingredient.setUnit(item.unit());
            recipe.getIngredients().add(ingredient);
        }
    }

    private boolean recipeIngredientsChanged(Recipe recipe, List<RecipeIngredientRequest> requests) {
        if (recipe.getIngredients().size() != requests.size()) return true;
        List<RecipeIngredient> remaining = new ArrayList<>(recipe.getIngredients());
        for (RecipeIngredientRequest request : requests) {
            int match = -1;
            for (int index = 0; index < remaining.size(); index++) {
                RecipeIngredient ingredient = remaining.get(index);
                boolean sameReference = request.foodId() != null
                        ? ingredient.getFood() != null && ingredient.getFood().getId().equals(request.foodId())
                        : ingredient.getIngredientRecipe() != null && ingredient.getIngredientRecipe().getId().equals(request.recipeId());
                if (sameReference
                    && ingredient.getUnit() == request.unit()
                    && ingredient.getQuantity().compareTo(request.quantity()) == 0) {
                    match = index;
                    break;
                }
            }
            if (match < 0) return true;
            remaining.remove(match);
        }
        return false;
    }

    private void applyRecipeWeights(Recipe recipe, CreateRecipeRequest request, boolean ingredientsChanged) {
        BigDecimal rawWeight = recipeRawTotalWeight(recipe.getIngredients());
        recipe.setRawTotalWeightGrams(rawWeight);
        // totalWeightGrams remains the raw weight for clients on the previous contract.
        recipe.setTotalWeightGrams(rawWeight);
        if (request.clearCookedTotalWeight() || ingredientsChanged && request.cookedTotalWeightGrams() == null) {
            recipe.setCookedTotalWeightGrams(null);
        } else if (request.cookedTotalWeightGrams() != null) {
            recipe.setCookedTotalWeightGrams(scaleWeight(request.cookedTotalWeightGrams()));
        }
    }

    private Pageable recipePageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    private boolean hasRecipeQuery(String query) {
        if (query == null || query.isBlank()) return false;
        if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        return true;
    }

    @Transactional(readOnly = true)
    public NutritionPreviewResponse previewRecipe(CreateRecipeRequest request) {
        Recipe recipe = new Recipe();
        for (var item : request.ingredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            if (item.foodId() != null) {
                ingredient.setFood(getFood(item.foodId()));
            } else {
                Recipe ingredientRecipe = getActiveRecipe(item.recipeId());
                validateRecipeIngredient(recipe, ingredientRecipe, item.unit());
                ingredient.setIngredientRecipe(ingredientRecipe);
            }
            ingredient.setQuantity(item.quantity());
            ingredient.setUnit(item.unit());
            recipe.getIngredients().add(ingredient);
        }
        applyRecipeWeights(recipe, request, true);
        applyRecipeTotals(recipe);
        return new NutritionPreviewResponse(recipe.getCalories(), recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams());
    }

    @Transactional
    public FoodLogResponse addFoodLog(AppUser user, AddFoodLogRequest request) {
        return addMealLog(user, new AddMealLogRequest(MealItemType.FOOD, request.foodId(), request.mealType(), request.quantity(), request.unit(), request.logDate(), request.acknowledgedArchivedFoodIds()));
    }

    @Transactional
    public FoodLogResponse addMealLog(AppUser user, AddMealLogRequest request) {
        checkArchived(itemFoods(request.itemType(), request.itemId()), request.acknowledgedArchivedFoodIds());
        return addMealLog(user, request, true);
    }

    private FoodLogResponse addMealLog(AppUser user, AddMealLogRequest request, boolean allowDeletedFoods) {
        NutritionPreviewResponse preview;
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setItemType(request.itemType());
        if (request.itemType() == MealItemType.FOOD) {
            Food food = allowDeletedFoods ? getFood(request.itemId()) : getActiveFood(request.itemId());
            preview = preview(food, request.quantity(), request.unit());
            log.setFood(food);
        } else if (request.itemType() == MealItemType.RECIPE) {
            Recipe recipe = getActiveRecipe(request.itemId());
            validateRecipeLogUnit(request.unit(), recipe.getCookedTotalWeightGrams(), false);
            preview = previewRecipeServing(recipe, request.quantity(), request.unit(), recipe.getCookedTotalWeightGrams());
            log.setRecipe(recipe);
            captureRecipeWeights(log, recipe);
        } else {
            throw new BadRequestException("Este tipo de registro solo se puede crear desde una estimación confirmada.");
        }
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(request.unit());
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        applyLogNutrition(log, preview);
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public List<FoodLogResponse> addMealLogs(AppUser user, BatchAddMealLogsRequest request) {
        checkArchived(request.logs().stream().flatMap(item -> (item.sourceLogId() == null ? itemFoods(item.itemType(), item.itemId()) : logFoods(findOwnedFoodLogEntity(user, item.sourceLogId()))).stream()).toList(), request.acknowledgedArchivedFoodIds());
        return request.logs().stream().map(item -> switch (item.itemType()) {
            case AI_ESTIMATE -> copyAiEstimate(user, item);
            case RECIPE -> item.sourceLogId() == null
                    ? addMealLog(user, new AddMealLogRequest(item.itemType(), item.itemId(), item.mealType(), item.quantity(), item.unit(), item.logDate()), true)
                    : copyRecipeMealLog(user, item);
            case FOOD -> addMealLog(user, new AddMealLogRequest(item.itemType(), item.itemId(), item.mealType(), item.quantity(), item.unit(), item.logDate()), true);
        }).toList();
    }

    private FoodLogResponse copyAiEstimate(AppUser user, BatchAddMealLogRequest request) {
        FoodLog source = request.sourceLogId() == null ? null : foodLogs.findByIdAndUserAndItemType(
                request.sourceLogId(), user, MealItemType.AI_ESTIMATE)
                .orElseThrow(() -> new NotFoundException("Estimación reciente no encontrada."));
        if (source != null && source.getUnit() != request.unit()) {
            throw new BadRequestException("La unidad de la estimación copiada debe coincidir con el registro original.");
        }
        BigDecimal ratio = source == null ? BigDecimal.ONE
                : request.quantity().divide(source.getQuantity(), 8, RoundingMode.HALF_UP);
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setItemType(MealItemType.AI_ESTIMATE);
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(request.unit());
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        BigDecimal protein = source == null ? zero(request.proteinGrams()) : NutritionMath.scaled(source.getProteinGrams(), ratio);
        BigDecimal carbs = source == null ? zero(request.carbsGrams()) : NutritionMath.scaled(source.getCarbsGrams(), ratio);
        BigDecimal fat = source == null ? zero(request.fatGrams()) : NutritionMath.scaled(source.getFatGrams(), ratio);
        log.setCalories(source == null
                ? request.calories() == null ? macroCalories(protein, carbs, fat) : request.calories()
                : scaledCalories(source.getCalories(), ratio, protein, carbs, fat));
        log.setProteinGrams(scale(protein));
        log.setCarbsGrams(scale(carbs));
        log.setFatGrams(scale(fat));
        log.setAiEstimateName(source == null ? request.displayName() == null ? "Comida estimada" : request.displayName() : source.getAiEstimateName());
        log.setAiEstimateConfidence(source == null ? request.aiEstimateConfidence() : source.getAiEstimateConfidence());
        log.setAiEstimateDetails(source == null ? request.aiEstimateDetails() : scaledAiDetails(source, ratio));
        if (source == null) {
            for (NutrientValueResponse nutrient : request.nutrients() == null ? List.<NutrientValueResponse>of() : request.nutrients()) {
                if (nutrient.code() == null) continue;
                nutrientDefinitions.findById(nutrient.code()).ifPresent(definition -> {
                    FoodLogNutrient copy = new FoodLogNutrient();
                    copy.setFoodLog(log);
                    copy.setDefinition(definition);
                    copy.setValue(nutrient.value());
                    copy.setKnownValue(nutrient.knownValue() == null ? nutrient.value() : nutrient.knownValue()); copy.setComplete(nutrient.value() != null);
                    copy.setSource(parseSource(nutrient.source()));
                    copy.setStatus(parseStatus(nutrient.status()));
                    log.getNutrientSnapshot().add(copy);
                });
            }
        } else {
            copyScaledNutrients(log, source, ratio);
        }
        return toFoodLogResponse(foodLogs.save(log));
    }

    private FoodLogResponse copyRecipeMealLog(AppUser user, BatchAddMealLogRequest request) {
        FoodLog source = foodLogs.findByIdAndUserAndItemType(request.sourceLogId(), user, MealItemType.RECIPE)
                .orElseThrow(() -> new NotFoundException("Receta reciente no encontrada."));
        if (!source.getRecipe().getId().equals(request.itemId())) {
            throw new BadRequestException("La receta reciente no coincide con su registro original.");
        }
        if (source.getUnit() != request.unit()) {
            throw new BadRequestException("La unidad de la receta copiada debe coincidir con el registro original.");
        }
        FoodLog copy = new FoodLog();
        copy.setUser(user);
        copy.setItemType(MealItemType.RECIPE);
        copy.setRecipe(source.getRecipe());
        copy.setMealType(request.mealType());
        copy.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        copy.setQuantity(request.quantity());
        copy.setUnit(request.unit());
        copy.setRecipeRawTotalWeightGrams(source.getRecipeRawTotalWeightGrams());
        copy.setRecipeCookedTotalWeightGrams(source.getRecipeCookedTotalWeightGrams());
        BigDecimal ratio = request.quantity().divide(source.getQuantity(), 8, RoundingMode.HALF_UP);
        copy.setProteinGrams(NutritionMath.scaled(source.getProteinGrams(), ratio));
        copy.setCarbsGrams(NutritionMath.scaled(source.getCarbsGrams(), ratio));
        copy.setFatGrams(NutritionMath.scaled(source.getFatGrams(), ratio));
        copy.setCalories(scaledCalories(source.getCalories(), ratio, copy.getProteinGrams(), copy.getCarbsGrams(), copy.getFatGrams()));
        for (FoodLogRecipeIngredient ingredient : source.getRecipeIngredients()) {
            FoodLogRecipeIngredient copiedIngredient = new FoodLogRecipeIngredient();
            copiedIngredient.setFoodLog(copy);
            copiedIngredient.setFood(ingredient.getFood());
            copiedIngredient.setUnit(ingredient.getUnit());
            copiedIngredient.setQuantity(ingredient.getQuantity().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
            copy.getRecipeIngredients().add(copiedIngredient);
        }
        copyScaledNutrients(copy, source, ratio);
        return toFoodLogResponse(foodLogs.save(copy));
    }

    private void copyScaledNutrients(FoodLog target, FoodLog source, BigDecimal ratio) {
        source.getNutrientSnapshot().forEach(nutrient -> {
            FoodLogNutrient copy = new FoodLogNutrient();
            copy.setFoodLog(target);
            copy.setDefinition(nutrient.getDefinition());
            copy.setValue(NutritionMath.scaled(nutrient.getValue(), ratio));
            copy.setKnownValue(NutritionMath.scaled(nutrient.getKnownValue() == null ? nutrient.getValue() : nutrient.getKnownValue(), ratio)); copy.setComplete(nutrient.isComplete());
            copy.setSource(nutrient.getSource());
            copy.setStatus(nutrient.getStatus());
            target.getNutrientSnapshot().add(copy);
        });
    }

    private String scaledAiDetails(FoodLog source, BigDecimal ratio) {
        try {
            AiEstimateDetails details = readAiEstimateDetails(source);
            List<AiEstimateItem> items = details.items().stream().map(item -> new AiEstimateItem(item.name(),
                    scaleWeight(item.estimatedGrams().multiply(ratio)), item.category(), item.preparation(),
                    scale(item.proteinGrams().multiply(ratio)), scale(item.carbsGrams().multiply(ratio)),
                    scale(item.fatGrams().multiply(ratio)), scaleNutrientMap(item.nutrients(), ratio), item.catalogFoodId(),
                    item.catalogMatchType(), item.catalogMatchConfidence())).toList();
            return objectMapper.writeValueAsString(new AiEstimateDetails(details.description(), details.context(), details.assumptions(), items));
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("No se pudo copiar la estimación reciente.");
        }
    }

    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private Integer scaledCalories(Integer sourceCalories, BigDecimal ratio, BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
        if (sourceCalories == null) return macroCalories(protein, carbs, fat);
        try {
            return BigDecimal.valueOf(sourceCalories).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("La cantidad supera el rango de calorías permitido.");
        }
    }

    private Map<String, BigDecimal> scaleNutrientMap(Map<String, BigDecimal> nutrients, BigDecimal ratio) {
        if (nutrients == null) return Map.of();
        return nutrients.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> scale(entry.getValue().multiply(ratio)),
                        (first, second) -> second, LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public List<RecentMealResponse> recentMeals(AppUser user, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 50);
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusMonths(12);
        List<Long> recentLogIds = foodLogs.findRecentMealGroupLogIds(user.getId(), start, end, limit);
        if (recentLogIds.isEmpty()) return List.of();
        List<FoodLog> logs = new ArrayList<>(foodLogs.findByIdIn(recentLogIds));
        logs.sort(Comparator.comparing(FoodLog::getLogDate).reversed()
                .thenComparing(FoodLog::getMealType)
                .thenComparing(FoodLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FoodLog::getId, Comparator.reverseOrder()));
        Map<String, List<FoodLog>> grouped = logs.stream().collect(Collectors.groupingBy(
                log -> log.getLogDate() + ":" + log.getMealType(), LinkedHashMap::new, Collectors.toList()));
        Set<String> signatures = new LinkedHashSet<>();
        List<RecentMealResponse> result = new ArrayList<>();
        for (List<FoodLog> bracket : grouped.values()) {
            if (bracket.isEmpty()) continue;
            String signature = bracket.stream().map(this::recentMealItemSignature).sorted().collect(Collectors.joining("|"));
            if (!signatures.add(bracket.get(0).getMealType() + ":" + signature)) continue;
            BigDecimal protein = bracket.stream().map(FoodLog::getProteinGrams).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal carbs = bracket.stream().map(FoodLog::getCarbsGrams).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal fat = bracket.stream().map(FoodLog::getFatGrams).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new RecentMealResponse(bracket.get(0).getLogDate(), bracket.get(0).getMealType(),
                    label(bracket.get(0).getMealType()), bracket.stream().map(FoodLog::getCalories).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(), scale(protein), scale(carbs),
                    scale(fat), bracket.stream().map(this::toFoodLogResponse).toList()));
            if (result.size() >= limit) break;
        }
        return result;
    }

    private String recentMealItemSignature(FoodLog log) {
        String item = log.getItemType() + ":" + (log.getFood() == null
                ? log.getRecipe() == null
                        ? log.getAiEstimateName() + ":" + (log.getAiEstimateDetails() == null ? "" : log.getAiEstimateDetails())
                        : log.getRecipe().getId()
                : log.getFood().getId());
        String ingredients = log.getRecipeIngredients().stream()
                .map(ingredient -> ingredient.getFood().getId() + "=" + ingredient.getQuantity() + "=" + ingredient.getUnit())
                .collect(Collectors.joining(","));
        return item + ":" + log.getQuantity() + ":" + log.getUnit() + ":" + ingredients;
    }

    @Transactional
    public FoodLogResponse addRecipeMealLog(AppUser user, AddRecipeMealLogRequest request) {
        checkArchived(ingredientFoods(request.ingredients()), request.acknowledgedArchivedFoodIds());
        Recipe recipe = getActiveRecipe(request.recipeId());
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setItemType(MealItemType.RECIPE);
        log.setRecipe(recipe);
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(FoodUnit.PORTION);
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        captureRecipeWeights(log, recipe);
        replaceRecipeLogIngredients(log, recipe, request.ingredients());
        applyLogNutrition(log, previewRecipeServing(log, request.quantity(), FoodUnit.PORTION));
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public List<FoodLogResponse> confirmAiEstimate(AppUser user, ConfirmAiEstimateRequest request) {
        checkArchived(request.items().stream().filter(item -> item.foodId() != null).map(item -> getFood(item.foodId())).toList(), request.acknowledgedArchivedFoodIds());
        LocalDate logDate = request.logDate() == null ? LocalDate.now() : request.logDate();
        return request.items().stream().map(item -> confirmAiEstimateItem(user, request.mealType(), logDate, item)).toList();
    }

    private FoodLogResponse confirmAiEstimateItem(AppUser user, MealType mealType, LocalDate logDate,
            ConfirmAiEstimateItem item) {
        Food food = item.foodId() == null ? aiFoodMatcher.resolve(item.proposal()).orElse(null) : getFood(item.foodId());
        if (food == null) return createAiEstimateLog(user, mealType, logDate, item.proposal(), item.servedGrams());
        NutritionPreviewResponse preview = preview(food, item.servedGrams(), FoodUnit.GRAM);
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setFood(food);
        log.setItemType(MealItemType.FOOD);
        log.setMealType(mealType);
        log.setQuantity(item.servedGrams());
        log.setUnit(FoodUnit.GRAM);
        log.setLogDate(logDate);
        applyLogNutrition(log, preview);
        FoodLog savedLog = foodLogs.save(log);
        return toFoodLogResponse(savedLog);
    }

    private FoodLogResponse createAiEstimateLog(AppUser user, MealType mealType, LocalDate logDate,
            AiEstimateFoodProposal proposal, BigDecimal servedGrams) {
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setItemType(MealItemType.AI_ESTIMATE);
        log.setMealType(mealType);
        log.setQuantity(BigDecimal.ONE);
        log.setUnit(FoodUnit.PORTION);
        log.setLogDate(logDate);
        log.setAiEstimateName(proposal.name().trim());
        BigDecimal ratio = servedGrams.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        log.setProteinGrams(scale(proposal.proteinGrams().multiply(ratio)));
        log.setCarbsGrams(scale(proposal.carbsGrams().multiply(ratio)));
        log.setFatGrams(scale(proposal.fatGrams().multiply(ratio)));
        log.setCalories(macroCalories(log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams()));
        addAiLogNutrients(log, proposal.nutrients(), ratio);
        try {
            AiEstimateItem item = new AiEstimateItem(proposal.name(), servedGrams, proposal.category(), proposal.preparation(),
                    proposal.proteinGrams().multiply(ratio), proposal.carbsGrams().multiply(ratio),
                    proposal.fatGrams().multiply(ratio), proposal.nutrients() == null ? Map.of()
                            : proposal.nutrients().entrySet().stream().collect(Collectors.toMap(
                                    Map.Entry::getKey, entry -> scale(entry.getValue().multiply(ratio)))));
            log.setAiEstimateDetails(objectMapper.writeValueAsString(new AiEstimateDetails("", "", List.of(), List.of(item))));
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("No se pudo guardar la estimación.");
        }
        return toFoodLogResponse(foodLogs.save(log));
    }

    private void addAiLogNutrients(FoodLog log, Map<String, BigDecimal> values, BigDecimal ratio) {
        if (values == null) return;
        values.forEach((code, value) -> nutrientDefinitions.findById(code).ifPresent(definition -> {
            FoodLogNutrient nutrient = new FoodLogNutrient();
            nutrient.setFoodLog(log);
            nutrient.setDefinition(definition);
            nutrient.setValue(scale(value.multiply(ratio)));
            nutrient.setSource(NutrientSource.AI);
            nutrient.setStatus(NutrientStatus.ESTIMATED);
            log.getNutrientSnapshot().add(nutrient);
        }));
    }

    @Transactional
    public FoodLogResponse updateAiEstimate(AppUser user, Long logId, UpdateAiEstimateRequest request) {
        FoodLog log = ownedAiEstimateLog(user, logId);
        AiEstimateDetails existing = readAiEstimateDetails(log);
        List<String> assumptions = request.assumptions() == null
                ? existing.assumptions() == null ? List.of() : existing.assumptions()
                : request.assumptions();
        applyAiEstimate(log, request.name(), request.description(), request.context(), request.confidence(), assumptions,
                request.items(), request.mealType(), request.logDate());
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public FoodResponse saveAiEstimateItemToCatalog(AppUser user, Long logId, int itemIndex,
            SaveAiEstimateItemRequest request) {
        FoodLog log = foodLogs.findOwnedForCatalog(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        if (log.getItemType() != MealItemType.AI_ESTIMATE) {
            throw new BadRequestException("Este registro no corresponde a una estimación de IA.");
        }
        AiEstimateDetails details = readAiEstimateDetails(log);
        if (itemIndex < 0 || itemIndex >= details.items().size()) {
            throw new BadRequestException("El item de la estimación no existe.");
        }
        AiEstimateItem item = details.items().get(itemIndex);
        validateAiEstimateItems(List.of(item));
        String sourceId = "food-log:" + log.getId() + ":item:" + itemIndex;
        Food food = materializeAiEstimateItem(user, sourceId, new AiEstimateItem(item.name(), item.estimatedGrams(),
                request.category(), request.preparation(), item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients()));
        if (request.tags() != null) {
            food.setTags(request.tags().stream()
                    .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
            food = foods.saveAndFlush(food);
            semanticFoods.indexIfAvailable(food);
        }
        return toFoodResponse(food);
    }

    private Food materializeAiEstimateItem(AppUser user, String sourceId, AiEstimateItem item) { return materializeAiEstimateItem(user, sourceId, item, Set.of()); }

    private Food materializeAiEstimateItem(AppUser user, String sourceId, AiEstimateItem item, Set<Long> acknowledged) {
        FoodCategory category = item.category() == null ? FoodCategory.OTHER : item.category();
        FoodPreparation preparation = item.preparation() == null ? FoodPreparation.UNSPECIFIED : item.preparation();
        BigDecimal itemToHundred = BigDecimal.valueOf(100).divide(item.estimatedGrams(), 4, RoundingMode.HALF_UP);
        String fingerprint = nutrientFingerprint(scaleNutrientMap(item.nutrients(), itemToHundred));
        String identityKey = SearchTextNormalizer.normalize(item.name()) + "|" + category + "|" + preparation + "|"
                + perHundred(item.proteinGrams(), item.estimatedGrams()).stripTrailingZeros().toPlainString() + "|"
                + perHundred(item.carbsGrams(), item.estimatedGrams()).stripTrailingZeros().toPlainString() + "|"
                + perHundred(item.fatGrams(), item.estimatedGrams()).stripTrailingZeros().toPlainString() + "|" + fingerprint;
        if (postgres) jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { while (rs.next()) { } }, identityKey);
        Optional<Food> identity = foods.findActiveBySearchName(SearchTextNormalizer.normalize(item.name()), ModerationStatus.APPROVED)
                .stream().filter(food -> "AI_ESTIMATE".equals(food.getSource())
                        && food.getCategory() == category
                        && (food.getPreparation() == null ? FoodPreparation.UNSPECIFIED : food.getPreparation()) == preparation
                        && (food.getBrand() == null || food.getBrand().isBlank())
                        && food.getProteinGrams().compareTo(perHundred(item.proteinGrams(), item.estimatedGrams())) == 0
                        && food.getCarbsGrams().compareTo(perHundred(item.carbsGrams(), item.estimatedGrams())) == 0
                        && food.getFatGrams().compareTo(perHundred(item.fatGrams(), item.estimatedGrams())) == 0
                        && (fingerprint.equals(food.getNutritionFingerprint()) || fingerprint.equals(nutrientFingerprint(
                                food.getNutrients().stream().collect(Collectors.toMap(n -> n.getDefinition().getCode(),
                                        FoodNutrient::getValue, (first, second) -> second)))))).findFirst();
        if (identity.isPresent()) return identity.get();
        Optional<Food> saved = foods.findBySourceAndSourceId("AI_ESTIMATE", sourceId);
        if (saved.isPresent()) {
            Food food = saved.get();
            if (food.getDeletedAt() != null) {
                checkArchived(List.of(food), acknowledged);
            }
            return food;
        }
        validateAiEstimateItems(List.of(item));
        BigDecimal estimatedGrams = item.estimatedGrams();
        BigDecimal ratio = itemToHundred;
        Food food = new Food();
        food.setName(item.name().trim());
        food.setCategory(category);
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setProteinGrams(scale(item.proteinGrams().multiply(ratio)));
        food.setCarbsGrams(scale(item.carbsGrams().multiply(ratio)));
        food.setFatGrams(scale(item.fatGrams().multiply(ratio)));
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        addAiNutrients(food, item.nutrients(), ratio);
        food.setPreparation(preparation);
        food.setPreparationSource("Estimado por IA");
        initializeIdentityCookedYield(food);
        food.setSource("AI_ESTIMATE");
        food.setSourceId(sourceId);
        food.setNutritionFingerprint(fingerprint);
        food.setCreatedBy(user);
        food.setCreatedAt(OffsetDateTime.now());
        food.setModerationStatus(ModerationStatus.APPROVED);
        Food savedFood = foods.saveAndFlush(food);
        semanticFoods.indexIfAvailable(savedFood);
        return savedFood;
    }

    private String nutrientFingerprint(Map<String, BigDecimal> nutrients) {
        if (nutrients == null || nutrients.isEmpty()) return md5("");
        String canonical = nutrients.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(";"));
        return md5(canonical);
    }

    private String md5(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private void applyAiEstimate(FoodLog log, String name, String description, String context, int confidence,
            List<String> assumptions, List<AiEstimateItem> items, MealType mealType, LocalDate logDate) {
        List<AiEstimateItem> normalizedItems = normalizeAiEstimateItems(items);
        validateAiEstimateItems(normalizedItems);
        BigDecimal protein = normalizedItems.stream().map(item -> scale(item.proteinGrams())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal carbs = normalizedItems.stream().map(item -> scale(item.carbsGrams())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fat = normalizedItems.stream().map(item -> scale(item.fatGrams())).reduce(BigDecimal.ZERO, BigDecimal::add);
        log.setMealType(mealType);
        log.setQuantity(BigDecimal.ONE);
        log.setUnit(FoodUnit.PORTION);
        log.setLogDate(logDate == null ? log.getLogDate() == null ? LocalDate.now() : log.getLogDate() : logDate);
        log.setProteinGrams(scale(protein));
        log.setCarbsGrams(scale(carbs));
        log.setFatGrams(scale(fat));
        log.setCalories(macroCalories(log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams()));
        log.setAiEstimateName(name.trim());
        log.setAiEstimateConfidence(Math.max(0, Math.min(100, confidence)));
        Map<String, BigDecimal> nutrientTotals = new LinkedHashMap<>();
        normalizedItems.forEach(item -> {
            if (item.nutrients() != null) item.nutrients().forEach((code, value) ->
                    nutrientTotals.merge(code, value, BigDecimal::add));
        });
        log.getNutrientSnapshot().removeIf(nutrient -> !nutrientTotals.containsKey(nutrient.getDefinition().getCode()));
        log.getNutrientSnapshot().forEach(nutrient -> {
            nutrient.setValue(scale(nutrientTotals.remove(nutrient.getDefinition().getCode())));
            nutrient.setSource(NutrientSource.AI);
            nutrient.setStatus(NutrientStatus.ESTIMATED);
        });
        addAiLogNutrients(log, nutrientTotals, BigDecimal.ONE);
        try {
            log.setAiEstimateDetails(objectMapper.writeValueAsString(new AiEstimateDetails(description, context,
                    assumptions == null ? List.of() : assumptions, normalizedItems)));
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("No se pudo guardar la estimación.");
        }
    }

    private List<AiEstimateItem> normalizeAiEstimateItems(List<AiEstimateItem> items) {
        return items.stream().map(item -> new AiEstimateItem(item.name(), item.estimatedGrams(),
                item.category() == null ? FoodCategory.OTHER : item.category(),
                item.preparation() == null ? FoodPreparation.UNSPECIFIED : item.preparation(),
                item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients(), item.catalogFoodId(),
                item.catalogMatchType(), item.catalogMatchConfidence())).toList();
    }

    private void validateAiEstimateItems(List<AiEstimateItem> items) {
        for (var item : items) {
            if (item.estimatedGrams().compareTo(BigDecimal.valueOf(3000)) > 0
                    || item.proteinGrams().compareTo(BigDecimal.valueOf(500)) > 0
                    || item.carbsGrams().compareTo(BigDecimal.valueOf(1000)) > 0
                    || item.fatGrams().compareTo(BigDecimal.valueOf(500)) > 0) {
                throw new BadRequestException("Revisá los valores estimados antes de guardarlos.");
            }
        }
    }

    private FoodLog ownedAiEstimateLog(AppUser user, Long logId) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        if (log.getItemType() != MealItemType.AI_ESTIMATE) {
            throw new BadRequestException("Este registro no corresponde a una estimación de IA.");
        }
        return log;
    }

    private AiEstimateDetails readAiEstimateDetails(FoodLog log) {
        if (log.getAiEstimateDetails() == null || log.getAiEstimateDetails().isBlank()) {
            return new AiEstimateDetails("", "", List.of(), List.of());
        }
        try {
            return objectMapper.readValue(log.getAiEstimateDetails(), AiEstimateDetails.class);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("No se pudo leer la estimación guardada.");
        }
    }

    private record AiEstimateDetails(String description, String context, List<String> assumptions, List<AiEstimateItem> items) {
    }

    @Transactional
    public void deleteFoodLog(AppUser user, Long logId) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        foodLogs.delete(log);
    }

    @Transactional
    public void deleteMealLogs(AppUser user, MealType mealType, LocalDate date) {
        foodLogs.deleteAll(foodLogs.findByUserAndMealTypeAndLogDate(user, mealType, date == null ? LocalDate.now() : date));
    }

    @Transactional
    public FoodLogResponse updateFoodLog(AppUser user, Long logId, UpdateFoodLogRequest request) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        if (request.itemId() != null && log.getItemType() == MealItemType.FOOD && !request.itemId().equals(log.getFood().getId())) {
            Food newFood = getFood(request.itemId());
            checkArchived(List.of(newFood), request.acknowledgedArchivedFoodIds());
            log.setFood(newFood);
        }
        NutritionPreviewResponse preview;
        if (log.getItemType() == MealItemType.RECIPE) {
            validateRecipeLogUnit(request.unit(), log.getRecipeCookedTotalWeightGrams(), !log.getRecipeIngredients().isEmpty());
            preview = previewRecipeServing(log, request.quantity(), request.unit());
        } else if (log.getItemType() == MealItemType.FOOD) {
            preview = preview(log.getFood(), request.quantity(), request.unit());
        } else {
            preview = new NutritionPreviewResponse(log.getCalories(), log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams());
        }
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(request.unit());
        log.setLogDate(request.logDate() == null ? log.getLogDate() : request.logDate());
        applyLogNutrition(log, preview);
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public FoodLogResponse updateRecipeLogIngredients(AppUser user, Long logId, UpdateRecipeLogIngredientsRequest request) {
        FoodLog log = ownedRecipeLog(user, logId);
        rejectAdjustedRecipeGrams(log);
        checkArchived(ingredientFoods(request.ingredients()).stream().filter(food -> logFoods(log).stream().noneMatch(existing -> existing.getId().equals(food.getId()))).toList(), request.acknowledgedArchivedFoodIds());
        replaceRecipeLogIngredients(log, log.getRecipe(), request.ingredients());
        NutritionPreviewResponse preview = previewRecipeServing(log, log.getQuantity(), FoodUnit.PORTION);
        applyLogNutrition(log, preview);
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public FoodLogResponse updateRecipeFoodLog(AppUser user, Long logId, UpdateRecipeFoodLogRequest request) {
        FoodLog log = ownedRecipeLog(user, logId);
        rejectAdjustedRecipeGrams(log);
        checkArchived(ingredientFoods(request.recipeIngredients()).stream().filter(food -> logFoods(log).stream().noneMatch(existing -> existing.getId().equals(food.getId()))).toList(), request.acknowledgedArchivedFoodIds());
        replaceRecipeLogIngredients(log, log.getRecipe(), request.recipeIngredients());
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        if (request.logDate() != null) log.setLogDate(request.logDate());
        applyLogNutrition(log, previewRecipeServing(log, request.quantity(), FoodUnit.PORTION));
        return toFoodLogResponse(foodLogs.save(log));
    }

    private void replaceRecipeLogIngredients(FoodLog log, Recipe recipe, List<RecipeIngredientRequest> requests) {
        if (recipe.getIngredients().stream().anyMatch(item -> item.getIngredientRecipe() != null)) {
            throw new BadRequestException("No se pueden ajustar recetas usadas como ingredientes en un registro diario.");
        }
        Set<Long> baseFoodIds = recipe.getIngredients().stream().map(item -> item.getFood().getId()).collect(Collectors.toSet());
        Set<Long> requestedFoodIds = requests.stream().map(RecipeIngredientRequest::foodId).collect(Collectors.toSet());
        if (requestedFoodIds.size() != requests.size() || !requestedFoodIds.equals(baseFoodIds)) {
            throw new BadRequestException("Solo podes ajustar los ingredientes originales de la receta.");
        }
        log.getRecipeIngredients().clear();
        for (RecipeIngredientRequest request : requests) {
            FoodLogRecipeIngredient ingredient = new FoodLogRecipeIngredient();
            ingredient.setFoodLog(log);
            ingredient.setFood(getFood(request.foodId()));
            ingredient.setQuantity(request.quantity());
            ingredient.setUnit(request.unit());
            log.getRecipeIngredients().add(ingredient);
        }
    }

    @Transactional
    public void resetRecipeLogIngredients(AppUser user, Long logId) { resetRecipeLogIngredients(user, logId, Set.of()); }

    @Transactional
    public void resetRecipeLogIngredients(AppUser user, Long logId, Set<Long> acknowledged) {
        FoodLog log = ownedRecipeLog(user, logId);
        checkArchived(recipeFoods(log.getRecipe(), new LinkedHashSet<>()), acknowledged);
        rejectAdjustedRecipeGrams(log);
        log.getRecipeIngredients().clear();
        applyLogNutrition(log, previewRecipeServing(log.getRecipe(), log.getQuantity(), log.getUnit(),
                log.getRecipeCookedTotalWeightGrams()));
        foodLogs.save(log);
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        NutritionPlan plan = profileService.resolvePlan(user, targetDate);
        List<FoodLog> logs = foodLogs.findByUserAndLogDate(user, targetDate);
        BigDecimal protein = sum(logs, FoodLog::getProteinGrams);
        BigDecimal carbs = sum(logs, FoodLog::getCarbsGrams);
        BigDecimal fat = sum(logs, FoodLog::getFatGrams);
        Map<String, NutrientValueResponse> dailyNutrients = new LinkedHashMap<>();
        logs.forEach(log -> mergeNutrients(dailyNutrients, log.getNutrientSnapshot().stream().map(this::toNutrientResponse).toList()));
        int calories = logs.stream().map(FoodLog::getCalories).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        Map<MealType, List<FoodLog>> byMeal = logs.stream().collect(Collectors.groupingBy(FoodLog::getMealType));
        List<MealSummary> meals = Arrays.stream(MealType.values()).map(meal -> {
            List<FoodLogResponse> items = byMeal.getOrDefault(meal, List.of()).stream().map(this::toFoodLogResponse).toList();
            BigDecimal mealProtein = sumResponses(items, FoodLogResponse::proteinGrams);
            BigDecimal mealCarbs = sumResponses(items, FoodLogResponse::carbsGrams);
            BigDecimal mealFat = sumResponses(items, FoodLogResponse::fatGrams);
            return new MealSummary(meal, label(meal), items.stream().map(FoodLogResponse::calories).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(), mealProtein,
                    mealCarbs, mealFat, items);
        }).toList();
        return new DashboardResponse(targetDate, plan.getDailyCalories(), calories,
                Math.max(0, plan.getDailyCalories() - calories),
                List.of(
                        progress("protein", "Proteina", protein, BigDecimal.valueOf(plan.getProteinGoalGrams())),
                        progress("carbs", "Carbohidratos", carbs, BigDecimal.valueOf(plan.getCarbsGoalGrams())),
                        progress("fat", "Grasas", fat, BigDecimal.valueOf(plan.getFatGoalGrams()))),
                meals,
                profileService.activePlan(user, targetDate), dailyNutrients.values().stream().toList());
    }

    @Transactional(readOnly = true)
    public List<MealTypeResponse> mealTypes() {
        return Arrays.stream(MealType.values()).map(meal -> new MealTypeResponse(meal, label(meal))).toList();
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(AppUser user, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<FoodLogRepository.DayNutritionProjection> summaries = foodLogs.summarizeByDate(user, ym.atDay(1), ym.atEndOfMonth());
        Map<LocalDate, FoodLogRepository.DayNutritionProjection> byDate = summaries.stream()
                .collect(Collectors.toMap(FoodLogRepository.DayNutritionProjection::getDate, item -> item));
        List<NutritionPlan> plans = profileService.plansForRange(user, ym.atDay(1), ym.atEndOfMonth());
        LocalDate today = LocalDate.now();
        List<DaySummary> days = ym.atDay(1).datesUntil(ym.atEndOfMonth().plusDays(1)).map(date -> {
            FoodLogRepository.DayNutritionProjection summary = byDate.get(date);
            long count = summary == null ? 0 : summary.getRecordCount();
            boolean energyComplete = count > 0 && summary.getEnergyCount() == count;
            boolean proteinComplete = count > 0 && summary.getProteinCount() == count;
            boolean carbsComplete = count > 0 && summary.getCarbsCount() == count;
            boolean fatComplete = count > 0 && summary.getFatCount() == count;
            boolean complete = energyComplete && proteinComplete && carbsComplete && fatComplete;
            int calories = summary == null ? 0 : Math.toIntExact(summary.getCalories());
            BigDecimal protein = summary == null ? BigDecimal.ZERO : scale(summary.getProteinGrams());
            BigDecimal carbs = summary == null ? BigDecimal.ZERO : scale(summary.getCarbsGrams());
            BigDecimal fat = summary == null ? BigDecimal.ZERO : scale(summary.getFatGrams());
            NutritionPlan plan = profileService.resolvePlanFromRange(user, date, plans);
            return new DaySummary(date, calories, plan.getDailyCalories(), proteinComplete ? protein : null,
                    carbsComplete ? carbs : null, fatComplete ? fat : null,
                    !date.isAfter(today) && energyComplete && calories <= plan.getDailyCalories(), plan.getId(), plan.getName(),
                    count, energyComplete, complete, count == 0 ? "NONE" : complete ? "COMPLETE" : "PARTIAL", protein, carbs, fat);
        }).toList();
        List<DaySummary> eligible = days.stream().filter(day -> !day.date().isAfter(today) && day.recordCount() > 0 && day.energyComplete()).toList();
        Integer average = eligible.isEmpty() ? null : (int) Math.round(eligible.stream().mapToInt(DaySummary::caloriesConsumed).average().orElseThrow());
        long completed = eligible.stream().filter(DaySummary::goalReached).count();
        return new HistoryResponse(year, month, days, average, completed, eligible.size());
    }

    private void checkArchived(List<Food> referenced, Set<Long> acknowledged) {
        List<Food> archived = referenced.stream().filter(food -> food.getDeletedAt() != null)
                .filter(food -> acknowledged == null || !acknowledged.contains(food.getId()))
                .collect(Collectors.toMap(Food::getId, food -> food, (left, right) -> left, LinkedHashMap::new)).values().stream().toList();
        if (!archived.isEmpty()) throw new com.scalegrams.common.ArchivedFoodAcknowledgementException(archived);
    }
    private List<Food> recipeFoods(Recipe recipe, Set<Long> visited) {
        if (recipe.getId() != null && !visited.add(recipe.getId())) return List.of();
        return recipe.getIngredients().stream().flatMap(item -> item.getFood() != null ? java.util.stream.Stream.of(item.getFood()) : recipeFoods(item.getIngredientRecipe(), visited).stream()).toList();
    }
    private List<Food> ingredientFoods(List<RecipeIngredientRequest> ingredients) {
        return ingredients.stream().flatMap(item -> item.foodId() != null ? java.util.stream.Stream.of(getFood(item.foodId())) : recipeFoods(getActiveRecipe(item.recipeId()), new LinkedHashSet<>()).stream()).toList();
    }
    private List<Food> itemFoods(MealItemType type, Long id) {
        return type == MealItemType.FOOD ? List.of(getFood(id)) : type == MealItemType.RECIPE ? recipeFoods(getActiveRecipe(id), new LinkedHashSet<>()) : List.of();
    }
    private List<Food> templateFoods(List<DayPresetItemRequest> items) {
        return items.stream().flatMap(item -> itemFoods(item.itemType(), item.itemId()).stream()).toList();
    }
    private List<Food> logFoods(FoodLog log) {
        if (log.getFood() != null) return List.of(log.getFood());
        if (!log.getRecipeIngredients().isEmpty()) return log.getRecipeIngredients().stream().map(FoodLogRecipeIngredient::getFood).toList();
        return log.getRecipe() == null ? List.of() : recipeFoods(log.getRecipe(), new LinkedHashSet<>());
    }
    private FoodLog findOwnedFoodLogEntity(AppUser user, Long id) {
        return foodLogs.findByIdAndUser(id, user).orElseThrow(() -> new NotFoundException("Registro reciente no encontrado."));
    }

    private Food getFood(Long foodId) {
        return foods.findById(foodId).orElseThrow(() -> new NotFoundException("Alimento no encontrado."));
    }

    private Food getActiveFood(Long foodId) {
        Food food = getFood(foodId);
        if (food.getDeletedAt() != null) throw new NotFoundException("Alimento no encontrado.");
        return food;
    }

    private Recipe getActiveRecipe(Long recipeId) {
        return recipes.findByIdAndDeletedAtIsNull(recipeId)
                .orElseThrow(() -> new NotFoundException("Receta no encontrada."));
    }

    @Transactional(readOnly = true)
    public FoodLogResponse findOwnedFoodLog(AppUser user, Long logId) {
        return toFoodLogResponse(foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado.")));
    }

    @Transactional
    public AiRegistrationResponse confirmAiRegistration(AppUser user, ConfirmAiRegistrationRequest request,
            String sourcePrefix) {
        List<AiEstimateItem> items = normalizeAiEstimateItems(request.items());
        validateAiEstimateItems(items);
        AiCaptureTarget targetType = items.size() > 1 ? AiCaptureTarget.RECIPE : AiCaptureTarget.FOOD;
        LocalDate logDate = request.logDate() == null ? LocalDate.now() : request.logDate();
        if (targetType == AiCaptureTarget.FOOD) {
            if (items.size() != 1) {
                throw new BadRequestException("El registro de alimento debe contener exactamente un elemento.");
            }
            AiEstimateItem item = items.getFirst();
            Food food = resolveAiRegistrationFood(user, sourcePrefix + ":item:0", item, request.acknowledgedArchivedFoodIds());
            FoodLogResponse log = null;
            if (request.addToDiary()) {
                if (request.mealType() == null) throw new BadRequestException("Elegí a qué comida agregar el alimento.");
                log = addMealLog(user, new AddMealLogRequest(MealItemType.FOOD, food.getId(), request.mealType(),
                        item.estimatedGrams(), FoodUnit.GRAM, logDate, request.acknowledgedArchivedFoodIds()));
            }
            return new AiRegistrationResponse(AiCaptureTarget.FOOD, toFoodResponse(food), null, log);
        }
        if (request.mealType() == null) throw new BadRequestException("Elegí a qué comida agregar la receta.");

        Recipe recipe = new Recipe();
        recipe.setName(request.name().trim());
        recipe.setDescription(clean(request.description()));
        recipe.setCreatedBy(user);
        for (int index = 0; index < items.size(); index++) {
            AiEstimateItem item = items.get(index);
            Food food = resolveAiRegistrationFood(user, sourcePrefix + ":item:" + index, item, request.acknowledgedArchivedFoodIds());
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            ingredient.setFood(food);
            ingredient.setQuantity(item.estimatedGrams());
            ingredient.setUnit(FoodUnit.GRAM);
            recipe.getIngredients().add(ingredient);
        }
        recipe.setRawTotalWeightGrams(recipeRawTotalWeight(recipe.getIngredients()));
        recipe.setTotalWeightGrams(recipe.getRawTotalWeightGrams());
        applyRecipeTotals(recipe);
        recipe = recipes.save(recipe);
        FoodLogResponse log = addMealLog(user, new AddMealLogRequest(MealItemType.RECIPE, recipe.getId(), request.mealType(),
                BigDecimal.ONE, FoodUnit.PORTION, logDate, request.acknowledgedArchivedFoodIds()));
        return new AiRegistrationResponse(AiCaptureTarget.RECIPE, null, toRecipeResponse(recipe), log);
    }

    @Transactional(readOnly = true)
    public AiRegistrationResponse findAiRegistrationResult(AppUser user, AiCaptureTarget targetType, Long foodId,
            Long recipeId, Long logId) {
        FoodResponse food = targetType == AiCaptureTarget.FOOD && foodId != null ? findFood(foodId) : null;
        RecipeResponse recipe = targetType == AiCaptureTarget.RECIPE && recipeId != null ? findRecipe(recipeId) : null;
        FoodLogResponse log = logId == null ? null : findOwnedFoodLog(user, logId);
        if (food == null && recipe == null) throw new NotFoundException("El elemento creado en esta captura ya no existe.");
        return new AiRegistrationResponse(targetType, food, recipe, log);
    }

    private Food resolveAiRegistrationFood(AppUser user, String sourceId, AiEstimateItem item, Set<Long> acknowledged) {
        if (item.catalogFoodId() != null) { Food food = getFood(item.catalogFoodId()); checkArchived(List.of(food), acknowledged); return food; }
        // Only reuse a catalog food when the reviewed estimate explicitly retained its match.
        // Otherwise its user-edited macros must be the values materialized into the new food.
        return materializeAiEstimateItem(user, sourceId, item, acknowledged);
    }

    private BigDecimal perHundred(BigDecimal value, BigDecimal grams) {
        if (grams == null || grams.signum() <= 0) {
            throw new BadRequestException("La cantidad estimada debe ser mayor que cero.");
        }
        BigDecimal ratio = BigDecimal.valueOf(100).divide(grams, 4, RoundingMode.HALF_UP);
        return scale(value.multiply(ratio));
    }

    private void validateRecipeIngredient(Recipe parent, Recipe ingredientRecipe, FoodUnit unit) {
        if (unit != FoodUnit.GRAM) {
            throw new BadRequestException("Las recetas usadas como ingredientes se expresan en gramos.");
        }
        if (parent.getId() != null && referencesRecipe(ingredientRecipe, parent.getId(), new HashSet<>())) {
            throw new BadRequestException("No se puede agregar una receta que genera una referencia circular.");
        }
    }

    private boolean referencesRecipe(Recipe source, Long targetId, Set<Long> visited) {
        if (source == null || source.getId() == null || !visited.add(source.getId())) return false;
        for (RecipeIngredient ingredient : source.getIngredients()) {
            Recipe nested = ingredient.getIngredientRecipe();
            if (nested == null) continue;
            if (targetId.equals(nested.getId()) || referencesRecipe(nested, targetId, visited)) return true;
        }
        return false;
    }

    private FoodLog ownedRecipeLog(AppUser user, Long logId) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        if (log.getItemType() != MealItemType.RECIPE || log.getRecipe() == null) {
            throw new BadRequestException("Este registro no corresponde a una receta.");
        }
        return log;
    }

    @Transactional(readOnly = true)
    public List<NutrientValueResponse> nutrientsForLog(AppUser user, Long logId) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        return log.getNutrientSnapshot().stream().map(this::toNutrientResponse).toList();
    }

    private NutritionPreviewResponse preview(Food food, BigDecimal quantity, FoodUnit unit) {
        BigDecimal normalizedQuantity = normalizeQuantity(food, quantity, unit);
        BigDecimal ratio = normalizedQuantity.divide(food.getBaseQuantity(), 4, RoundingMode.HALF_UP);
        BigDecimal protein = NutritionMath.scaled(food.getProteinGrams(), ratio);
        BigDecimal carbs = NutritionMath.scaled(food.getCarbsGrams(), ratio);
        BigDecimal fat = NutritionMath.scaled(food.getFatGrams(), ratio);
        return new NutritionPreviewResponse(
                scaledCalories(food.getCalories(), ratio, protein, carbs, fat),
                protein,
                carbs,
                fat,
                scaleNutrients(food, ratio));
    }

    private NutritionPreviewResponse previewRecipeServing(Recipe recipe, BigDecimal quantity, FoodUnit unit,
            BigDecimal cookedTotalWeightGrams) {
        BigDecimal ratio = recipeServingRatio(quantity, unit, cookedTotalWeightGrams);
        BigDecimal protein = NutritionMath.scaled(recipe.getProteinGrams(), ratio);
        BigDecimal carbs = NutritionMath.scaled(recipe.getCarbsGrams(), ratio);
        BigDecimal fat = NutritionMath.scaled(recipe.getFatGrams(), ratio);
        return new NutritionPreviewResponse(
                scaledCalories(recipe.getCalories(), ratio, protein, carbs, fat),
                protein,
                carbs,
                fat,
                scaleRecipeNutrients(recipe, ratio));
    }

    private NutritionPreviewResponse previewIngredient(RecipeIngredient ingredient) {
        if (ingredient.getFood() != null) return preview(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit());
        if (ingredient.getIngredientRecipe() != null) return previewRecipeIngredient(ingredient.getIngredientRecipe(), ingredient.getQuantity(), ingredient.getUnit());
        throw new BadRequestException("La receta contiene un ingrediente sin referencia.");
    }

    private NutritionPreviewResponse previewRecipeIngredient(Recipe recipe, BigDecimal quantity, FoodUnit unit) {
        if (unit != FoodUnit.GRAM) throw new BadRequestException("Las recetas usadas como ingredientes se expresan en gramos.");
        BigDecimal weight = recipe.getCookedTotalWeightGrams() != null
                ? recipe.getCookedTotalWeightGrams() : recipe.getRawTotalWeightGrams();
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("La receta usada como ingrediente no tiene un peso válido.");
        }
        BigDecimal ratio = quantity.divide(weight, 4, RoundingMode.HALF_UP);
        BigDecimal protein = NutritionMath.scaled(recipe.getProteinGrams(), ratio);
        BigDecimal carbs = NutritionMath.scaled(recipe.getCarbsGrams(), ratio);
        BigDecimal fat = NutritionMath.scaled(recipe.getFatGrams(), ratio);
        return new NutritionPreviewResponse(scaledCalories(recipe.getCalories(), ratio, protein, carbs, fat), protein, carbs, fat,
                scaleRecipeNutrients(recipe, ratio));
    }

    private NutritionPreviewResponse previewRecipeServing(FoodLog log, BigDecimal quantity, FoodUnit unit) {
        if (log.getRecipeIngredients().isEmpty()) {
            return previewRecipeServing(log.getRecipe(), quantity, unit, log.getRecipeCookedTotalWeightGrams());
        }
        if (unit != FoodUnit.PORTION) {
            throw new BadRequestException("No se pueden registrar en gramos recetas con ingredientes ajustados.");
        }
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        Map<String, NutrientValueResponse> nutrients = new LinkedHashMap<>();
        for (FoodLogRecipeIngredient ingredient : log.getRecipeIngredients()) {
            NutritionPreviewResponse ingredientPreview = preview(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit());
            protein = NutritionMath.add(protein, ingredientPreview.proteinGrams());
            carbs = NutritionMath.add(carbs, ingredientPreview.carbsGrams());
            fat = NutritionMath.add(fat, ingredientPreview.fatGrams());
            mergeNutrients(nutrients, ingredientPreview.nutrients());
        }
        protein = NutritionMath.scaled(protein, quantity);
        carbs = NutritionMath.scaled(carbs, quantity);
        fat = NutritionMath.scaled(fat, quantity);
        return new NutritionPreviewResponse(macroCalories(protein, carbs, fat), protein, carbs, fat,
                scaleNutrientResponses(nutrients.values().stream().toList(), quantity));
    }

    private BigDecimal recipeServingRatio(BigDecimal quantity, FoodUnit unit, BigDecimal cookedTotalWeightGrams) {
        if (unit == FoodUnit.PORTION) return quantity;
        if (unit == FoodUnit.GRAM) return quantity.divide(cookedTotalWeightGrams, 4, RoundingMode.HALF_UP);
        throw new BadRequestException("Las recetas solo se pueden registrar por porción o por gramos cocidos.");
    }

    private void validateRecipeLogUnit(FoodUnit unit, BigDecimal cookedTotalWeightGrams, boolean adjustedRecipe) {
        if (unit != FoodUnit.PORTION && unit != FoodUnit.GRAM) {
            throw new BadRequestException("Las recetas solo se pueden registrar por porción o por gramos cocidos.");
        }
        if (unit == FoodUnit.GRAM && adjustedRecipe) {
            throw new BadRequestException("No se pueden registrar en gramos recetas con ingredientes ajustados.");
        }
        if (unit == FoodUnit.GRAM && cookedTotalWeightGrams == null) {
            throw new BadRequestException("La receta necesita un peso total cocido para registrarse en gramos.");
        }
    }

    private void rejectAdjustedRecipeGrams(FoodLog log) {
        if (log.getUnit() == FoodUnit.GRAM) {
            throw new BadRequestException("No se pueden ajustar ingredientes en una receta registrada por gramos cocidos.");
        }
        if (log.getUnit() != FoodUnit.PORTION) {
            throw new BadRequestException("Las recetas con ingredientes ajustados solo se registran por porción.");
        }
    }

    private void captureRecipeWeights(FoodLog log, Recipe recipe) {
        log.setRecipeRawTotalWeightGrams(recipe.getRawTotalWeightGrams());
        log.setRecipeCookedTotalWeightGrams(recipe.getCookedTotalWeightGrams());
    }

    private void applyLogNutrition(FoodLog log, NutritionPreviewResponse preview) {
        log.setCalories(preview.calories());
        log.setProteinGrams(preview.proteinGrams());
        log.setCarbsGrams(preview.carbsGrams());
        log.setFatGrams(preview.fatGrams());
        Map<String, FoodLogNutrient> existing = log.getNutrientSnapshot().stream()
                .filter(item -> item.getDefinition() != null)
                .collect(Collectors.toMap(item -> item.getDefinition().getCode(), item -> item,
                        (left, right) -> left, LinkedHashMap::new));
        Set<String> retainedCodes = new LinkedHashSet<>();
        for (NutrientValueResponse value : preview.nutrients()) {
            nutrientDefinitions.findById(value.code()).ifPresent(definition -> {
                retainedCodes.add(definition.getCode());
                FoodLogNutrient snapshot = existing.get(definition.getCode());
                if (snapshot == null) {
                    snapshot = new FoodLogNutrient();
                    snapshot.setFoodLog(log);
                    log.getNutrientSnapshot().add(snapshot);
                }
                snapshot.setDefinition(definition);
                snapshot.setValue(value.value());
                snapshot.setKnownValue(value.knownValue()); snapshot.setComplete(value.complete());
                snapshot.setSource(parseSource(value.source()));
                snapshot.setStatus(parseStatus(value.status()));
            });
        }
        log.getNutrientSnapshot().removeIf(item -> item.getDefinition() == null
                || !retainedCodes.contains(item.getDefinition().getCode()));
    }

    private void applyRecipeTotals(Recipe recipe) {
        if (recipe.getIngredients().isEmpty()) {
            throw new BadRequestException("La receta debe tener al menos un ingrediente.");
        }
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        Integer calories = 0;
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            NutritionPreviewResponse preview = previewIngredient(ingredient);
            calories = calories == null || preview.calories() == null ? null : Integer.valueOf(Math.addExact(calories, preview.calories()));
            protein = NutritionMath.add(protein, preview.proteinGrams());
            carbs = NutritionMath.add(carbs, preview.carbsGrams());
            fat = NutritionMath.add(fat, preview.fatGrams());
        }
        recipe.setProteinGrams(scale(protein));
        recipe.setCarbsGrams(scale(carbs));
        recipe.setFatGrams(scale(fat));
        recipe.setCalories(calories);
        recipe.setUpdatedAt(OffsetDateTime.now());
    }

    private BigDecimal recipeRawTotalWeight(List<? extends Object> ingredients) {
        BigDecimal total = ingredients.stream()
                .map(this::ingredientWeightInGrams)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("La receta necesita al menos un ingrediente con peso en gramos.");
        }
        return scaleWeight(total);
    }

    private BigDecimal ingredientWeightInGrams(Object ingredient) {
        Food food;
        Recipe recipe;
        BigDecimal quantity;
        FoodUnit unit;
        if (ingredient instanceof RecipeIngredient item) {
            food = item.getFood();
            recipe = item.getIngredientRecipe();
            quantity = item.getQuantity();
            unit = item.getUnit();
        } else if (ingredient instanceof FoodLogRecipeIngredient item) {
            food = item.getFood();
            recipe = null;
            quantity = item.getQuantity();
            unit = item.getUnit();
        } else {
            throw new IllegalArgumentException("Ingrediente de receta no soportado.");
        }
        if (recipe != null) {
            if (unit != FoodUnit.GRAM) throw new BadRequestException("Las recetas usadas como ingredientes se expresan en gramos.");
            return quantity;
        }
        if (unit == FoodUnit.GRAM) return quantity;
        if ((unit == FoodUnit.UNIT || unit == FoodUnit.PORTION) && food.getServingWeightGrams() != null) {
            return quantity.multiply(food.getServingWeightGrams());
        }
        // Volumen no implica masa: sin densidad no se puede sumar mililitros como gramos.
        return BigDecimal.ZERO;
    }

    private BigDecimal normalizeQuantity(Food food, BigDecimal quantity, FoodUnit unit) {
        if (unit == food.getBaseUnit()) return quantity;
        if (unit == FoodUnit.UNIT || unit == FoodUnit.PORTION) {
            if (food.getServingWeightGrams() == null) {
                throw new BadRequestException("Este alimento no tiene un peso definido para esa unidad.");
            }
            return quantity.multiply(food.getServingWeightGrams());
        }
        throw new BadRequestException("No se puede convertir " + unit + " a la unidad base de este alimento.");
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
                food.getCreatedAt(), food.getModerationStatus(), scaleNutrients(food, BigDecimal.ONE),
                food.getCookedYieldFactor(), food.getCookedYieldSource(), food.getCookedYieldAssumption(), food.getDeletedAt() != null);
    }

    private FoodSummaryResponse toFoodSummaryResponse(Food food) {
        return new FoodSummaryResponse(food.getId(), food.getName(), food.getBrand(), food.getBarcode(), food.getCategory(), food.getBaseUnit(),
                food.getBaseQuantity(), food.getCalories(),
                food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams(), food.getPreparation(),
                food.getPreparationGroup(), food.getServingName(), food.getServingWeightGrams(), food.getImageUrl(), scaleNutrients(food, BigDecimal.ONE),
                food.getCookedYieldFactor(), food.getCookedYieldSource(), food.getCookedYieldAssumption());
    }

    private FoodLogResponse toFoodLogResponse(FoodLog log) {
        return new FoodLogResponse(log.getId(), log.getLogDate(), log.getMealType(), log.getItemType(),
                toFoodResponse(log.getFood()), log.getRecipe() == null ? null : toRecipeResponse(log),
                log.getQuantity(), log.getUnit(), log.getRecipeRawTotalWeightGrams(), log.getRecipeCookedTotalWeightGrams(),
                log.getCalories(), log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams(),
                !log.getRecipeIngredients().isEmpty(), log.getItemType() == MealItemType.AI_ESTIMATE ? log.getAiEstimateName() : null,
                log.getAiEstimateConfidence(), log.getAiEstimateDetails(), log.getNutrientSnapshot().stream().map(this::toNutrientResponse).toList());
    }

    private RecipeResponse toRecipeResponse(FoodLog log) {
        if (log.getRecipeIngredients().isEmpty()) return toRecipeResponse(log.getRecipe());
        Integer calories = 0;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal rawTotalWeight = BigDecimal.ZERO;
        Map<String, NutrientValueResponse> nutrients = new LinkedHashMap<>();
        List<RecipeIngredientResponse> ingredients = log.getRecipeIngredients().stream().map(item -> {
            return new RecipeIngredientResponse(toFoodResponse(item.getFood()), null, item.getQuantity(), item.getUnit());
        }).toList();
        for (FoodLogRecipeIngredient ingredient : log.getRecipeIngredients()) {
            NutritionPreviewResponse preview = preview(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit());
            calories = calories == null || preview.calories() == null ? null : Integer.valueOf(Math.addExact(calories, preview.calories()));
            protein = NutritionMath.add(protein, preview.proteinGrams());
            carbs = NutritionMath.add(carbs, preview.carbsGrams());
            fat = NutritionMath.add(fat, preview.fatGrams());
            mergeNutrients(nutrients, preview.nutrients());
            rawTotalWeight = rawTotalWeight.add(ingredientWeightInGrams(ingredient));
        }
        rawTotalWeight = scale(rawTotalWeight);
        return new RecipeResponse(log.getRecipe().getId(), log.getRecipe().getName(), log.getRecipe().getDescription(), rawTotalWeight,
                rawTotalWeight, null, calories, scale(protein), scale(carbs), scale(fat), ingredients,
                nutrients.values().stream().toList());
    }

    private RecipeResponse toRecipeResponse(Recipe recipe) {
        return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getRawTotalWeightGrams(),
                recipe.getRawTotalWeightGrams(), recipe.getCookedTotalWeightGrams(),
                recipe.getCalories(), recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams(),
                recipe.getIngredients().stream()
                        .map(this::toRecipeIngredientResponse)
                        .toList(), scaleRecipeNutrients(recipe, BigDecimal.ONE));
    }

    private RecipeIngredientResponse toRecipeIngredientResponse(RecipeIngredient item) {
        return new RecipeIngredientResponse(toFoodResponse(item.getFood()), toRecipeReference(item.getIngredientRecipe()),
                item.getQuantity(), item.getUnit());
    }

    private RecipeReferenceResponse toRecipeReference(Recipe recipe) {
        if (recipe == null) return null;
        return new RecipeReferenceResponse(recipe.getId(), recipe.getName(), recipe.getDescription(),
                recipe.getRawTotalWeightGrams(), recipe.getCookedTotalWeightGrams(),
                recipe.getCalories(),
                recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams());
    }

    private PageResponse<RecipeResponse> recipeSummaryPage(Page<Recipe> result) {
        if (result.isEmpty()) return page(result.map(recipe -> toRecipeSummary(recipe, 0)));
        Map<Long, Integer> counts = recipes.countIngredientsForPage(result.getContent().stream().map(Recipe::getId).toList())
                .stream().collect(Collectors.toMap(RecipeRepository.RecipeIngredientCountProjection::getRecipeId, item -> Math.toIntExact(item.getIngredientCount())));
        return page(result.map(recipe -> toRecipeSummary(recipe, counts.getOrDefault(recipe.getId(), 0))));
    }

    private RecipeResponse toRecipeSummary(Recipe recipe, int ingredientCount) {
        return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getRawTotalWeightGrams(),
                recipe.getRawTotalWeightGrams(), recipe.getCookedTotalWeightGrams(), recipe.getCalories(),
                recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams(), List.of(), List.of(), ingredientCount);
    }

    private static BigDecimal sum(List<FoodLog> logs, java.util.function.Function<FoodLog, BigDecimal> mapper) {
        return logs.stream().map(mapper).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return NutritionMath.scale(value);
    }

    private static BigDecimal scaleWeight(BigDecimal value) {
        if (value == null) return null;
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return scaled.stripTrailingZeros().scale() < 1 ? scaled.setScale(1, RoundingMode.HALF_UP) : scaled;
    }

    private List<NutrientValueResponse> scaleRecipeNutrients(Recipe recipe, BigDecimal ratio) {
        Map<String, NutrientValueResponse> values = new LinkedHashMap<>();
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            mergeNutrients(values, previewIngredient(ingredient).nutrients());
        }
        return scaleNutrientResponses(values.values().stream().toList(), ratio);
    }

    private void mergeNutrients(Map<String, NutrientValueResponse> target, List<NutrientValueResponse> source) {
        for (NutrientValueResponse value : source) {
            NutrientValueResponse current = target.get(value.code());
            if (current == null) target.put(value.code(), value);
            else target.put(value.code(), new NutrientValueResponse(value.code(), value.name(), value.group(), value.unit(),
                    current.value() == null || value.value() == null ? null : scale(current.value().add(value.value())),
                    current.source(), current.complete() && value.complete() ? current.status() : "PARTIAL",
                    current.knownValue() == null && value.knownValue() == null ? null : scale(zero(current.knownValue()).add(zero(value.knownValue()))),
                    current.complete() && value.complete()));
        }
    }

    private List<NutrientValueResponse> scaleNutrientResponses(List<NutrientValueResponse> values, BigDecimal ratio) {
        return values.stream().map(value -> new NutrientValueResponse(value.code(), value.name(), value.group(), value.unit(),
                value.value() == null ? null : scale(value.value().multiply(ratio)), value.source(), value.status(), NutritionMath.scaled(value.knownValue(), ratio), value.complete())).toList();
    }

    private List<NutrientValueResponse> scaleNutrients(Food food, BigDecimal ratio) {
        Map<String, FoodNutrient> existing = food.getNutrients().stream().filter(item -> item.getDefinition() != null)
                .collect(Collectors.toMap(item -> item.getDefinition().getCode(), item -> item, (left, right) -> left, LinkedHashMap::new));
        List<NutrientValueResponse> values = nutrientDefinitions.findAll().stream()
                .filter(NutrientDefinition::isVisible)
                .sorted(Comparator.comparing(NutrientDefinition::getDisplayOrder))
                .map(definition -> {
                    FoodNutrient item = existing.get(definition.getCode());
                    BigDecimal legacyValue = switch (definition.getCode()) {
                        case "CALORIES" -> food.getCalories() == null ? null : BigDecimal.valueOf(food.getCalories());
                        case "PROTEIN" -> food.getProteinGrams();
                        case "CARBOHYDRATE" -> food.getCarbsGrams();
                        case "FAT" -> food.getFatGrams();
                        default -> null;
                    };
                    BigDecimal value = item == null ? legacyValue : item.getValue();
                    String source = item == null ? (legacyValue == null ? NutrientSource.LEGACY.name() : NutrientSource.LEGACY.name())
                            : item.getSource() == null ? NutrientSource.LEGACY.name() : item.getSource().name();
                    String status = item == null ? (legacyValue == null ? NutrientStatus.MISSING.name() : NutrientStatus.PARTIAL.name())
                            : item.getStatus() == null ? NutrientStatus.MISSING.name() : item.getStatus().name();
                    return new NutrientValueResponse(definition.getCode(), definition.getName(), definition.getNutrientGroup(), definition.getUnit(),
                            value == null ? null : scale(value.multiply(ratio)), source, status);
                }).toList();
        return values.isEmpty() ? List.of(
                new NutrientValueResponse("PROTEIN", "Proteínas", "MACRO", "g", NutritionMath.scaled(food.getProteinGrams(), ratio), "LEGACY", "PARTIAL"),
                new NutrientValueResponse("CARBOHYDRATE", "Carbohidratos", "MACRO", "g", NutritionMath.scaled(food.getCarbsGrams(), ratio), "LEGACY", "PARTIAL"),
                new NutrientValueResponse("FAT", "Grasas", "MACRO", "g", NutritionMath.scaled(food.getFatGrams(), ratio), "LEGACY", "PARTIAL")) : values;
    }

    private NutrientValueResponse toNutrientResponse(FoodLogNutrient item) {
        NutrientDefinition definition = item.getDefinition();
        return new NutrientValueResponse(definition.getCode(), definition.getName(), definition.getNutrientGroup(), definition.getUnit(),
                item.getValue(), item.getSource() == null ? "LEGACY" : item.getSource().name(),
                item.getStatus() == null ? "MISSING" : item.getStatus().name(), item.getKnownValue() == null ? item.getValue() : item.getKnownValue(), item.getValue() != null && item.isComplete());
    }

    private static NutrientSource parseSource(String value) {
        try { return NutrientSource.valueOf(value == null ? "LEGACY" : value); }
        catch (IllegalArgumentException ex) { return NutrientSource.LEGACY; }
    }

    private static NutrientStatus parseStatus(String value) {
        try { return NutrientStatus.valueOf(value == null ? "MISSING" : value); }
        catch (IllegalArgumentException ex) { return NutrientStatus.MISSING; }
    }

    private static Integer macroCalories(BigDecimal protein, BigDecimal carbs, BigDecimal fat) { return NutritionMath.calories(protein, carbs, fat); }

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
        return logs.stream().map(mapper).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private Set<String> copyTags(Set<String> tags) {
        return tags == null ? Set.of() : new LinkedHashSet<>(tags);
    }
}
