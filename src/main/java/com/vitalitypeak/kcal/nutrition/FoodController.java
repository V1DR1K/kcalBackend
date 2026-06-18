package com.vitalitypeak.kcal.nutrition;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.common.CurrentUser;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.CreateFoodRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.NutritionPreviewRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/foods")
public class FoodController {
    private final NutritionService nutritionService;
    private final CurrentUser currentUser;

    public FoodController(NutritionService nutritionService, CurrentUser currentUser) {
        this.nutritionService = nutritionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<FoodResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) FoodCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return nutritionService.searchFoods(q, category, page, size);
    }

    @PostMapping
    FoodResponse create(Authentication authentication, @Valid @RequestBody CreateFoodRequest request) {
        currentUser.from(authentication);
        return nutritionService.createFood(request);
    }

    @GetMapping("/{id}")
    FoodResponse find(@PathVariable Long id) {
        return nutritionService.findFood(id);
    }

    @GetMapping("/barcode/{barcode}")
    FoodResponse barcode(@PathVariable String barcode) {
        return nutritionService.findByBarcode(barcode);
    }

    @PostMapping("/preview")
    NutritionPreviewResponse preview(@Valid @RequestBody NutritionPreviewRequest request) {
        return nutritionService.preview(request.foodId(), request.quantity(), request.unit());
    }
}
