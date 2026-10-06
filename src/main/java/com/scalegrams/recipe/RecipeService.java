package com.scalegrams.recipe;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.PageResponse;
import com.scalegrams.common.PaginationProperties;
import com.scalegrams.common.SearchTextNormalizer;
import com.scalegrams.nutrition.NutritionDtos.RecipeResponse;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeFromMealRequest;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeRequest;
import com.scalegrams.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeFromMealResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeOwnerResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeResponse;
import com.scalegrams.nutrition.NutritionService;
import com.scalegrams.user.AppUser;

/**
 * Recipe query boundary. Mutating workflows still reuse NutritionService's
 * shared nutrition and archived-food rules during the decomposition.
 */
@Service
public class RecipeService {
    private final NutritionService nutritionService;
    private final RecipeRepository recipes;
    private final PaginationProperties pagination;

    public RecipeService(NutritionService nutritionService, RecipeRepository recipes, PaginationProperties pagination) {
        this.nutritionService = nutritionService;
        this.recipes = recipes;
        this.pagination = pagination;
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> search(String query, int page, int size) {
        Pageable pageable = pagination.pageRequest(page, size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        String normalizedQuery = SearchTextNormalizer.normalize(query);
        validateQuery(normalizedQuery);
        Page<Recipe> result = normalizedQuery.isBlank()
                ? recipes.findAllByDeletedAtIsNull(pageable)
                : recipes.findBySearchNameContainingAndDeletedAtIsNull(normalizedQuery, pageable);
        return summaryPage(result);
    }

    @Transactional(readOnly = true)
    public RecipeResponse find(Long id) {
        return nutritionService.findRecipe(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchOwned(AppUser user, String query, int page, int size) {
        Pageable pageable = recipePageable(page, size);
        String normalizedQuery = SearchTextNormalizer.normalize(query);
        validateQuery(normalizedQuery);
        Page<Recipe> result = normalizedQuery.isBlank()
                ? recipes.findByCreatedByIdAndDeletedAtIsNull(user.getId(), pageable)
                : recipes.findByCreatedByIdAndSearchNameContainingAndDeletedAtIsNull(user.getId(), normalizedQuery, pageable);
        return summaryPage(result);
    }

    @Transactional(readOnly = true)
    public List<RecipeOwnerResponse> authors(AppUser user) {
        return recipes.findAuthorCountsExcluding(user.getId()).stream()
                .map(author -> new RecipeOwnerResponse(author.getOwnerId(), author.getOwnerName(), author.getRecipeCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchByOwner(Long ownerId, String query, int page, int size) {
        Pageable pageable = recipePageable(page, size);
        String normalizedQuery = SearchTextNormalizer.normalize(query);
        validateQuery(normalizedQuery);
        Page<Recipe> result = normalizedQuery.isBlank()
                ? recipes.findByCreatedByIdAndDeletedAtIsNull(ownerId, pageable)
                : recipes.findByCreatedByIdAndSearchNameContainingAndDeletedAtIsNull(ownerId, normalizedQuery, pageable);
        return summaryPage(result);
    }

    @Transactional
    public RecipeResponse create(AppUser user, CreateRecipeRequest request) {
        return nutritionService.createRecipe(user, request);
    }

    @Transactional
    public RecipeFromMealResponse createFromMeal(AppUser user, CreateRecipeFromMealRequest request) {
        return nutritionService.createRecipeFromMeal(user, request);
    }

    @Transactional
    public RecipeResponse copy(AppUser user, Long id) {
        return nutritionService.copyRecipe(user, id);
    }

    @Transactional
    public RecipeResponse copy(AppUser user, Long id, java.util.Set<Long> acknowledged) { return nutritionService.copyRecipe(user, id, acknowledged); }

    @Transactional
    public RecipeResponse update(AppUser user, Long id, CreateRecipeRequest request) {
        return nutritionService.updateOwnedRecipe(user, id, request);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        nutritionService.deleteOwnedRecipe(user, id);
    }

    @Transactional(readOnly = true)
    public NutritionPreviewResponse preview(CreateRecipeRequest request) {
        return nutritionService.previewRecipe(request);
    }

    private Pageable recipePageable(int page, int size) {
        return pagination.pageRequest(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    private void validateQuery(String query) {
        if (!query.isBlank() && query.length() > 120) {
            throw new BadRequestException("La búsqueda no puede superar 120 caracteres.");
        }
    }

    private PageResponse<RecipeResponse> summaryPage(Page<Recipe> result) {
        if (result.isEmpty()) return PageResponse.from(result.map(recipe -> toSummary(recipe, 0)));
        var counts = recipes.countIngredientsForPage(result.getContent().stream().map(Recipe::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(RecipeRepository.RecipeIngredientCountProjection::getRecipeId,
                        item -> Math.toIntExact(item.getIngredientCount())));
        return PageResponse.from(result.map(recipe -> toSummary(recipe, counts.getOrDefault(recipe.getId(), 0))));
    }

    private RecipeResponse toSummary(Recipe recipe, int ingredientCount) {
        return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getRawTotalWeightGrams(),
                recipe.getRawTotalWeightGrams(), recipe.getCookedTotalWeightGrams(), recipe.getCalories(),
                recipe.getProteinGrams(), recipe.getCarbsGrams(), recipe.getFatGrams(), List.of(), List.of(), ingredientCount);
    }
}
