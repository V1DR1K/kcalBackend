package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodPreparation;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.ForbiddenException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.externalfood.ExternalFoodCandidate;
import com.scalegrams.externalfood.ExternalFoodLookupService;
import com.scalegrams.nutrition.NutritionDtos.CreateFoodRequest;
import com.scalegrams.nutrition.NutritionDtos.FoodResponse;
import com.scalegrams.nutrition.NutritionDtos.NutrientInput;
import com.scalegrams.nutrition.NutritionDtos.NutrientUpdateRequest;
import com.scalegrams.nutrition.NutritionDtos.NutrientValueResponse;
import com.scalegrams.user.AppUser;
import com.scalegrams.user.Role;

@Service
public class FoodCatalogService {
    public record EnrichmentReport(int examined, int updated, int skipped, int noMatch) {}

    private final FoodRepository foods;
    private final FoodSemanticSearchService semanticFoods;
    private final FoodNutrientMapper nutrientMapper;
    private final FoodYieldPolicy yieldPolicy;
    private final NutrientDefinitionRepository nutrientDefinitions;
    private final ExternalFoodLookupService externalFoodLookup;

    public FoodCatalogService(FoodRepository foods, FoodSemanticSearchService semanticFoods,
            FoodNutrientMapper nutrientMapper, FoodYieldPolicy yieldPolicy,
            NutrientDefinitionRepository nutrientDefinitions, ExternalFoodLookupService externalFoodLookup) {
        this.foods = foods;
        this.semanticFoods = semanticFoods;
        this.nutrientMapper = nutrientMapper;
        this.yieldPolicy = yieldPolicy;
        this.nutrientDefinitions = nutrientDefinitions;
        this.externalFoodLookup = externalFoodLookup;
    }

