package com.vitalitypeak.kcal.recipe;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vitalitypeak.kcal.common.CurrentUser;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.CreateRecipeRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.PageResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.RecipeResponse;
import com.vitalitypeak.kcal.nutrition.NutritionService;

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

    @PostMapping
    RecipeResponse create(Authentication authentication, @Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.createRecipe(currentUser.from(authentication), request);
    }

    @PostMapping("/preview")
    NutritionPreviewResponse preview(@Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.previewRecipe(request);
    }
}
