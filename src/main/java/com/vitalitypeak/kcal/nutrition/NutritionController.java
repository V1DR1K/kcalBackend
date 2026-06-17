package com.vitalitypeak.kcal.nutrition;

import java.time.LocalDate;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vitalitypeak.kcal.common.CurrentUser;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddFoodLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddWaterRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.DashboardResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodLogResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.HistoryResponse;

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

    @PostMapping("/food-logs")
    FoodLogResponse addFood(Authentication authentication, @Valid @RequestBody AddFoodLogRequest request) {
        return nutritionService.addFoodLog(currentUser.from(authentication), request);
    }

    @PostMapping("/water-logs")
    void addWater(Authentication authentication, @Valid @RequestBody AddWaterRequest request) {
        nutritionService.addWater(currentUser.from(authentication), request);
    }

    @GetMapping("/history")
    HistoryResponse history(Authentication authentication, @RequestParam int year, @RequestParam int month) {
        return nutritionService.history(currentUser.from(authentication), year, month);
    }
}
