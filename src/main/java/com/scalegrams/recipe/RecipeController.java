package com.scalegrams.recipe;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scalegrams.common.CurrentUser;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeRequest;
import com.scalegrams.nutrition.NutritionDtos.CreateRecipeFromMealRequest;
import com.scalegrams.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.scalegrams.nutrition.NutritionDtos.PageResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeOwnerResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeFromMealResponse;
import com.scalegrams.nutrition.NutritionDtos.RecipeResponse;
import com.scalegrams.nutrition.NutritionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    private final NutritionService nutritionService;
    private final CurrentUser currentUser;

    public RecipeController(NutritionService nutritionService, CurrentUser currentUser) {
        this.nutritionService = nutritionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<RecipeResponse> search(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return nutritionService.searchRecipes(q, page, size);
    }

    @GetMapping("/{id}")
    RecipeResponse find(@PathVariable Long id) {
        return nutritionService.findRecipe(id);
    }

    @GetMapping("/mine")
    PageResponse<RecipeResponse> mine(Authentication authentication, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return nutritionService.searchOwnedRecipes(currentUser.from(authentication), q, page, size);
    }

    @GetMapping("/explore/users")
    java.util.List<RecipeOwnerResponse> authors(Authentication authentication) {
        return nutritionService.recipeAuthors(currentUser.from(authentication));
    }

    @GetMapping("/explore/users/{ownerId}")
    PageResponse<RecipeResponse> byAuthor(@PathVariable Long ownerId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return nutritionService.searchRecipesByOwner(ownerId, q, page, size);
    }

    @PostMapping
    RecipeResponse create(Authentication authentication, @Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.createRecipe(currentUser.from(authentication), request);
    }

    @PostMapping("/from-meal")
    RecipeFromMealResponse createFromMeal(Authentication authentication,
            @Valid @RequestBody CreateRecipeFromMealRequest request) {
        return nutritionService.createRecipeFromMeal(currentUser.from(authentication), request);
    }

    @PostMapping("/{id}/copy")
    RecipeResponse copy(Authentication authentication, @PathVariable Long id) {
        return nutritionService.copyRecipe(currentUser.from(authentication), id);
    }

    @PutMapping("/{id}")
    RecipeResponse update(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.updateOwnedRecipe(currentUser.from(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        nutritionService.deleteOwnedRecipe(currentUser.from(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview")
    NutritionPreviewResponse preview(@Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.previewRecipe(request);
    }
}
