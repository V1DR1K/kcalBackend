package com.vitalitypeak.kcal.profile;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vitalitypeak.kcal.auth.NutritionGoalCalculator;
import com.vitalitypeak.kcal.profile.ProfileDtos.ProfileResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.UpdateProfileRequest;
import com.vitalitypeak.kcal.user.AppUser;
import com.vitalitypeak.kcal.user.UserRepository;

@Service
public class ProfileService {
    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    public ProfileResponse get(AppUser user) {
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse update(AppUser user, UpdateProfileRequest request) {
        if (request.fullName() != null && !request.fullName().isBlank()) user.setFullName(request.fullName());
        if (request.weightKg() != null) user.setWeightKg(request.weightKg());
        if (request.heightCm() != null) user.setHeightCm(request.heightCm());
        if (request.birthDate() != null) user.setBirthDate(request.birthDate());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.activityLevel() != null) user.setActivityLevel(request.activityLevel());
        if (request.goal() != null) user.setGoal(request.goal());
        if (request.targetWeightKg() != null) user.setTargetWeightKg(request.targetWeightKg());
        if (request.nutritionStyle() != null) user.setNutritionStyle(request.nutritionStyle());
        if (request.waterGoalLiters() != null) user.setWaterGoalLiters(request.waterGoalLiters());
        NutritionGoalCalculator.apply(user);
        return toResponse(users.save(user));
    }

    private ProfileResponse toResponse(AppUser user) {
        Integer age = user.getBirthDate() == null ? null : Period.between(user.getBirthDate(), LocalDate.now()).getYears();
        return new ProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPlanName(),
                user.getNutritionStyle(), user.getWeightKg(), user.getHeightCm(), age, user.getGender(),
                user.getActivityLevel(), user.getGoal(), user.getTargetWeightKg(), user.getDailyCalorieGoal(),
                user.getProteinGoalGrams(), user.getCarbsGoalGrams(), user.getFatGoalGrams(), user.getWaterGoalLiters());
    }
}
