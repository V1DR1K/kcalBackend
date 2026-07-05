package com.vitalitypeak.kcal.nutrition;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import com.vitalitypeak.kcal.common.CurrentUser;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddFoodLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddMealLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddWaterRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.DashboardResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodLogResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.HistoryResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.MealTypeResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.UpdateFoodLogRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {
    private final NutritionService nutritionService;
    private final CurrentUser currentUser;

    public NutritionController(NutritionService nutritionService, CurrentUser currentUser) {
        this.nutritionService = nutritionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    DashboardResponse dashboard(Authentication authentication, @RequestParam(required = false) LocalDate date) {
        return nutritionService.dashboard(currentUser.from(authentication), date);
    }

    @GetMapping("/meal-types")
    List<MealTypeResponse> mealTypes() {
        return nutritionService.mealTypes();
    }

    @PostMapping("/food-logs")
    FoodLogResponse addFood(Authentication authentication, @Valid @RequestBody AddFoodLogRequest request) {
        return nutritionService.addFoodLog(currentUser.from(authentication), request);
    }

    @PostMapping("/meal-logs")
    FoodLogResponse addMealLog(Authentication authentication, @Valid @RequestBody AddMealLogRequest request) {
        return nutritionService.addMealLog(currentUser.from(authentication), request);
    }

    @PostMapping("/water-logs")
    void addWater(Authentication authentication, @Valid @RequestBody AddWaterRequest request) {
        nutritionService.addWater(currentUser.from(authentication), request);
    }

    @DeleteMapping("/food-logs/{id}")
    ResponseEntity<Void> deleteFoodLog(Authentication authentication, @PathVariable Long id) {
        nutritionService.deleteFoodLog(currentUser.from(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/food-logs/{id}")
    FoodLogResponse updateFoodLog(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpdateFoodLogRequest request) {
        return nutritionService.updateFoodLog(currentUser.from(authentication), id, request);
    }

    @DeleteMapping("/water-logs/latest")
    ResponseEntity<Void> deleteLatestWaterLog(Authentication authentication, @RequestParam(required = false) LocalDate date) {
        nutritionService.deleteLatestWaterLog(currentUser.from(authentication), date);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    HistoryResponse history(Authentication authentication, @RequestParam int year, @RequestParam int month) {
        return nutritionService.history(currentUser.from(authentication), year, month);
    }
}
