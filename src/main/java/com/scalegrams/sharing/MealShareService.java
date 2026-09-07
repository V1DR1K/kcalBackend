package com.scalegrams.sharing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodRepository;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.ConflictException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.nutrition.FoodLog;
import com.scalegrams.nutrition.FoodLogNutrient;
import com.scalegrams.nutrition.FoodLogRecipeIngredient;
import com.scalegrams.nutrition.FoodLogRepository;
import com.scalegrams.nutrition.MealItemType;
import com.scalegrams.nutrition.MealType;
import com.scalegrams.nutrition.NutrientDefinitionRepository;
import com.scalegrams.nutrition.NutrientSource;
import com.scalegrams.nutrition.NutrientStatus;
import com.scalegrams.recipe.RecipeRepository;
import com.scalegrams.sharing.MealShareDtos.AcceptMealShareRequest;
import com.scalegrams.sharing.MealShareDtos.CreateMealShareRequest;
import com.scalegrams.sharing.MealShareDtos.MealShareAcceptanceResponse;
import com.scalegrams.sharing.MealShareDtos.MealShareCreatedResponse;
import com.scalegrams.sharing.MealShareDtos.MealShareItemResponse;
import com.scalegrams.sharing.MealShareDtos.MealSharePreviewResponse;
import com.scalegrams.user.AppUser;

@Service
public class MealShareService {
    private static final int EXPIRY_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final MealShareRepository shares;
    private final MealShareAcceptanceRepository acceptances;
    private final FoodLogRepository foodLogs;
    private final FoodRepository foods;
    private final RecipeRepository recipes;
    private final NutrientDefinitionRepository nutrientDefinitions;
    private final ObjectMapper objectMapper;

    public MealShareService(MealShareRepository shares, MealShareAcceptanceRepository acceptances,
            FoodLogRepository foodLogs, FoodRepository foods, RecipeRepository recipes, NutrientDefinitionRepository nutrientDefinitions,
            ObjectMapper objectMapper) {
        this.shares = shares;
        this.acceptances = acceptances;
        this.foodLogs = foodLogs;
        this.foods = foods;
        this.recipes = recipes;
        this.nutrientDefinitions = nutrientDefinitions;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MealShareCreatedResponse create(AppUser owner, CreateMealShareRequest request) {
        List<FoodLog> logs = foodLogs.findByUserAndMealTypeAndLogDateWithRecipeIngredients(owner, request.mealType(), request.sourceDate());
        if (logs.isEmpty()) throw new BadRequestException("No hay alimentos para compartir en esa comida.");
        List<SharedMealItem> snapshot = logs.stream().map(this::snapshot).toList();
        String token = newToken();
        MealShare share = new MealShare();
        share.setOwner(owner);
        share.setTokenHash(hash(token));
        share.setSourceDate(request.sourceDate());
        share.setSourceMealType(request.mealType().name());
        share.setSnapshotJson(writeSnapshot(snapshot));
        share.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(EXPIRY_DAYS));
        share = shares.save(share);
        return new MealShareCreatedResponse(token, share.getExpiresAt(), preview(share, snapshot, false));
    }

    @Transactional(readOnly = true)
    public MealSharePreviewResponse preview(AppUser recipient, String token) {
        MealShare share = activeShare(token);
        List<SharedMealItem> snapshot = readSnapshot(share.getSnapshotJson());
        return preview(share, snapshot, acceptances.findByShareIdAndRecipient(share.getId(), recipient).isPresent());
    }

