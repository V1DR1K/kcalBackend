package com.scalegrams.recipe;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.nutrition.NutritionDtos.CreateRecipeFromMealRequest;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeRequest;
import com.scalegrams.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.scalegrams.nutrition.NutritionDtos.PageResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeFromMealResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeOwnerResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeResponse;
import com.scalegrams.nutrition.NutritionService;
import com.scalegrams.user.AppUser;

/**
 * Recipe application boundary. The implementation is intentionally delegated
 * while NutritionService is being decomposed, so the HTTP contract stays
 * stable during the migration.
 */
@Service
public class RecipeService {
    private final NutritionService nutritionService;

    public RecipeService(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> search(String query, int page, int size) {
        return nutritionService.searchRecipes(query, page, size);
    }

    @Transactional(readOnly = true)
    public RecipeResponse find(Long id) {
        return nutritionService.findRecipe(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchOwned(AppUser user, String query, int page, int size) {
        return nutritionService.searchOwnedRecipes(user, query, page, size);
    }

    @Transactional(readOnly = true)
    public List<RecipeOwnerResponse> authors(AppUser user) {
        return nutritionService.recipeAuthors(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> searchByOwner(Long ownerId, String query, int page, int size) {
        return nutritionService.searchRecipesByOwner(ownerId, query, page, size);
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
}
