package com.vitalitypeak.kcal.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vitalitypeak.kcal.auth.NutritionGoalCalculator;
import com.vitalitypeak.kcal.common.BadRequestException;
import com.vitalitypeak.kcal.common.NotFoundException;
import com.vitalitypeak.kcal.profile.ProfileDtos.NutritionPlanPresetResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.NutritionPlanResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.ProfileResponse;
import com.vitalitypeak.kcal.profile.ProfileDtos.UpdateProfileRequest;
import com.vitalitypeak.kcal.profile.ProfileDtos.UpsertNutritionPlanRequest;
import com.vitalitypeak.kcal.user.AppUser;
import com.vitalitypeak.kcal.user.UserRepository;

@Service
public class ProfileService {
    private final UserRepository users;
    private final NutritionPlanRepository nutritionPlans;

    public ProfileService(UserRepository users, NutritionPlanRepository nutritionPlans) {
        this.users = users;
        this.nutritionPlans = nutritionPlans;
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<NutritionPlanResponse> plans(AppUser user) {
        return nutritionPlans.findByUserOrderByStartDateDesc(user).stream().map(this::toPlanResponse).toList();
    }

    @Transactional(readOnly = true)
    public NutritionPlanResponse activePlan(AppUser user, LocalDate date) {
        return toPlanResponse(resolvePlan(user, date == null ? LocalDate.now() : date));
    }

    @Transactional
    public NutritionPlanResponse createPlan(AppUser user, UpsertNutritionPlanRequest request) {
        validatePlan(request);
        LocalDate previousEnd = request.startDate().minusDays(1);
        nutritionPlans.findActiveForUserAndDate(user, request.startDate())
                .filter(plan -> plan.getEndDate() == null || !plan.getEndDate().isBefore(request.startDate()))
                .ifPresent(plan -> {
                    if (plan.getStartDate().equals(request.startDate())) {
                        throw new BadRequestException("Ya existe un plan que empieza ese dia. Editalo o elegi otra fecha.");
                    }
                    plan.setEndDate(previousEnd);
                    plan.setUpdatedAt(OffsetDateTime.now());
                });
        nutritionPlans.flush();
        ensureNoOverlap(user, request.startDate(), openEnd(request.endDate()), null);
        NutritionPlan plan = new NutritionPlan();
        plan.setUser(user);
        applyPlan(plan, request);
        syncUserFallback(user, plan);
        users.save(user);
        return toPlanResponse(nutritionPlans.save(plan));
    }

    @Transactional
    public NutritionPlanResponse updatePlan(AppUser user, Long id, UpsertNutritionPlanRequest request) {
        validatePlan(request);
        NutritionPlan plan = nutritionPlans.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Plan alimenticio no encontrado."));
        ensureNoOverlap(user, request.startDate(), openEnd(request.endDate()), id);
        applyPlan(plan, request);
        syncUserFallback(user, plan);
        users.save(user);
        return toPlanResponse(nutritionPlans.save(plan));
    }

    public List<NutritionPlanPresetResponse> presets() {
        return List.of(
                preset("balanced", "Balanceado", "Punto de partida simple para la mayoria: energia estable y facil adherencia.", 2200, 25, 50, 25),
                preset("high_protein", "Alto en proteina", "Prioriza saciedad y masa muscular sin llevar grasas o carbohidratos a extremos.", 2200, 35, 40, 25),
                preset("moderate_low_carb", "Bajo en carbohidratos moderado", "Reduce carbohidratos sin hacer una dieta extrema; requiere elegir grasas de calidad.", 2200, 35, 30, 35),
                preset("mediterranean", "Mediterraneo aproximado", "Enfoque flexible con grasas saludables, carbohidratos de calidad y proteina moderada.", 2200, 20, 45, 35));
    }

    @Transactional(readOnly = true)
    public NutritionPlan resolvePlan(AppUser user, LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return nutritionPlans.findActiveForUserAndDate(user, targetDate).orElseGet(() -> fallbackPlan(user, targetDate));
    }

    private ProfileResponse toResponse(AppUser user) {
        Integer age = user.getBirthDate() == null ? null : Period.between(user.getBirthDate(), LocalDate.now()).getYears();
        return new ProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPlanName(),
                user.getNutritionStyle(), user.getWeightKg(), user.getHeightCm(), age, user.getGender(),
                user.getActivityLevel(), user.getGoal(), user.getTargetWeightKg(), user.getDailyCalorieGoal(),
                user.getProteinGoalGrams(), user.getCarbsGoalGrams(), user.getFatGoalGrams(), user.getWaterGoalLiters());
    }