    @Transactional
    public MealShareAcceptanceResponse accept(AppUser recipient, String token, AcceptMealShareRequest request) {
        MealShare share = activeShareForUpdate(token);
        if (acceptances.findByShareIdAndRecipient(share.getId(), recipient).isPresent()) {
            throw new ConflictException("Ya agregaste esta comida a tu cuenta.");
        }
        List<SharedMealItem> snapshot = readSnapshot(share.getSnapshotJson());
        if (snapshot.isEmpty()) throw new BadRequestException("El enlace no contiene alimentos para agregar.");
        for (SharedMealItem item : snapshot) createFoodLog(recipient, request, item);
        MealShareAcceptance acceptance = new MealShareAcceptance();
        acceptance.setShare(share);
        acceptance.setRecipient(recipient);
        acceptance.setTargetDate(request.targetDate());
        acceptance.setTargetMealType(request.mealType().name());
        acceptances.save(acceptance);
        return new MealShareAcceptanceResponse(request.targetDate(), request.mealType(), snapshot.size());
    }

    private FoodLog createFoodLog(AppUser recipient, AcceptMealShareRequest request, SharedMealItem item) {
        FoodLog log = new FoodLog();
        log.setUser(recipient);
        log.setItemType(item.itemType());
        log.setFood(item.foodId() == null ? null : foods.findById(item.foodId()).orElse(null));
        log.setRecipe(item.recipeId() == null ? null : recipes.findById(item.recipeId())
                .orElseThrow(() -> new NotFoundException("La receta compartida ya no existe.")));
        log.setMealType(request.mealType());
        log.setLogDate(request.targetDate());
        log.setQuantity(item.quantity());
        log.setUnit(item.unit());
        log.setCalories(item.calories());
        log.setProteinGrams(item.proteinGrams());
        log.setCarbsGrams(item.carbsGrams());
        log.setFatGrams(item.fatGrams());
        log.setAiEstimateName(item.displayName());
        log.setAiEstimateConfidence(item.aiEstimateConfidence());
        log.setAiEstimateDetails(item.aiEstimateDetails());
        for (SharedNutrient nutrient : item.nutrients()) {
            nutrientDefinitions.findById(nutrient.code()).ifPresent(definition -> {
                FoodLogNutrient copy = new FoodLogNutrient();
                copy.setFoodLog(log);
                copy.setDefinition(definition);
                copy.setValue(nutrient.value());
                copy.setSource(parseSource(nutrient.source()));
                copy.setStatus(parseStatus(nutrient.status()));
                log.getNutrientSnapshot().add(copy);
            });
        }
        for (SharedRecipeIngredient ingredient : item.recipeIngredients()) {
            FoodLogRecipeIngredient copy = new FoodLogRecipeIngredient();
            copy.setFoodLog(log);
            copy.setFood(foods.findById(ingredient.foodId()).orElseThrow(() -> new NotFoundException("Un ingrediente compartido ya no existe.")));
            copy.setQuantity(ingredient.quantity());
            copy.setUnit(ingredient.unit());
            log.getRecipeIngredients().add(copy);
        }
        return foodLogs.save(log);
    }

    private SharedMealItem snapshot(FoodLog log) {
        MealItemType itemType = log.getItemType();
        if (itemType == null) throw new BadRequestException("La comida contiene un registro inválido.");
        if (itemType == MealItemType.FOOD && log.getFood() == null) throw new BadRequestException("La comida contiene un alimento inválido.");
        return new SharedMealItem(itemType, log.getFood() == null ? null : log.getFood().getId(),
                log.getRecipe() == null ? null : log.getRecipe().getId(), itemType == MealItemType.RECIPE
                        ? log.getRecipe().getName() : log.getFood() == null ? log.getAiEstimateName() : log.getFood().getName(),
                log.getQuantity(), log.getUnit(), log.getCalories(), log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams(),
                log.getAiEstimateConfidence(), log.getAiEstimateDetails(), log.getNutrientSnapshot().stream().map(this::snapshot).toList(),
                log.getRecipeIngredients().stream().map(this::snapshot).toList());
    }

    private SharedNutrient snapshot(FoodLogNutrient nutrient) {
        return new SharedNutrient(nutrient.getDefinition().getCode(), nutrient.getValue(),
                nutrient.getSource() == null ? NutrientSource.LEGACY.name() : nutrient.getSource().name(),
                nutrient.getStatus() == null ? NutrientStatus.MISSING.name() : nutrient.getStatus().name());
    }

