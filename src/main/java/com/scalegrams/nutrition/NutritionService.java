package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.catalog.ModerationStatus;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.externalfood.ExternalFoodCandidate;
import com.scalegrams.externalfood.ExternalFoodLookupService;
import com.scalegrams.externalfood.OpenFoodFactsProvider;
import com.scalegrams.externalfood.UsdaFoodDataProvider;
import com.scalegrams.nutrition.NutritionDtos.AddMealLogRequest;
import com.scalegrams.nutrition.NutritionDtos.AddFoodLogRequest;
import com.scalegrams.nutrition.NutritionDtos.AddWaterRequest;
import com.scalegrams.nutrition.NutritionDtos.BatchAddMealLogsRequest;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateItem;
import com.scalegrams.nutrition.NutritionDtos.ConfirmAiEstimateRequest;
import com.scalegrams.nutrition.NutritionDtos.ConfirmAiEstimateItem;
import com.scalegrams.nutrition.NutritionDtos.AiEstimateFoodProposal;
import com.scalegrams.nutrition.NutritionDtos.CreateFoodRequest;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeRequest;
import com.scalegrams.nutrition.NutritionDtos.AddRecipeMealLogRequest;
import com.scalegrams.nutrition.NutritionDtos.DashboardResponse;
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
import com.scalegrams.nutrition.NutritionDtos.UpdateRecipeLogIngredientsRequest;
import com.scalegrams.nutrition.NutritionDtos.UpdateRecipeFoodLogRequest;
import com.scalegrams.nutrition.NutritionDtos.SaveAiEstimateItemRequest;
import com.scalegrams.nutrition.NutritionDtos.PageResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeIngredientResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeIngredientRequest;
import com.scalegrams.nutrition.NutritionDtos.RecipeResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NutritionService {
    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);
    public record EnrichmentReport(int examined, int updated, int skipped, int noMatch,
            int matchedOff, int matchedUsda, int aiEstimated) {}
    private final FoodRepository foods;
    private final RecipeRepository recipes;
    private final FoodLogRepository foodLogs;
    private final WaterLogRepository waterLogs;
    private final ProfileService profileService;
    private final ExternalFoodLookupService externalFoodLookup;
    private final ObjectMapper objectMapper;
    private final NutrientDefinitionRepository nutrientDefinitions;
    private final UsdaFoodDataProvider usda;
    private final OpenFoodFactsProvider openFoodFacts;
    private final GeminiNutritionClient gemini;

    public NutritionService(FoodRepository foods, RecipeRepository recipes, FoodLogRepository foodLogs,
            WaterLogRepository waterLogs, ProfileService profileService,
            ExternalFoodLookupService externalFoodLookup, ObjectMapper objectMapper,
            NutrientDefinitionRepository nutrientDefinitions, UsdaFoodDataProvider usda,
            OpenFoodFactsProvider openFoodFacts, GeminiNutritionClient gemini) {
        this.foods = foods;
        this.recipes = recipes;
        this.foodLogs = foodLogs;
        this.waterLogs = waterLogs;
        this.profileService = profileService;
        this.externalFoodLookup = externalFoodLookup;
        this.objectMapper = objectMapper;
        this.nutrientDefinitions = nutrientDefinitions;
        this.usda = usda;
        this.openFoodFacts = openFoodFacts;
        this.gemini = gemini;
    }

    @Transactional
    public PageResponse<FoodSummaryResponse> searchFoods(String query, FoodCategory category, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        Page<Food> result;
        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            query = query.trim();
            if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        }
        if (hasQuery && category != null) {
            result = foods.search(query, category, ModerationStatus.APPROVED, pageable);
        } else if (hasQuery) {
            result = foods.search(query, ModerationStatus.APPROVED, pageable);
        } else if (category != null) {
            result = foods.findByModerationStatusAndCategory(ModerationStatus.APPROVED, category, pageable);
        } else {
            result = foods.findByModerationStatus(ModerationStatus.APPROVED, pageable);
        }
        if (hasQuery && page == 0 && result.getTotalElements() < Math.min(size, 10)) {
            externalFoodLookup.searchByText(query, 12).forEach(candidate -> {
                if (candidate.barcode() != null && !foods.existsByBarcode(candidate.barcode())) importExternalFood(candidate);
            });
            result = category == null ? foods.search(query, ModerationStatus.APPROVED, pageable)
                    : foods.search(query, category, ModerationStatus.APPROVED, pageable);
        }
        return page(result.map(this::toFoodSummaryResponse));
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
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        food.setPreparation(request.preparation() == null ? com.scalegrams.catalog.FoodPreparation.UNSPECIFIED : request.preparation());
        food.setPreparationSource("Ingresado por el usuario");
        food.setServingName(clean(request.servingName()));
        food.setServingWeightGrams(request.servingWeightGrams());
        food.setCreatedBy(creator);
        food.setCreatedAt(OffsetDateTime.now());
        food.setModerationStatus(com.scalegrams.catalog.ModerationStatus.APPROVED);
        if (request.tags() != null) {
            food.setTags(request.tags().stream()
                    .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return toFoodResponse(foods.save(food));
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findFoodsCreatedBy(com.scalegrams.user.AppUser creator) {
        return foods.findByCreatedByIdOrderByCreatedAtDesc(creator.getId()).stream().map(this::toFoodResponse).toList();
    }

    @Transactional
    public FoodResponse updateOwnedFood(Long id, CreateFoodRequest request, com.scalegrams.user.AppUser creator) {
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
        food.setProteinGrams(scale(request.proteinGrams()));
        food.setCarbsGrams(scale(request.carbsGrams()));
        food.setFatGrams(scale(request.fatGrams()));
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        if (request.preparation() != null) {
            food.setPreparation(request.preparation());
            food.setPreparationSource("Ingresado por el usuario");
        }
        if (request.servingName() != null || request.servingWeightGrams() != null) {
            food.setServingName(clean(request.servingName()));
            food.setServingWeightGrams(request.servingWeightGrams());
        }
        if (request.tags() != null) {
            food.setTags(request.tags().stream()
                    .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return toFoodResponse(foods.save(food));
    }

    @Transactional(readOnly = true)
    public FoodResponse findFood(Long id) {
        return toFoodResponse(getFood(id));
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
        if (food.getCreatedBy() != null && !food.getCreatedBy().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Solo podés enriquecer alimentos propios.");
        }
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
        if (food.getCreatedBy() != null && !food.getCreatedBy().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Solo podés editar nutrientes de alimentos propios.");
        }
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

    @Transactional
    public EnrichmentReport enrichFoodCatalog(AppUser user, int limit) {
        if (user.getRole() != Role.ADMIN) throw new BadRequestException("Solo un administrador puede enriquecer el catálogo.");
        int examined = 0;
        int updated = 0;
        int skipped = 0;
        int noMatch = 0;
        int matchedOff = 0;
        int matchedUsda = 0;
        int aiEstimated = 0;
        Map<String, String> translations = new LinkedHashMap<>();
        for (Food food : foods.findAll(PageRequest.of(0, Math.min(Math.max(limit, 1), 100), Sort.by(Sort.Direction.ASC, "id")))) {
            examined++;
            if (hasDetailedNutrients(food)) { skipped++; continue; }
            String barcode = food.getBarcode();
            boolean hasRealBarcode = barcode != null && !barcode.matches("^7790000000\\d+");
            if (hasRealBarcode) {
                Optional<ExternalFoodCandidate> candidate = externalFoodLookup.lookupByBarcode(barcode);
                if (candidate.isPresent()) { enrichExistingFood(food, candidate.get()); updated++; matchedOff++; }
                else noMatch++;
                continue;
            }
            Optional<ExternalFoodCandidate> candidate;
            try {
                candidate = smartCandidate(food, translations);
            } catch (AiQuotaExceededException ex) {
                if (!awaitGeminiQuota(ex)) break;
                candidate = smartCandidate(food, translations);
            }
            if (candidate.isPresent()) {
                enrichExistingFood(food, candidate.get());
                updated++;
                if ("OPEN_FOOD_FACTS".equals(candidate.get().source())) matchedOff++;
                else if ("USDA".equals(candidate.get().source())) matchedUsda++;
                else noMatch++;
            } else {
                try {
                    Optional<GeminiNutritionClient.AiNutritionItem> estimate = gemini.estimateByName(food.getName(),
                            food.getCategory() == null ? null : food.getCategory().name());
                    if (estimate.isPresent()) {
                        applyAiEstimateToFood(food, estimate.get());
                        updated++;
                        aiEstimated++;
                    } else noMatch++;
                } catch (AiQuotaExceededException ex) {
                    if (!awaitGeminiQuota(ex)) break;
                    try {
                        Optional<GeminiNutritionClient.AiNutritionItem> estimate = gemini.estimateByName(food.getName(),
                                food.getCategory() == null ? null : food.getCategory().name());
                        if (estimate.isPresent()) {
                            applyAiEstimateToFood(food, estimate.get());
                            updated++;
                            aiEstimated++;
                        } else noMatch++;
                    } catch (AiQuotaExceededException second) { break; }
                }
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        return new EnrichmentReport(examined, updated, skipped, noMatch, matchedOff, matchedUsda, aiEstimated);
    }

    private boolean awaitGeminiQuota(AiQuotaExceededException ex) {
        OffsetDateTime retryAt = ex.getRetryAt();
        Duration wait = Duration.between(OffsetDateTime.now(), retryAt).plusSeconds(5);
        if (wait.isNegative() || wait.toMinutes() > 10) {
            log.warn("Gemini quota exhausted until {}; enrichment aborted.", retryAt);
            return false;
        }
        log.warn("Gemini quota exhausted; waiting {}s before continuing.", wait.getSeconds());
        try { Thread.sleep(wait.toMillis()); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        return true;
    }

    private Optional<ExternalFoodCandidate> smartCandidate(Food food, Map<String, String> translations) {
        Optional<ExternalFoodCandidate> off = openFoodFacts.searchByText(food.getName(), 8).stream()
                .filter(candidate -> plausible(candidate, food))
                .findFirst();
        if (off.isPresent()) return off;
        String translated = translations.computeIfAbsent(food.getName(), name -> {
            if (name == null || name.isBlank()) return null;
            return gemini.translateFoodNames(List.of(name)).get(name);
        });
        if (translated == null || translated.isBlank()) return Optional.empty();
        Optional<ExternalFoodCandidate> usdaCandidate = usda.searchOne(translated);
        if (usdaCandidate.isPresent() && plausible(usdaCandidate.get(), food)) return usdaCandidate;
        return Optional.empty();
    }

    private boolean plausible(ExternalFoodCandidate candidate, Food food) {
        Integer calories = candidate.calories();
        BigDecimal protein = candidate.proteinGrams();
        BigDecimal carbs = candidate.carbsGrams();
        BigDecimal fat = candidate.fatGrams();
        if (calories == null || protein == null || carbs == null || fat == null) return false;
        if (calories < 15 || calories > 950) return false;
        if (protein.signum() < 0 || carbs.signum() < 0 || fat.signum() < 0) return false;
        if (protein.compareTo(BigDecimal.valueOf(120)) > 0 || fat.compareTo(BigDecimal.valueOf(100)) > 0) return false;
        int macroCalories = macroCalories(protein, carbs, fat);
        if (Math.abs(macroCalories - calories) > Math.max(45, calories / 3)) return false;
        FoodCategory category = food.getCategory();
        if (category != null) {
            int maxCalories = switch (category) {
                case FRUIT, VEGETABLE -> 450;
                case LEGUME, CEREAL, PROTEIN, DAIRY -> 700;
                case BEVERAGE -> 350;
                default -> 950;
            };
            if (calories > maxCalories) return false;
        }
        return true;
    }

    private Food applyAiEstimateToFood(Food food, GeminiNutritionClient.AiNutritionItem estimate) {
        BigDecimal ratio = BigDecimal.valueOf(100).divide(estimate.estimatedGrams(), 4, RoundingMode.HALF_UP);
        food.setProteinGrams(scale(estimate.proteinGrams().multiply(ratio)));
        food.setCarbsGrams(scale(estimate.carbsGrams().multiply(ratio)));
        food.setFatGrams(scale(estimate.fatGrams().multiply(ratio)));
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        addAiNutrients(food, estimate.nutrients(), ratio);
        food.setPreparationSource("Estimado por IA");
        if (food.getSource() == null || "LOCAL".equals(food.getSource())) {
            food.setSource("AI_ESTIMATE");
            food.setSourceId("ai-estimate:" + food.getId());
        }
        food.setLastSyncedAt(OffsetDateTime.now());
        return foods.save(food);
    }

    private boolean hasDetailedNutrients(Food food) {
        return food.getNutrients().stream().anyMatch(item -> item.getDefinition() != null
                && item.getValue() != null
                && !List.of("CALORIES", "PROTEIN", "CARBOHYDRATE", "FAT").contains(item.getDefinition().getCode()));
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
        food.setProteinGrams(scale(candidate.proteinGrams()));
        food.setCarbsGrams(scale(candidate.carbsGrams()));
        food.setFatGrams(scale(candidate.fatGrams()));
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        food.setPreparation(candidate.preparation());
        food.setPreparationSource(clean(candidate.preparationSource()));
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
        if (query != null && !query.isBlank()) {
            query = query.trim();
            if (query.length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        }
        Page<Recipe> result = query != null && !query.isBlank()
                ? recipes.findByNameContainingIgnoreCase(query, pageable)
                : recipes.findAll(pageable);
        return page(result.map(this::toRecipeSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchOwnedRecipes(AppUser user, String query, int page, int size) {
        Pageable pageable = recipePageable(page, size);
        Page<Recipe> result = hasRecipeQuery(query)
                ? recipes.findByCreatedByIdAndNameContainingIgnoreCase(user.getId(), query.trim(), pageable)
                : recipes.findByCreatedById(user.getId(), pageable);
        return page(result.map(this::toRecipeSummary));
    }

    @Transactional(readOnly = true)
    public List<RecipeOwnerResponse> recipeAuthors(AppUser user) {
        Set<Long> seen = new java.util.HashSet<>();
        return recipes.findAuthorsExcluding(user.getId()).stream()
                .filter(author -> seen.add(author.getId()))
                .map(author -> new RecipeOwnerResponse(author.getId(), author.getFullName(), recipes.countByCreatedById(author.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchRecipesByOwner(Long ownerId, String query, int page, int size) {
        Pageable pageable = recipePageable(page, size);
        Page<Recipe> result = hasRecipeQuery(query)
                ? recipes.findByCreatedByIdAndNameContainingIgnoreCase(ownerId, query.trim(), pageable)
                : recipes.findByCreatedById(ownerId, pageable);
        return page(result.map(this::toRecipeSummary));
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
        recipe.setCreatedBy(user);
        replaceRecipeIngredients(recipe, request);
        recipe.setTotalWeightGrams(recipeTotalWeight(recipe));
        applyRecipeTotals(recipe);
        return toRecipeResponse(recipes.save(recipe));
    }

    @Transactional
    public RecipeResponse copyRecipe(AppUser user, Long id) {
        Recipe source = getRecipe(id);
        Recipe copy = new Recipe();
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setCreatedBy(user);
        copy.setTotalWeightGrams(source.getTotalWeightGrams());
        copy.setCalories(source.getCalories());
        copy.setProteinGrams(source.getProteinGrams());
        copy.setCarbsGrams(source.getCarbsGrams());
        copy.setFatGrams(source.getFatGrams());
        for (RecipeIngredient sourceIngredient : source.getIngredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(copy);
            ingredient.setFood(sourceIngredient.getFood());
            ingredient.setQuantity(sourceIngredient.getQuantity());
            ingredient.setUnit(sourceIngredient.getUnit());
            copy.getIngredients().add(ingredient);
        }
        return toRecipeResponse(recipes.save(copy));
    }

    @Transactional
    public RecipeResponse updateOwnedRecipe(AppUser user, Long id, CreateRecipeRequest request) {
        Recipe recipe = getRecipe(id);
        if (recipe.getCreatedBy() == null || !recipe.getCreatedBy().getId().equals(user.getId())) {
            throw new BadRequestException("Solo podes editar recetas creadas por vos.");
        }
        recipe.setName(request.name().trim());
        recipe.setDescription(clean(request.description()));
        replaceRecipeIngredients(recipe, request);
        recipe.setTotalWeightGrams(recipeTotalWeight(recipe));
        applyRecipeTotals(recipe);
        return toRecipeResponse(recipes.save(recipe));
    }

    @Transactional
    public void deleteOwnedRecipe(AppUser user, Long id) {
        Recipe recipe = getRecipe(id);
        if (recipe.getCreatedBy() == null || !recipe.getCreatedBy().getId().equals(user.getId())) {
            throw new BadRequestException("Solo podes borrar recetas creadas por vos.");
        }
        if (foodLogs.existsByRecipeId(id)) {
            throw new BadRequestException("No se puede borrar una receta que ya tiene registros en comidas.");
        }
        recipes.delete(recipe);
    }

    private void replaceRecipeIngredients(Recipe recipe, CreateRecipeRequest request) {
        recipe.getIngredients().clear();
        for (var item : request.ingredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            ingredient.setFood(getFood(item.foodId()));
            ingredient.setQuantity(item.quantity());
            ingredient.setUnit(item.unit());
            recipe.getIngredients().add(ingredient);
        }
    }

    private Pageable recipePageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    private boolean hasRecipeQuery(String query) {
        if (query == null || query.isBlank()) return false;
        if (query.trim().length() > 120) throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        return true;
    }

    @Transactional(readOnly = true)
    public NutritionPreviewResponse previewRecipe(CreateRecipeRequest request) {
        Recipe recipe = new Recipe();
        for (var item : request.ingredients()) {
            RecipeIngredient ingredient = new RecipeIngredient();
            ingredient.setRecipe(recipe);
            ingredient.setFood(getFood(item.foodId()));
            ingredient.setQuantity(item.quantity());
            ingredient.setUnit(item.unit());
            recipe.getIngredients().add(ingredient);
        }
        recipe.setTotalWeightGrams(recipeTotalWeight(recipe));
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
            preview = preview(food, request.quantity(), request.unit());
            log.setFood(food);
        } else if (request.itemType() == MealItemType.RECIPE) {
            Recipe recipe = getRecipe(request.itemId());
            preview = previewRecipeServing(recipe, request.quantity());
            log.setRecipe(recipe);
        } else {
            throw new BadRequestException("Este tipo de registro solo se puede crear desde una estimación confirmada.");
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
    public List<FoodLogResponse> addMealLogs(AppUser user, BatchAddMealLogsRequest request) {
        return request.logs().stream().map(item -> addMealLog(user, item)).toList();
    }

    @Transactional(readOnly = true)
    public List<RecentMealResponse> recentMeals(AppUser user, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 50);
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusMonths(12);
        List<FoodLog> logs = new ArrayList<>(foodLogs.findByUserAndLogDateBetween(user, start, end));
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
            BigDecimal protein = bracket.stream().map(FoodLog::getProteinGrams).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal carbs = bracket.stream().map(FoodLog::getCarbsGrams).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal fat = bracket.stream().map(FoodLog::getFatGrams).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new RecentMealResponse(bracket.get(0).getLogDate(), bracket.get(0).getMealType(),
                    label(bracket.get(0).getMealType()), macroCalories(protein, carbs, fat), scale(protein), scale(carbs),
                    scale(fat), bracket.stream().map(this::toFoodLogResponse).toList()));
            if (result.size() >= limit) break;
        }
        return result;
    }

    private String recentMealItemSignature(FoodLog log) {
        String item = log.getItemType() + ":" + (log.getFood() == null
                ? log.getRecipe() == null ? "" : log.getRecipe().getId()
                : log.getFood().getId());
        String ingredients = log.getRecipeIngredients().stream()
                .map(ingredient -> ingredient.getFood().getId() + "=" + ingredient.getQuantity() + "=" + ingredient.getUnit())
                .collect(Collectors.joining(","));
        return item + ":" + log.getQuantity() + ":" + log.getUnit() + ":" + ingredients;
    }

    @Transactional
    public FoodLogResponse addRecipeMealLog(AppUser user, AddRecipeMealLogRequest request) {
        Recipe recipe = getRecipe(request.recipeId());
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setItemType(MealItemType.RECIPE);
        log.setRecipe(recipe);
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(FoodUnit.PORTION);
        log.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        replaceRecipeLogIngredients(log, recipe, request.ingredients());
        applyLogNutrition(log, previewRecipeServing(log, request.quantity()));
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public List<FoodLogResponse> confirmAiEstimate(AppUser user, ConfirmAiEstimateRequest request) {
        LocalDate logDate = request.logDate() == null ? LocalDate.now() : request.logDate();
        return request.items().stream().map(item -> confirmAiEstimateItem(user, request.mealType(), logDate, item)).toList();
    }

    private FoodLogResponse confirmAiEstimateItem(AppUser user, MealType mealType, LocalDate logDate,
            ConfirmAiEstimateItem item) {
        Food food = item.foodId() == null ? createAiEstimateFood(user, item.proposal()) : getFood(item.foodId());
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
        if (item.proposal() != null) {
            food.setSourceId("food-log:" + savedLog.getId());
            foods.save(food);
        }
        return toFoodLogResponse(savedLog);
    }

    private Food createAiEstimateFood(AppUser user, AiEstimateFoodProposal proposal) {
        Food food = new Food();
        food.setName(proposal.name().trim());
        food.setCategory(proposal.category());
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setProteinGrams(scale(proposal.proteinGrams()));
        food.setCarbsGrams(scale(proposal.carbsGrams()));
        food.setFatGrams(scale(proposal.fatGrams()));
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        addAiNutrients(food, proposal.nutrients(), BigDecimal.ONE);
        food.setPreparation(proposal.preparation() == null ? com.scalegrams.catalog.FoodPreparation.UNSPECIFIED
                : proposal.preparation());
        food.setPreparationSource("Estimado por IA");
        food.setSource("AI_ESTIMATE");
        food.setCreatedBy(user);
        food.setCreatedAt(OffsetDateTime.now());
        food.setModerationStatus(com.scalegrams.catalog.ModerationStatus.APPROVED);
        return foods.save(food);
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
        FoodLog log = ownedAiEstimateLog(user, logId);
        AiEstimateDetails details = readAiEstimateDetails(log);
        if (itemIndex < 0 || itemIndex >= details.items().size()) {
            throw new BadRequestException("El item de la estimación no existe.");
        }
        AiEstimateItem item = details.items().get(itemIndex);
        validateAiEstimateItems(List.of(item));
        BigDecimal ratio = BigDecimal.valueOf(100).divide(item.estimatedGrams(), 4, RoundingMode.HALF_UP);
        Food food = new Food();
        food.setName(item.name().trim());
        food.setCategory(request.category());
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setProteinGrams(scale(item.proteinGrams().multiply(ratio)));
        food.setCarbsGrams(scale(item.carbsGrams().multiply(ratio)));
        food.setFatGrams(scale(item.fatGrams().multiply(ratio)));
        food.setCalories(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        addAiNutrients(food, item.nutrients(), ratio);
        food.setPreparation(request.preparation() == null ? com.scalegrams.catalog.FoodPreparation.UNSPECIFIED : request.preparation());
        food.setPreparationSource("Estimado por IA");
        food.setSource("AI_ESTIMATE");
        food.setSourceId("food-log:" + log.getId() + ":item:" + itemIndex);
        food.setCreatedBy(user);
        food.setCreatedAt(OffsetDateTime.now());
        food.setModerationStatus(com.scalegrams.catalog.ModerationStatus.APPROVED);
        if (request.tags() != null) {
            food.setTags(request.tags().stream()
                    .map(this::clean).filter(tag -> tag != null).limit(10).collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return toFoodResponse(foods.save(food));
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
                item.proteinGrams(), item.carbsGrams(), item.fatGrams(), item.nutrients())).toList();
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
        try {
            return objectMapper.readValue(log.getAiEstimateDetails(), AiEstimateDetails.class);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("No se pudo leer la estimación guardada.");
        }
    }

    private record AiEstimateDetails(String description, String context, List<String> assumptions, List<AiEstimateItem> items) {
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
    public void deleteMealLogs(AppUser user, MealType mealType, LocalDate date) {
        foodLogs.deleteAll(foodLogs.findByUserAndMealTypeAndLogDate(user, mealType, date == null ? LocalDate.now() : date));
    }

    @Transactional
    public FoodLogResponse updateFoodLog(AppUser user, Long logId, UpdateFoodLogRequest request) {
        FoodLog log = foodLogs.findByIdAndUser(logId, user)
                .orElseThrow(() -> new NotFoundException("Registro de comida no encontrado."));
        if (request.itemId() != null && log.getItemType() == MealItemType.FOOD && !request.itemId().equals(log.getFood().getId())) {
            Food newFood = getFood(request.itemId());
            log.setFood(newFood);
        }
        NutritionPreviewResponse preview = log.getItemType() == MealItemType.RECIPE
                ? previewRecipeServing(log, request.quantity())
                : log.getItemType() == MealItemType.FOOD
                ? preview(log.getFood(), request.quantity(), request.unit())
                : new NutritionPreviewResponse(log.getCalories(), log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams());
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
    public FoodLogResponse updateRecipeLogIngredients(AppUser user, Long logId, UpdateRecipeLogIngredientsRequest request) {
        FoodLog log = ownedRecipeLog(user, logId);
        replaceRecipeLogIngredients(log, log.getRecipe(), request.ingredients());
        NutritionPreviewResponse preview = previewRecipeServing(log, log.getQuantity());
        applyLogNutrition(log, preview);
        return toFoodLogResponse(foodLogs.save(log));
    }

    @Transactional
    public FoodLogResponse updateRecipeFoodLog(AppUser user, Long logId, UpdateRecipeFoodLogRequest request) {
        FoodLog log = ownedRecipeLog(user, logId);
        replaceRecipeLogIngredients(log, log.getRecipe(), request.recipeIngredients());
        log.setMealType(request.mealType());
        log.setQuantity(request.quantity());
        log.setUnit(FoodUnit.PORTION);
        if (request.logDate() != null) log.setLogDate(request.logDate());
        applyLogNutrition(log, previewRecipeServing(log, request.quantity()));
        return toFoodLogResponse(foodLogs.save(log));
    }

    private void replaceRecipeLogIngredients(FoodLog log, Recipe recipe, List<RecipeIngredientRequest> requests) {
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
    public void resetRecipeLogIngredients(AppUser user, Long logId) {
        FoodLog log = ownedRecipeLog(user, logId);
        log.getRecipeIngredients().clear();
        applyLogNutrition(log, previewRecipeServing(log, log.getQuantity()));
        foodLogs.save(log);
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
        BigDecimal protein = sum(logs, FoodLog::getProteinGrams);
        BigDecimal carbs = sum(logs, FoodLog::getCarbsGrams);
        BigDecimal fat = sum(logs, FoodLog::getFatGrams);
        Map<String, NutrientValueResponse> dailyNutrients = new LinkedHashMap<>();
        logs.forEach(log -> mergeNutrients(dailyNutrients, log.getNutrientSnapshot().stream().map(this::toNutrientResponse).toList()));
        int calories = macroCalories(protein, carbs, fat);
        BigDecimal water = waterLogs.sumLitersByUserAndLogDate(user, targetDate);
        Map<MealType, List<FoodLog>> byMeal = logs.stream().collect(Collectors.groupingBy(FoodLog::getMealType));
        List<MealSummary> meals = Arrays.stream(MealType.values()).map(meal -> {
            List<FoodLogResponse> items = byMeal.getOrDefault(meal, List.of()).stream().map(this::toFoodLogResponse).toList();
            BigDecimal mealProtein = sumResponses(items, FoodLogResponse::proteinGrams);
            BigDecimal mealCarbs = sumResponses(items, FoodLogResponse::carbsGrams);
            BigDecimal mealFat = sumResponses(items, FoodLogResponse::fatGrams);
            return new MealSummary(meal, label(meal), macroCalories(mealProtein, mealCarbs, mealFat), mealProtein,
                    mealCarbs, mealFat, items);
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
                        plan.getFatGoalGrams(), plan.getStartDate(), plan.getEndDate(), !plan.getStartDate().isAfter(targetDate)
                                && (plan.getEndDate() == null || !plan.getEndDate().isBefore(targetDate))), dailyNutrients.values().stream().toList());
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
        List<DaySummary> days = ym.atDay(1).datesUntil(ym.atEndOfMonth().plusDays(1)).map(date -> {
            FoodLogRepository.DayNutritionProjection summary = byDate.get(date);
            BigDecimal protein = summary == null ? BigDecimal.ZERO : scale(summary.getProteinGrams());
            BigDecimal carbs = summary == null ? BigDecimal.ZERO : scale(summary.getCarbsGrams());
            BigDecimal fat = summary == null ? BigDecimal.ZERO : scale(summary.getFatGrams());
            int calories = macroCalories(protein, carbs, fat);
            NutritionPlan plan = plans.stream()
                    .filter(candidate -> !candidate.getStartDate().isAfter(date)
                            && (candidate.getEndDate() == null || !candidate.getEndDate().isBefore(date)))
                    .findFirst()
                    .orElseGet(() -> profileService.resolvePlan(user, date));
            return new DaySummary(date, calories, plan.getDailyCalories(), protein,
                    carbs, fat,
                    calories > 0 && calories <= plan.getDailyCalories(), plan.getId(), plan.getName());
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
        BigDecimal protein = scale(food.getProteinGrams().multiply(ratio));
        BigDecimal carbs = scale(food.getCarbsGrams().multiply(ratio));
        BigDecimal fat = scale(food.getFatGrams().multiply(ratio));
        return new NutritionPreviewResponse(
                macroCalories(protein, carbs, fat),
                protein,
                carbs,
                fat,
                scaleNutrients(food, ratio));
    }

    private NutritionPreviewResponse previewRecipeServing(Recipe recipe, BigDecimal portions) {
        BigDecimal ratio = portions;
        BigDecimal protein = scale(recipe.getProteinGrams().multiply(ratio));
        BigDecimal carbs = scale(recipe.getCarbsGrams().multiply(ratio));
        BigDecimal fat = scale(recipe.getFatGrams().multiply(ratio));
        return new NutritionPreviewResponse(
                macroCalories(protein, carbs, fat),
                protein,
                carbs,
                fat,
                scaleRecipeNutrients(recipe, ratio));
    }

    private NutritionPreviewResponse previewRecipeServing(FoodLog log, BigDecimal portions) {
        if (log.getRecipeIngredients().isEmpty()) return previewRecipeServing(log.getRecipe(), portions);
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        Map<String, NutrientValueResponse> nutrients = new LinkedHashMap<>();
        for (FoodLogRecipeIngredient ingredient : log.getRecipeIngredients()) {
            NutritionPreviewResponse ingredientPreview = preview(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit());
            protein = protein.add(ingredientPreview.proteinGrams());
            carbs = carbs.add(ingredientPreview.carbsGrams());
            fat = fat.add(ingredientPreview.fatGrams());
            mergeNutrients(nutrients, ingredientPreview.nutrients());
        }
        protein = scale(protein.multiply(portions));
        carbs = scale(carbs.multiply(portions));
        fat = scale(fat.multiply(portions));
        return new NutritionPreviewResponse(macroCalories(protein, carbs, fat), protein, carbs, fat,
                scaleNutrientResponses(nutrients.values().stream().toList(), portions));
    }

    private void applyLogNutrition(FoodLog log, NutritionPreviewResponse preview) {
        log.setCalories(preview.calories());
        log.setProteinGrams(preview.proteinGrams());
        log.setCarbsGrams(preview.carbsGrams());
        log.setFatGrams(preview.fatGrams());
        log.getNutrientSnapshot().clear();
        for (NutrientValueResponse value : preview.nutrients()) {
            nutrientDefinitions.findById(value.code()).ifPresent(definition -> {
                FoodLogNutrient snapshot = new FoodLogNutrient();
                snapshot.setFoodLog(log);
                snapshot.setDefinition(definition);
                snapshot.setValue(value.value());
                snapshot.setSource(parseSource(value.source()));
                snapshot.setStatus(parseStatus(value.status()));
                log.getNutrientSnapshot().add(snapshot);
            });
        }
    }

    private void applyRecipeTotals(Recipe recipe) {
        if (recipe.getIngredients().isEmpty()) {
            throw new BadRequestException("La receta debe tener al menos un ingrediente.");
        }
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            NutritionPreviewResponse preview = preview(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit());
            protein = protein.add(preview.proteinGrams());
            carbs = carbs.add(preview.carbsGrams());
            fat = fat.add(preview.fatGrams());
        }
        recipe.setProteinGrams(scale(protein));
        recipe.setCarbsGrams(scale(carbs));
        recipe.setFatGrams(scale(fat));
        recipe.setCalories(macroCalories(recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams()));
        recipe.setUpdatedAt(OffsetDateTime.now());
    }

    private BigDecimal recipeTotalWeight(Recipe recipe) {
        BigDecimal total = recipe.getIngredients().stream()
                .map(ingredient -> normalizeQuantity(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("El peso total de la receta debe ser mayor a cero.");
        }
        return scale(total);
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
                food.getBaseUnit(), food.getBaseQuantity(), macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()), food.getProteinGrams(), food.getCarbsGrams(),
                food.getFatGrams(), food.getPreparation(), food.getPreparationSource(), food.getPreparationGroup(), food.getServingName(), food.getServingWeightGrams(), food.getImageUrl(), food.getSource(), food.getSourceId(), food.getLastSyncedAt(),
                copyTags(food.getTags()), food.getCreatedBy() == null ? null : food.getCreatedBy().getId(),
                food.getCreatedAt(), food.getModerationStatus(), scaleNutrients(food, BigDecimal.ONE));
    }

    private FoodSummaryResponse toFoodSummaryResponse(Food food) {
        return new FoodSummaryResponse(food.getId(), food.getName(), food.getBrand(), food.getBarcode(), food.getCategory(), food.getBaseUnit(),
                food.getBaseQuantity(), macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()),
                food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams(), food.getPreparation(),
                food.getPreparationGroup(), food.getServingName(), food.getServingWeightGrams(), food.getImageUrl(), scaleNutrients(food, BigDecimal.ONE));
    }

    private FoodLogResponse toFoodLogResponse(FoodLog log) {
        return new FoodLogResponse(log.getId(), log.getLogDate(), log.getMealType(), log.getItemType(),
                toFoodResponse(log.getFood()), log.getRecipe() == null ? null : toRecipeResponse(log),
                log.getQuantity(), log.getUnit(), macroCalories(log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams()), log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams(),
                !log.getRecipeIngredients().isEmpty(), log.getItemType() == MealItemType.AI_ESTIMATE ? log.getAiEstimateName() : null,
                log.getAiEstimateConfidence(), log.getAiEstimateDetails(), log.getNutrientSnapshot().stream().map(this::toNutrientResponse).toList());
    }

    private RecipeResponse toRecipeResponse(FoodLog log) {
        if (log.getRecipeIngredients().isEmpty()) return toRecipeResponse(log.getRecipe());
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        Map<String, NutrientValueResponse> nutrients = new LinkedHashMap<>();
        List<RecipeIngredientResponse> ingredients = log.getRecipeIngredients().stream().map(item -> {
            return new RecipeIngredientResponse(toFoodResponse(item.getFood()), item.getQuantity(), item.getUnit());
        }).toList();
        for (FoodLogRecipeIngredient ingredient : log.getRecipeIngredients()) {
            NutritionPreviewResponse preview = preview(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit());
            protein = protein.add(preview.proteinGrams());
            carbs = carbs.add(preview.carbsGrams());
            fat = fat.add(preview.fatGrams());
            mergeNutrients(nutrients, preview.nutrients());
            totalWeight = totalWeight.add(normalizeQuantity(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit()));
        }
        return new RecipeResponse(log.getRecipe().getId(), log.getRecipe().getName(), log.getRecipe().getDescription(), scale(totalWeight),
                macroCalories(protein, carbs, fat), scale(protein), scale(carbs), scale(fat), ingredients, nutrients.values().stream().toList());
    }

    private RecipeResponse toRecipeResponse(Recipe recipe) {
        return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getTotalWeightGrams(),
                macroCalories(recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams()), recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams(),
                recipe.getIngredients().stream()
                        .map(item -> new RecipeIngredientResponse(toFoodResponse(item.getFood()), item.getQuantity(), item.getUnit()))
                        .toList(), scaleRecipeNutrients(recipe, BigDecimal.ONE));
    }

    private RecipeResponse toRecipeSummary(Recipe recipe) {
        return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getTotalWeightGrams(),
                macroCalories(recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams()), recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams(), List.of(), scaleRecipeNutrients(recipe, BigDecimal.ONE));
    }

    private static BigDecimal sum(List<FoodLog> logs, java.util.function.Function<FoodLog, BigDecimal> mapper) {
        return logs.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : value.setScale(1, RoundingMode.HALF_UP);
    }

    private List<NutrientValueResponse> scaleRecipeNutrients(Recipe recipe, BigDecimal ratio) {
        Map<String, NutrientValueResponse> values = new LinkedHashMap<>();
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            mergeNutrients(values, preview(ingredient.getFood(), ingredient.getQuantity(), ingredient.getUnit()).nutrients());
        }
        return scaleNutrientResponses(values.values().stream().toList(), ratio);
    }

    private void mergeNutrients(Map<String, NutrientValueResponse> target, List<NutrientValueResponse> source) {
        for (NutrientValueResponse value : source) {
            NutrientValueResponse current = target.get(value.code());
            if (current == null) target.put(value.code(), value);
            else target.put(value.code(), new NutrientValueResponse(value.code(), value.name(), value.group(), value.unit(),
                    current.value() == null || value.value() == null ? null : scale(current.value().add(value.value())),
                    current.source(), current.status()));
        }
    }

    private List<NutrientValueResponse> scaleNutrientResponses(List<NutrientValueResponse> values, BigDecimal ratio) {
        return values.stream().map(value -> new NutrientValueResponse(value.code(), value.name(), value.group(), value.unit(),
                value.value() == null ? null : scale(value.value().multiply(ratio)), value.source(), value.status())).toList();
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
                        case "CALORIES" -> BigDecimal.valueOf(macroCalories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
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
                new NutrientValueResponse("PROTEIN", "Proteínas", "MACRO", "g", scale(food.getProteinGrams().multiply(ratio)), "LEGACY", "PARTIAL"),
                new NutrientValueResponse("CARBOHYDRATE", "Carbohidratos", "MACRO", "g", scale(food.getCarbsGrams().multiply(ratio)), "LEGACY", "PARTIAL"),
                new NutrientValueResponse("FAT", "Grasas", "MACRO", "g", scale(food.getFatGrams().multiply(ratio)), "LEGACY", "PARTIAL")) : values;
    }

    private NutrientValueResponse toNutrientResponse(FoodLogNutrient item) {
        NutrientDefinition definition = item.getDefinition();
        return new NutrientValueResponse(definition.getCode(), definition.getName(), definition.getNutrientGroup(), definition.getUnit(),
                item.getValue(), item.getSource() == null ? "LEGACY" : item.getSource().name(),
                item.getStatus() == null ? "MISSING" : item.getStatus().name());
    }

    private static NutrientSource parseSource(String value) {
        try { return NutrientSource.valueOf(value == null ? "LEGACY" : value); }
        catch (IllegalArgumentException ex) { return NutrientSource.LEGACY; }
    }

    private static NutrientStatus parseStatus(String value) {
        try { return NutrientStatus.valueOf(value == null ? "MISSING" : value); }
        catch (IllegalArgumentException ex) { return NutrientStatus.MISSING; }
    }

    private static int macroCalories(BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
        return scale(protein).multiply(BigDecimal.valueOf(4))
                .add(scale(carbs).multiply(BigDecimal.valueOf(4)))
                .add(scale(fat).multiply(BigDecimal.valueOf(9)))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
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