    private void validatePlan(UpsertNutritionPlanRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("La fecha fin no puede ser anterior al inicio.");
        }
        BigDecimal sum = request.proteinPercent().add(request.carbsPercent()).add(request.fatPercent()).setScale(1, RoundingMode.HALF_UP);
        if (sum.compareTo(BigDecimal.valueOf(100).setScale(1, RoundingMode.HALF_UP)) != 0) {
            throw new BadRequestException("La suma de macros debe dar 100%.");
        }
    }

    private void ensureNoOverlap(AppUser user, LocalDate startDate, LocalDate endDate, Long ignoreId) {
        if (nutritionPlans.existsOverlappingPlanForUser(user, startDate, endDate, ignoreId)) {
            throw new BadRequestException("Ya existe un plan para ese rango de fechas.");
        }
    }

    private void applyPlan(NutritionPlan plan, UpsertNutritionPlanRequest request) {
        plan.setName(request.name().trim());
        plan.setDailyCalories(request.dailyCalories());
        plan.setProteinPercent(scalePercent(request.proteinPercent()));
        plan.setCarbsPercent(scalePercent(request.carbsPercent()));
        plan.setFatPercent(scalePercent(request.fatPercent()));
        plan.setProteinGoalGrams(grams(request.dailyCalories(), request.proteinPercent(), 4));
        plan.setCarbsGoalGrams(grams(request.dailyCalories(), request.carbsPercent(), 4));
        plan.setFatGoalGrams(grams(request.dailyCalories(), request.fatPercent(), 9));
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.setUpdatedAt(OffsetDateTime.now());
    }

    private void syncUserFallback(AppUser user, NutritionPlan plan) {
        LocalDate today = LocalDate.now();
        if (!plan.getStartDate().isAfter(today) && (plan.getEndDate() == null || !plan.getEndDate().isBefore(today))) {
            user.setNutritionStyle(plan.getName());
            user.setDailyCalorieGoal(plan.getDailyCalories());
            user.setProteinGoalGrams(plan.getProteinGoalGrams());
            user.setCarbsGoalGrams(plan.getCarbsGoalGrams());
            user.setFatGoalGrams(plan.getFatGoalGrams());
        }
    }

    private NutritionPlan fallbackPlan(AppUser user, LocalDate date) {
        NutritionPlan plan = new NutritionPlan();
        plan.setUser(user);
        plan.setName(user.getNutritionStyle() == null ? "Plan manual" : user.getNutritionStyle());
        plan.setDailyCalories(user.getDailyCalorieGoal());
        plan.setProteinGoalGrams(user.getProteinGoalGrams());
        plan.setCarbsGoalGrams(user.getCarbsGoalGrams());
        plan.setFatGoalGrams(user.getFatGoalGrams());
        int proteinCalories = user.getProteinGoalGrams() * 4;
        int carbsCalories = user.getCarbsGoalGrams() * 4;
        int fatCalories = user.getFatGoalGrams() * 9;
        int total = Math.max(1, proteinCalories + carbsCalories + fatCalories);
        plan.setProteinPercent(BigDecimal.valueOf((proteinCalories * 100.0) / total).setScale(1, RoundingMode.HALF_UP));
        plan.setCarbsPercent(BigDecimal.valueOf((carbsCalories * 100.0) / total).setScale(1, RoundingMode.HALF_UP));
        plan.setFatPercent(BigDecimal.valueOf((fatCalories * 100.0) / total).setScale(1, RoundingMode.HALF_UP));
        plan.setStartDate(date);
        return plan;
    }

    private NutritionPlanResponse toPlanResponse(NutritionPlan plan) {
        return new NutritionPlanResponse(plan.getId(), plan.getName(), plan.getDailyCalories(), plan.getProteinPercent(),
                plan.getCarbsPercent(), plan.getFatPercent(), plan.getProteinGoalGrams(), plan.getCarbsGoalGrams(),
                plan.getFatGoalGrams(), plan.getStartDate(), plan.getEndDate());
    }

    private NutritionPlanPresetResponse preset(String key, String name, String description, int calories,
            int protein, int carbs, int fat) {
        return new NutritionPlanPresetResponse(key, name, description, calories, BigDecimal.valueOf(protein),
                BigDecimal.valueOf(carbs), BigDecimal.valueOf(fat));
    }

    private static int grams(Integer calories, BigDecimal percent, int caloriesPerGram) {
        return BigDecimal.valueOf(calories).multiply(percent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(caloriesPerGram), 0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal scalePercent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static LocalDate openEnd(LocalDate endDate) {
        return endDate == null ? LocalDate.of(9999, 12, 31) : endDate;
    }
}