    private SharedRecipeIngredient snapshot(FoodLogRecipeIngredient ingredient) {
        return new SharedRecipeIngredient(ingredient.getFood().getId(), ingredient.getQuantity(), ingredient.getUnit());
    }

    private MealSharePreviewResponse preview(MealShare share, List<SharedMealItem> snapshot, boolean alreadyAccepted) {
        List<MealShareItemResponse> items = snapshot.stream().map(item -> new MealShareItemResponse(item.itemType(), item.displayName(),
                item.quantity(), item.unit(), item.calories(), item.proteinGrams(), item.carbsGrams(), item.fatGrams(),
                item.itemType() == MealItemType.AI_ESTIMATE)).toList();
        BigDecimal protein = snapshot.stream().map(SharedMealItem::proteinGrams).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal carbs = snapshot.stream().map(SharedMealItem::carbsGrams).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fat = snapshot.stream().map(SharedMealItem::fatGrams).reduce(BigDecimal.ZERO, BigDecimal::add);
        int calories = snapshot.stream().map(SharedMealItem::calories).filter(value -> value != null).reduce(0, Integer::sum);
        return new MealSharePreviewResponse(share.getSourceDate(), MealType.valueOf(share.getSourceMealType()),
                mealLabel(MealType.valueOf(share.getSourceMealType())), calories, protein, carbs, fat, items, share.getExpiresAt(), alreadyAccepted);
    }

    private MealShare activeShare(String token) {
        MealShare share = shares.findByTokenHash(hash(token)).orElseThrow(() -> new NotFoundException("El enlace de comida no existe o venció."));
        if (share.getRevokedAt() != null || share.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new NotFoundException("El enlace de comida no existe o venció.");
        }
        return share;
    }

    private MealShare activeShareForUpdate(String token) {
        MealShare share = shares.findByTokenHashForUpdate(hash(token)).orElseThrow(() -> new NotFoundException("El enlace de comida no existe o venció."));
        if (share.getRevokedAt() != null || share.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new NotFoundException("El enlace de comida no existe o venció.");
        }
        return share;
    }

    private String writeSnapshot(List<SharedMealItem> snapshot) {
        try { return objectMapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException error) { throw new BadRequestException("No se pudo preparar la comida para compartir."); }
    }

    private List<SharedMealItem> readSnapshot(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<SharedMealItem>>() { }); }
        catch (JsonProcessingException error) { throw new BadRequestException("El enlace de comida no es válido."); }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private String hash(String token) {
        if (token == null || token.isBlank()) throw new NotFoundException("El enlace de comida no existe o venció.");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return TOKEN_ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 no está disponible", error); }
    }

    private NutrientSource parseSource(String value) {
        try { return value == null ? NutrientSource.LEGACY : NutrientSource.valueOf(value); }
        catch (IllegalArgumentException error) { return NutrientSource.LEGACY; }
    }

    private NutrientStatus parseStatus(String value) {
        try { return value == null ? NutrientStatus.MISSING : NutrientStatus.valueOf(value); }
        catch (IllegalArgumentException error) { return NutrientStatus.MISSING; }
    }

    private String mealLabel(MealType type) {
        return switch (type) {
            case BREAKFAST -> "Desayuno";
            case LUNCH -> "Almuerzo";
            case AFTERNOON_SNACK -> "Merienda";
            case DINNER -> "Cena";
        };
    }

    private record SharedMealItem(MealItemType itemType, Long foodId, Long recipeId, String displayName,
            BigDecimal quantity, FoodUnit unit, Integer calories, BigDecimal proteinGrams, BigDecimal carbsGrams,
            BigDecimal fatGrams, Integer aiEstimateConfidence, String aiEstimateDetails, List<SharedNutrient> nutrients,
            List<SharedRecipeIngredient> recipeIngredients) { }

    private record SharedNutrient(String code, BigDecimal value, String source, String status) { }

    private record SharedRecipeIngredient(Long foodId, BigDecimal quantity, FoodUnit unit) { }
}