    @Transactional
    public FoodResponse create(CreateFoodRequest request, AppUser creator) {
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
        food.setProteinGrams(NutritionMath.scale(request.proteinGrams()));
        food.setCarbsGrams(NutritionMath.scale(request.carbsGrams()));
        food.setFatGrams(NutritionMath.scale(request.fatGrams()));
        food.setCalories(food.getProteinGrams() == null || food.getCarbsGrams() == null || food.getFatGrams() == null
                ? request.calories() : NutritionMath.calories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        food.setPreparation(request.preparation() == null ? FoodPreparation.UNSPECIFIED : request.preparation());
        food.setPreparationSource("Ingresado por el usuario");
        yieldPolicy.applyRequestedCookedYield(food, request);
        food.setServingName(clean(request.servingName()));
        food.setServingWeightGrams(request.servingWeightGrams());
        food.setCreatedBy(creator);
        food.setCreatedAt(OffsetDateTime.now());
        food.setModerationStatus(com.scalegrams.catalog.ModerationStatus.APPROVED);
        if (request.tags() != null) {
            food.setTags(request.tags().stream().map(this::clean).filter(tag -> tag != null).limit(10)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        Food saved = foods.saveAndFlush(food);
        semanticFoods.indexIfAvailable(saved);
        return nutrientMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findCreatedBy(AppUser creator) {
        return foods.findByCreatedByIdAndDeletedAtIsNullOrderByCreatedAtDesc(creator.getId()).stream()
                .map(nutrientMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findDeletedCreatedBy(AppUser creator) {
        return foods.findByCreatedByIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(creator.getId()).stream()
                .map(nutrientMapper::toResponse).toList();
    }

    @Transactional
    public FoodResponse update(Long id, CreateFoodRequest request, AppUser creator) {
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
        food.setProteinGrams(NutritionMath.scale(request.proteinGrams()));
        food.setCarbsGrams(NutritionMath.scale(request.carbsGrams()));
        food.setFatGrams(NutritionMath.scale(request.fatGrams()));
        food.setCalories(NutritionMath.calories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        if (request.preparation() != null) {
            food.setPreparation(request.preparation());
            food.setPreparationSource("Ingresado por el usuario");
        }
        yieldPolicy.applyRequestedCookedYield(food, request);
        if (request.servingName() != null || request.servingWeightGrams() != null) {
            food.setServingName(clean(request.servingName()));
            food.setServingWeightGrams(request.servingWeightGrams());
        }
        if (request.tags() != null) {
            food.setTags(request.tags().stream().map(this::clean).filter(tag -> tag != null).limit(10)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        Food saved = foods.saveAndFlush(food);
        semanticFoods.indexIfAvailable(saved);
        return nutrientMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id, AppUser creator) {
        Food food = getFood(id);
        if (food.getCreatedBy() == null || !food.getCreatedBy().getId().equals(creator.getId())) {
            throw new BadRequestException("Solo podés borrar alimentos creados por vos.");
        }
        if (food.getDeletedAt() == null) food.setDeletedAt(OffsetDateTime.now());
        foods.save(food);
    }

    @Transactional
    public FoodResponse restore(Long id, AppUser creator) {
        Food food = getFood(id);
        if (food.getCreatedBy() == null || !food.getCreatedBy().getId().equals(creator.getId())) {
            throw new BadRequestException("Solo podés recuperar alimentos creados por vos.");
        }
        food.setDeletedAt(null);
        return nutrientMapper.toResponse(foods.save(food));
    }

    @Transactional(readOnly = true)
    public FoodResponse find(Long id) {
        return nutrientMapper.toResponse(getActiveFood(id));
    }

    @Transactional(readOnly = true)
    public List<NutrientValueResponse> nutrientDefinitions() {
        return nutrientDefinitions.findAll().stream().filter(NutrientDefinition::isVisible)
                .sorted(java.util.Comparator.comparing(NutrientDefinition::getDisplayOrder))
                .map(item -> new NutrientValueResponse(item.getCode(), item.getName(), item.getNutrientGroup(),
                        item.getUnit(), null, null, "MISSING"))
                .toList();
    }

    @Transactional
    public FoodResponse enrich(Long id, AppUser user) {
        Food food = getFood(id);
        requireFoodWriteAccess(food, user);
        Optional<ExternalFoodCandidate> candidate = food.getBarcode() == null
                ? externalFoodLookup.searchByText(food.getName(), 1).stream().findFirst()
                : externalFoodLookup.lookupByBarcode(food.getBarcode());
        if (candidate.isEmpty()) {
            throw new BadRequestException("No encontramos una fuente nutricional para este alimento. Configurá USDA_FOOD_DATA_API_KEY para alimentos genéricos o usá Open Food Facts para productos con código de barras.");
        }
        return nutrientMapper.toResponse(enrichExistingFood(food, candidate.get()));
    }

    @Transactional
    public FoodResponse updateNutrients(Long id, NutrientUpdateRequest request, AppUser user) {
        Food food = getFood(id);
        requireFoodWriteAccess(food, user);
        for (NutrientInput input : request.nutrients()) {
            NutrientDefinition definition = nutrientDefinitions.findById(input.code().trim().toUpperCase())
                    .orElseThrow(() -> new BadRequestException("Nutriente desconocido: " + input.code()));
            FoodNutrient nutrient = food.getNutrients().stream()
                    .filter(item -> item.getDefinition().getCode().equals(definition.getCode())).findFirst()
                    .orElseGet(() -> {
                        FoodNutrient next = new FoodNutrient();
                        next.setFood(food);
                        next.setDefinition(definition);
                        food.getNutrients().add(next);
                        return next;
                    });
            nutrient.setValue(NutritionMath.scale(input.value()));
            nutrient.setSource(NutrientSource.MANUAL);
            nutrient.setStatus(NutrientStatus.VERIFIED);
            nutrient.setUpdatedAt(OffsetDateTime.now());
            if ("PROTEIN".equals(definition.getCode())) food.setProteinGrams(NutritionMath.scale(input.value()));
            if ("CARBOHYDRATE".equals(definition.getCode())) food.setCarbsGrams(NutritionMath.scale(input.value()));
            if ("FAT".equals(definition.getCode())) food.setFatGrams(NutritionMath.scale(input.value()));
            if ("CALORIES".equals(definition.getCode())) {
                food.setCalories(input.value().setScale(0, RoundingMode.HALF_UP).intValue());
            }
        }
        if (food.getCalories() == null || request.nutrients().stream()
                .noneMatch(item -> "CALORIES".equalsIgnoreCase(item.code()))) {
            food.setCalories(NutritionMath.calories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        }
        return nutrientMapper.toResponse(foods.save(food));
    }

    @Transactional
    public EnrichmentReport enrichCatalog(AppUser user, int limit) {
        if (user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Solo un administrador puede enriquecer el catálogo.");
        }
        int examined = 0;
        int updated = 0;
        int skipped = 0;
        int noMatch = 0;
        for (Food food : foods.findAll(PageRequest.of(0, Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Direction.ASC, "id")))) {
            examined++;
            if (hasDetailedNutrients(food)) {
                skipped++;
                continue;
            }
            Optional<ExternalFoodCandidate> candidate = food.getBarcode() == null
                    ? externalFoodLookup.searchByText(food.getName(), 1).stream().findFirst()
                    : externalFoodLookup.lookupByBarcode(food.getBarcode());
            if (candidate.isPresent()) {
                enrichExistingFood(food, candidate.get());
                updated++;
            } else {
                noMatch++;
            }
        }
        return new EnrichmentReport(examined, updated, skipped, noMatch);
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> preparationOptions(Long id) {
        Food food = getActiveFood(id);
        if (clean(food.getPreparationGroup()) == null) return List.of(nutrientMapper.toResponse(food));
        return foods.findByPreparationGroupAndDeletedAtIsNullOrderByPreparationAsc(food.getPreparationGroup()).stream()
                .map(nutrientMapper::toResponse).toList();
    }

    @Transactional
    public FoodResponse findByBarcode(String barcode) {
        String cleanBarcode = clean(barcode);
        if (cleanBarcode == null) throw new NotFoundException("No encontramos un alimento con ese codigo.");
        return foods.findByBarcodeAndDeletedAtIsNull(cleanBarcode)
                .map(existing -> shouldEnrich(existing) ? externalFoodLookup.lookupByBarcode(cleanBarcode)
                        .map(candidate -> enrichExistingFood(existing, candidate)).orElse(existing) : existing)
                .map(nutrientMapper::toResponse)
                .orElseGet(() -> externalFoodLookup.lookupByBarcode(cleanBarcode).map(this::importExternalFood)
                        .map(nutrientMapper::toResponse)
                        .orElseThrow(() -> new NotFoundException("No encontramos un alimento con ese codigo.")));
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

    private boolean hasDetailedNutrients(Food food) {
        return food.getNutrients().stream().anyMatch(item -> item.getDefinition() != null && item.getValue() != null
                && !List.of("CALORIES", "PROTEIN", "CARBOHYDRATE", "FAT").contains(item.getDefinition().getCode()));
    }

    private boolean shouldEnrich(Food food) {
        return (food.getSource() == null || "LOCAL".equals(food.getSource()))
                && (food.getPreparation() == null || food.getPreparation() == FoodPreparation.UNSPECIFIED);
    }

    private Food enrichExistingFood(Food food, ExternalFoodCandidate candidate) {
        if (food.getPreparation() == null || food.getPreparation() == FoodPreparation.UNSPECIFIED) {
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
        yieldPolicy.initializeIdentityCookedYield(food);
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
        food.setProteinGrams(NutritionMath.scale(candidate.proteinGrams()));
        food.setCarbsGrams(NutritionMath.scale(candidate.carbsGrams()));
        food.setFatGrams(NutritionMath.scale(candidate.fatGrams()));
        food.setCalories(candidate.calories() == null
                ? NutritionMath.calories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams())
                : candidate.calories());
        food.setPreparation(candidate.preparation());
        food.setPreparationSource(clean(candidate.preparationSource()));
        yieldPolicy.initializeIdentityCookedYield(food);
        food.setServingName(clean(candidate.servingName()));
        food.setServingWeightGrams(candidate.servingWeightGrams());
        food.setSource(candidate.source());
        food.setSourceId(candidate.sourceId());
        food.setLastSyncedAt(OffsetDateTime.now());
        applyExternalNutrients(food, candidate);
        food.setTags(candidate.tags() == null ? new LinkedHashSet<>()
                : candidate.tags().stream().map(this::clean).filter(tag -> tag != null).limit(10)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        return foods.save(food);
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
            nutrient.setValue(NutritionMath.scale(value));
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
                case "PROTEIN" -> food.setProteinGrams(NutritionMath.scale(item.getValue()));
                case "CARBOHYDRATE" -> food.setCarbsGrams(NutritionMath.scale(item.getValue()));
                case "FAT" -> food.setFatGrams(NutritionMath.scale(item.getValue()));
                case "CALORIES" -> food.setCalories(item.getValue().setScale(0, RoundingMode.HALF_UP).intValue());
                default -> { }
            }
        });
        if (food.getCalories() == null) {
            food.setCalories(NutritionMath.calories(food.getProteinGrams(), food.getCarbsGrams(), food.getFatGrams()));
        }
    }

    private Food getFood(Long id) {
        return foods.findById(id).orElseThrow(() -> new NotFoundException("Alimento no encontrado."));
    }

    private Food getActiveFood(Long id) {
        Food food = getFood(id);
        if (food.getDeletedAt() != null) throw new NotFoundException("Alimento no encontrado.");
        return food;
    }

    private static NutrientSource parseSource(String value) {
        try { return NutrientSource.valueOf(value == null ? "LEGACY" : value); }
        catch (IllegalArgumentException ex) { return NutrientSource.LEGACY; }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
