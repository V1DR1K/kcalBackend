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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.vitalitypeak.kcal.common.CurrentUser;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddFoodLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddMealLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AddWaterRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.DashboardResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodLogResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.HistoryResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.MealTypeResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.UpdateFoodLogRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.UpdateRecipeLogIngredientsRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AiEstimateResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AiEstimateUsageResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.ConfirmAiEstimateRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.AiTranscriptionResponse;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.RefineAiEstimateRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.SaveAiEstimateItemRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.UpdateAiEstimateRequest;
import com.vitalitypeak.kcal.nutrition.NutritionDtos.FoodResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {
    private final NutritionService nutritionService;
    private final AiNutritionService aiNutritionService;
    private final CurrentUser currentUser;

    public NutritionController(NutritionService nutritionService, AiNutritionService aiNutritionService, CurrentUser currentUser) {
        this.nutritionService = nutritionService;
        this.aiNutritionService = aiNutritionService;
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

    @GetMapping("/ai-estimates/usage")
    AiEstimateUsageResponse aiEstimateUsage(Authentication authentication) {
        return aiNutritionService.usage(currentUser.from(authentication));
    }

    @PostMapping(value = "/ai-estimates", consumes = "multipart/form-data")
    AiEstimateResponse estimateMeal(Authentication authentication, @RequestPart("image") MultipartFile image,
            @RequestPart(value = "context", required = false) String context) {
        return aiNutritionService.analyze(currentUser.from(authentication), image, context);
    }

    @PostMapping(value = "/ai-estimates/refinements", consumes = "multipart/form-data")
    AiEstimateResponse refineMeal(Authentication authentication, @RequestPart("image") MultipartFile image,
            @RequestPart(value = "context", required = false) String context,
            @Valid @RequestPart("request") RefineAiEstimateRequest request) {
        return aiNutritionService.refine(currentUser.from(authentication), image, context, request);
    }

    @PostMapping(value = "/ai-estimates/transcriptions", consumes = "multipart/form-data")
    AiTranscriptionResponse transcribeMealNote(Authentication authentication, @RequestPart("audio") MultipartFile audio) {
        return aiNutritionService.transcribe(currentUser.from(authentication), audio);
    }

    @PostMapping("/ai-estimates/confirm")
    FoodLogResponse confirmAiEstimate(Authentication authentication, @Valid @RequestBody ConfirmAiEstimateRequest request) {
        return nutritionService.addAiEstimate(currentUser.from(authentication), request);
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

    @DeleteMapping("/food-logs")
    ResponseEntity<Void> deleteMealLogs(Authentication authentication, @RequestParam MealType mealType,
            @RequestParam(required = false) LocalDate date) {
        nutritionService.deleteMealLogs(currentUser.from(authentication), mealType, date);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/food-logs/{id}")
    FoodLogResponse updateFoodLog(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpdateFoodLogRequest request) {
        return nutritionService.updateFoodLog(currentUser.from(authentication), id, request);
    }

    @PutMapping("/food-logs/{id}/ai-estimate")
    FoodLogResponse updateAiEstimate(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpdateAiEstimateRequest request) {
        return nutritionService.updateAiEstimate(currentUser.from(authentication), id, request);
    }

    @PostMapping("/food-logs/{id}/ai-estimate/items/{itemIndex}/catalog")
    FoodResponse saveAiEstimateItemToCatalog(Authentication authentication, @PathVariable Long id,
            @PathVariable int itemIndex, @Valid @RequestBody SaveAiEstimateItemRequest request) {
        return nutritionService.saveAiEstimateItemToCatalog(currentUser.from(authentication), id, itemIndex, request);
    }

    @PutMapping("/food-logs/{id}/recipe-ingredients")
    FoodLogResponse updateRecipeLogIngredients(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpdateRecipeLogIngredientsRequest request) {
        return nutritionService.updateRecipeLogIngredients(currentUser.from(authentication), id, request);
    }

    @DeleteMapping("/food-logs/{id}/recipe-ingredients")
    ResponseEntity<Void> resetRecipeLogIngredients(Authentication authentication, @PathVariable Long id) {
        nutritionService.resetRecipeLogIngredients(currentUser.from(authentication), id);
        return ResponseEntity.noContent().build();
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
