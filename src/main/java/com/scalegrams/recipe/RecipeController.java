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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    private final RecipeService recipeService;
    private final CurrentUser currentUser;

    public RecipeController(RecipeService recipeService, CurrentUser currentUser) {
        this.recipeService = recipeService;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<RecipeResponse> search(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recipeService.search(q, page, size);
    }

    @GetMapping("/{id}")
    RecipeResponse find(@PathVariable Long id) {
        return recipeService.find(id);
    }

    @GetMapping("/mine")
    PageResponse<RecipeResponse> mine(Authentication authentication, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return recipeService.searchOwned(currentUser.from(authentication), q, page, size);
    }

    @GetMapping("/explore/users")
    java.util.List<RecipeOwnerResponse> authors(Authentication authentication) {
        return recipeService.authors(currentUser.from(authentication));
    }

    @GetMapping("/explore/users/{ownerId}")
    PageResponse<RecipeResponse> byAuthor(@PathVariable Long ownerId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return recipeService.searchByOwner(ownerId, q, page, size);
    }

    @PostMapping
    RecipeResponse create(Authentication authentication, @Valid @RequestBody CreateRecipeRequest request) {
        return recipeService.create(currentUser.from(authentication), request);
    }

    @PostMapping("/from-meal")
    RecipeFromMealResponse createFromMeal(Authentication authentication,
            @Valid @RequestBody CreateRecipeFromMealRequest request) {
        return recipeService.createFromMeal(currentUser.from(authentication), request);
    }

    @PostMapping("/{id}/copy")
    RecipeResponse copy(Authentication authentication, @PathVariable Long id) {
        return recipeService.copy(currentUser.from(authentication), id);
    }

    @PutMapping("/{id}")
    RecipeResponse update(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody CreateRecipeRequest request) {
        return recipeService.update(currentUser.from(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        recipeService.delete(currentUser.from(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview")
    NutritionPreviewResponse preview(@Valid @RequestBody CreateRecipeRequest request) {
        return recipeService.preview(request);
    }
}
