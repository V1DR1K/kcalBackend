package com.vitalitypeak.kcal.profile;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vitalitypeak.kcal.common.CurrentUser;
import com.vitalitypeak.kcal.profile.ProfileDtos.NutritionPlanPresetResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.NutritionPlanResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.ProfileResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.UpdateProfileRequest;
import com.vitalitypeak.kcal.profile.ProfileDtos.UpsertNutritionPlanRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public ProfileController(ProfileService profileService, CurrentUser currentUser) {
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    @GetMapping
    ProfileResponse get(Authentication authentication) {
        return profileService.get(currentUser.from(authentication));
    }

    @PatchMapping
    ProfileResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(currentUser.from(authentication), request);
    }

    @GetMapping("/nutrition-plans")
    List<NutritionPlanResponse> plans(Authentication authentication) {
        return profileService.plans(currentUser.from(authentication));
    }

    @GetMapping("/nutrition-plans/active")
    NutritionPlanResponse activePlan(Authentication authentication, @RequestParam(required = false) LocalDate date) {
        return profileService.activePlan(currentUser.from(authentication), date);
    }

    @PostMapping("/nutrition-plans")
    NutritionPlanResponse createPlan(Authentication authentication, @Valid @RequestBody UpsertNutritionPlanRequest request) {
        return profileService.createPlan(currentUser.from(authentication), request);
    }

    @PutMapping("/nutrition-plans/{id}")
    NutritionPlanResponse updatePlan(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpsertNutritionPlanRequest request) {
        return profileService.updatePlan(currentUser.from(authentication), id, request);
    }

    @GetMapping("/nutrition-plan-presets")
    List<NutritionPlanPresetResponse> presets() {
        return profileService.presets();
    }
}
