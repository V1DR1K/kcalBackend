package com.scalegrams.nutrition;

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

import com.scalegrams.catalog.FoodCategory;
import com.scalegrams.common.CurrentUser;
import com.scalegrams.nutrition.NutritionDtos.CreateFoodRequest;
import com.scalegrams.nutrition.NutritionDtos.FoodResponse;
import com.scalegrams.nutrition.NutritionDtos.FoodSummaryResponse;
import com.scalegrams.nutrition.NutritionDtos.NutritionPreviewRequest;
import com.scalegrams.nutrition.NutritionDtos.NutritionPreviewResponse;
import com.scalegrams.common.PageResponse;
import com.scalegrams.nutrition.NutritionDtos.NutrientUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/foods")
public class FoodController {
    private final NutritionService nutritionService;
    private final FoodCatalogService foodCatalogService;
    private final FoodCatalogSearchService foodCatalogSearchService;
    private final CurrentUser currentUser;

    public FoodController(NutritionService nutritionService, FoodCatalogService foodCatalogService,
            FoodCatalogSearchService foodCatalogSearchService, CurrentUser currentUser) {
        this.nutritionService = nutritionService;
        this.foodCatalogService = foodCatalogService;
        this.foodCatalogSearchService = foodCatalogSearchService;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<FoodSummaryResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) FoodCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "${app.pagination.default-size:20}") int size) {
        return foodCatalogSearchService.search(q, category, page, size);
    }

    @PostMapping
    FoodResponse create(Authentication authentication, @Valid @RequestBody CreateFoodRequest request) {
        return foodCatalogService.create(request, currentUser.from(authentication));
    }

    @GetMapping("/mine")
    List<FoodResponse> mine(Authentication authentication) {
        return foodCatalogService.findCreatedBy(currentUser.from(authentication));
    }

    @GetMapping("/mine/deleted")
    List<FoodResponse> deleted(Authentication authentication) {
        return foodCatalogService.findDeletedCreatedBy(currentUser.from(authentication));
    }

    @PutMapping("/{id}")
    FoodResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody CreateFoodRequest request) {
        return foodCatalogService.update(id, request, currentUser.from(authentication));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        foodCatalogService.delete(id, currentUser.from(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    FoodResponse restore(Authentication authentication, @PathVariable Long id) {
        return foodCatalogService.restore(id, currentUser.from(authentication));
    }

    @GetMapping("/{id}")
    FoodResponse find(@PathVariable Long id) {
        return foodCatalogService.find(id);
    }

    @GetMapping("/nutrient-definitions")
    List<NutritionDtos.NutrientValueResponse> nutrientDefinitions() {
        return foodCatalogService.nutrientDefinitions();
    }

    @PostMapping("/{id}/enrich")
    FoodResponse enrich(Authentication authentication, @PathVariable Long id) {
        return foodCatalogService.enrich(id, currentUser.from(authentication));
    }

    @PutMapping("/{id}/nutrients")
    FoodResponse updateNutrients(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody NutrientUpdateRequest request) {
        return foodCatalogService.updateNutrients(id, request, currentUser.from(authentication));
    }

    @PostMapping("/enrich-existing")
    FoodCatalogService.EnrichmentReport enrichExisting(Authentication authentication,
            @RequestParam(defaultValue = "50") int limit) {
        return foodCatalogService.enrichCatalog(currentUser.from(authentication), limit);
    }

    @GetMapping("/{id}/preparations")
    List<FoodResponse> preparations(@PathVariable Long id) {
        return foodCatalogService.preparationOptions(id);
    }

    @GetMapping("/barcode/{barcode}")
    FoodResponse barcode(@PathVariable String barcode) {
        return foodCatalogService.findByBarcode(barcode);
    }

    @PostMapping("/preview")
    NutritionPreviewResponse preview(@Valid @RequestBody NutritionPreviewRequest request) {
        return nutritionService.preview(request.foodId(), request.quantity(), request.unit());
    }
}
