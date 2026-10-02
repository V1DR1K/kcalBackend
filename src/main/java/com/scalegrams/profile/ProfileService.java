package com.scalegrams.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Comparator;
import com.scalegrams.common.ConflictException;
import com.scalegrams.profile.ProfileDtos.PlanTimelinePreview;
import com.scalegrams.profile.ProfileDtos.PlanVersionRequest;
import com.scalegrams.profile.ProfileDtos.ConfirmPlanTimelineRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.profile.ProfileDtos.NutritionPlanPresetResponse;
import com.scalegrams.profile.ProfileDtos.NutritionPlanResponse;
import com.scalegrams.profile.ProfileDtos.ProfileResponse;
import com.scalegrams.profile.ProfileDtos.UpdateProfileRequest;
import com.scalegrams.profile.ProfileDtos.UpsertNutritionPlanRequest;
import com.scalegrams.profile.ProfileDtos.UpsertWeightEntryRequest;
import com.scalegrams.profile.ProfileDtos.WeightEntryResponse;
import com.scalegrams.user.AppUser;
import com.scalegrams.user.UserRepository;

@Service
public class ProfileService {
    private final UserRepository users;
    private final NutritionPlanRepository nutritionPlans;
    private final WeightEntryRepository weightEntries;

    public ProfileService(UserRepository users, NutritionPlanRepository nutritionPlans,
            WeightEntryRepository weightEntries) {
        this.users = users;
        this.nutritionPlans = nutritionPlans;
        this.weightEntries = weightEntries;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(AppUser user) {
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse update(AppUser user, UpdateProfileRequest request) {
        if (request.fullName() != null && !request.fullName().isBlank()) user.setFullName(request.fullName());
        if (request.weightKg() != null) {
            LocalDate today = LocalDate.now();
            WeightEntry entry = weightEntries.findByUserAndEntryDate(user, today).orElse(null);
            if (entry == null) {
                entry = new WeightEntry();
                entry.setUser(user);
                entry.setEntryDate(today);
            }
            entry.setWeightKg(request.weightKg());
            weightEntries.save(entry);
            user.setWeightKg(request.weightKg());
        }
        if (request.heightCm() != null) user.setHeightCm(request.heightCm());
        if (request.birthDate() != null) user.setBirthDate(request.birthDate());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.activityLevel() != null) user.setActivityLevel(request.activityLevel());
        if (request.goal() != null) user.setGoal(request.goal());
        if (request.targetWeightKg() != null) user.setTargetWeightKg(request.targetWeightKg());
        if (request.nutritionStyle() != null) user.setNutritionStyle(request.nutritionStyle());
        return toResponse(users.save(user));
    }

    @Transactional(readOnly = true)
    public List<WeightEntryResponse> weightEntries(AppUser user) {
        return weightEntries.findByUserOrderByEntryDateAsc(user).stream().map(this::toWeightResponse).toList();
    }

    @Transactional
    public WeightEntryResponse upsertWeightEntry(AppUser user, UpsertWeightEntryRequest request) {
        LocalDate entryDate = request.entryDate() == null ? LocalDate.now() : request.entryDate();
        WeightEntry entry = weightEntries.findByUserAndEntryDate(user, entryDate).orElse(null);
        if (entry == null) {
            entry = new WeightEntry();
            entry.setUser(user);
            entry.setEntryDate(entryDate);
        }
        entry.setWeightKg(request.weightKg());
        user.setWeightKg(request.weightKg());
        users.save(user);
        return toWeightResponse(weightEntries.save(entry));
    }

    @Transactional
    public void deleteWeightEntry(AppUser user, Long id) {
        WeightEntry entry = weightEntries.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Registro de peso no encontrado."));
        weightEntries.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<NutritionPlanResponse> plans(AppUser user) {
        List<NutritionPlan> all = nutritionPlans.findByUserOrderByStartDateDescIdDesc(user);
        return all.stream().map(plan -> toPlanResponse(plan, all)).toList();
    }

    @Transactional(readOnly = true)
    public NutritionPlanResponse activePlan(AppUser user, LocalDate date) {
        return toPlanResponse(resolvePlan(user, date == null ? LocalDate.now() : date));
    }

    @Transactional
    public NutritionPlanResponse createPlan(AppUser user, UpsertNutritionPlanRequest request) {
        lockUser(user);
        validatePlan(user, request, null);
        if (request.status() != null && request.status() != NutritionPlanStatus.ALTERNATIVE)
            throw new BadRequestException("Guardá una alternativa y confirmá su programación.");
        NutritionPlan plan = new NutritionPlan(); plan.setUser(user); applyPlan(plan, request);
        // Old clients keep their wire contract, without implicitly overwriting another plan.
        plan.setStatus(request.status() == null ? NutritionPlanStatus.SCHEDULED : NutritionPlanStatus.ALTERNATIVE);
        plan.setActive(plan.getStatus() == NutritionPlanStatus.SCHEDULED);
        if (plan.isActive()) rejectSameStart(user, plan);
        nutritionPlans.saveAndFlush(plan);
        return toPlanResponse(plan);
    }

    @Transactional
    public NutritionPlanResponse updatePlan(AppUser user, Long id, UpsertNutritionPlanRequest request) {
        lockUser(user);
        NutritionPlan plan = requirePlan(user, id); checkPlanVersion(plan, request.version());
        validatePlan(user, request, id);
        if (plan.getStatus() == NutritionPlanStatus.SCHEDULED &&
                (!Objects.equals(plan.getStartDate(), request.startDate()) || !Objects.equals(plan.getEndDate(), request.endDate())))
            throw new BadRequestException("Para cambiar la vigencia, creá una alternativa y confirmá su programación.");
        if (request.status() != null && request.status() != plan.getStatus())
            throw new BadRequestException("Usá la programación o cancelación para cambiar el estado del plan.");
        applyPlan(plan, request); nutritionPlans.saveAndFlush(plan);
        return toPlanResponse(plan);
    }

    @Transactional
    public void deletePlan(AppUser user, Long id) {
        lockUser(user);
        NutritionPlan plan = requirePlan(user, id);
        if (toPlanResponse(plan).current()) throw new BadRequestException("Cancelá la programación antes de archivar el plan vigente.");
        plan.setStatus(NutritionPlanStatus.ARCHIVED); plan.setActive(false); plan.setUpdatedAt(OffsetDateTime.now());
        nutritionPlans.saveAndFlush(plan);
    }

    @Transactional(readOnly = true)
    public PlanTimelinePreview previewTimeline(AppUser user, Long id, PlanVersionRequest request, boolean cancel) {
        NutritionPlan plan = requirePlan(user, id); checkPlanVersion(plan, request.version());
        return timelinePreview(user, plan, cancel);
    }

    @Transactional
    public NutritionPlanResponse confirmTimeline(AppUser user, Long id, ConfirmPlanTimelineRequest request, boolean cancel) {
        lockUser(user);
        NutritionPlan plan = requirePlan(user, id); checkPlanVersion(plan, request.version());
        PlanTimelinePreview preview = timelinePreview(user, plan, cancel);
        if (!Objects.equals(preview.previewToken(), request.previewToken()))
            throw new ConflictException("La programación cambió. Revisá una nueva vista previa antes de confirmar.");
        if (!cancel) for (NutritionPlan displaced : nutritionPlans.findByUserOrderByStartDateDescIdDesc(user)) {
            if (!Objects.equals(displaced.getId(), id) && displaced.getStatus() == NutritionPlanStatus.SCHEDULED
                    && displaced.getStartDate().equals(plan.getStartDate())) {
                displaced.setStatus(NutritionPlanStatus.ARCHIVED); displaced.setActive(false); displaced.setUpdatedAt(OffsetDateTime.now());
            }
        }
        plan.setStatus(cancel ? NutritionPlanStatus.ALTERNATIVE : NutritionPlanStatus.SCHEDULED);
        plan.setActive(!cancel); plan.setUpdatedAt(OffsetDateTime.now()); nutritionPlans.saveAndFlush(plan);
        return toPlanResponse(plan);
    }

    private PlanTimelinePreview timelinePreview(AppUser user, NutritionPlan candidate, boolean cancel) {
        if (cancel && candidate.getStatus() != NutritionPlanStatus.SCHEDULED
                || !cancel && candidate.getStatus() != NutritionPlanStatus.ALTERNATIVE)
            throw new BadRequestException("El estado del plan cambió. Recargá antes de continuar.");
        List<NutritionPlan> all = nutritionPlans.findByUserOrderByStartDateDescIdDesc(user);
        List<NutritionPlan> after = new ArrayList<>();
        for (NutritionPlan source : all) {
            NutritionPlan copy = planCopy(source);
            if (Objects.equals(copy.getId(), candidate.getId())) copy.setStatus(cancel ? NutritionPlanStatus.ALTERNATIVE : NutritionPlanStatus.SCHEDULED);
            else if (!cancel && copy.getStatus() == NutritionPlanStatus.SCHEDULED && copy.getStartDate().equals(candidate.getStartDate())) copy.setStatus(NutritionPlanStatus.ARCHIVED);
            after.add(copy);
        }
        String action = cancel ? "CANCEL" : "SCHEDULE";
        String fingerprint = action + ":" + candidate.getId() + ":" + all.stream().sorted(Comparator.comparing(NutritionPlan::getId))
                .map(item -> item.getId() + ":" + item.getVersion() + ":" + item.getStatus()).collect(java.util.stream.Collectors.joining("|"));
        String token;
        try { token = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        List<NutritionPlanResponse> beforeViews = timelineViews(all);
        List<NutritionPlanResponse> afterViews = timelineViews(after);
        List<Long> affected = all.stream().filter(item -> !Objects.equals(toPlanResponse(item, all), toPlanResponse(after.stream()
                .filter(changed -> Objects.equals(changed.getId(), item.getId())).findFirst().orElseThrow(), after)))
                .map(NutritionPlan::getId).toList();
        return new PlanTimelinePreview(action, beforeViews, afterViews, affected, token);
    }

    private List<NutritionPlanResponse> timelineViews(List<NutritionPlan> plans) {
        return plans.stream().filter(item -> item.getStatus() == NutritionPlanStatus.SCHEDULED)
                .sorted(Comparator.comparing(NutritionPlan::getStartDate).thenComparing(NutritionPlan::getId))
                .map(item -> toPlanResponse(item, plans)).toList();
    }

    private NutritionPlan requirePlan(AppUser user, Long id) {
        return nutritionPlans.findByIdAndUser(id, user).orElseThrow(() -> new NotFoundException("Plan alimenticio no encontrado."));
    }

    private void checkPlanVersion(NutritionPlan plan, Long version) {
        if (version != null && !Objects.equals(version, plan.getVersion())) throw new ConflictException("El plan cambió. Recargá antes de guardar.");
    }

    private void rejectSameStart(AppUser user, NutritionPlan plan) {
        if (nutritionPlans.findByUserAndActiveTrueOrderByStartDateDescIdDesc(user).stream()
                .anyMatch(item -> item.getStartDate().equals(plan.getStartDate())))
            throw new ConflictException("Ya hay un plan para esa fecha. Guardá una alternativa y revisá el reemplazo antes de confirmar.");
    }

    private static NutritionPlan planCopy(NutritionPlan source) {
        NutritionPlan copy = new NutritionPlan(); org.springframework.beans.BeanUtils.copyProperties(source, copy); return copy;
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

    public NutritionPlan resolvePlanFromRange(AppUser user, LocalDate date, List<NutritionPlan> effectivePlans) {
        return effectivePlans.stream().filter(plan -> !plan.getStartDate().isAfter(date)
                && (plan.getEndDate() == null || !plan.getEndDate().isBefore(date))).findFirst()
                .orElseGet(() -> fallbackPlan(user, date));
    }

    @Transactional(readOnly = true)
    public List<NutritionPlan> plansForRange(AppUser user, LocalDate start, LocalDate end) {
        List<NutritionPlan> timeline = nutritionPlans.findByUserAndActiveTrueOrderByStartDateDescIdDesc(user);
        return timeline.stream().map(source -> { NutritionPlan copy = planCopy(source);
            copy.setEndDate(NutritionPlanTimeline.effectiveEnd(source, timeline)); return copy; })
            .filter(item -> !item.getStartDate().isAfter(end) && (item.getEndDate() == null || !item.getEndDate().isBefore(start))).toList();
    }

    private WeightEntryResponse toWeightResponse(WeightEntry entry) {
        return new WeightEntryResponse(entry.getId(), entry.getEntryDate(), entry.getWeightKg());
    }

    private ProfileResponse toResponse(AppUser user) {
        Integer age = user.getBirthDate() == null ? null : Period.between(user.getBirthDate(), LocalDate.now()).getYears();
        NutritionPlan effective = resolvePlan(user, LocalDate.now());
        return new ProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPlanName(),
                user.getNutritionStyle(), user.getWeightKg(), user.getHeightCm(), age, user.getGender(),
                user.getActivityLevel(), user.getGoal(), user.getTargetWeightKg(), effective.getDailyCalories(),
                effective.getProteinGoalGrams(), effective.getCarbsGoalGrams(), effective.getFatGoalGrams(),
                effective.getId() == null ? "MANUAL" : "SCHEDULED", effective.getId(), effective.getName(), LocalDate.now());
    }

    private void validatePlan(AppUser user, UpsertNutritionPlanRequest request, Long excludedId) {
        String normalizedName = request.name().trim();
        if (nutritionPlans.findByUserOrderByStartDateDescIdDesc(user).stream().anyMatch(item -> item.getStatus() != NutritionPlanStatus.ARCHIVED
                && !Objects.equals(item.getId(), excludedId) && item.getName().equalsIgnoreCase(normalizedName))) {
            throw new BadRequestException("Ya existe un plan con ese nombre.");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("La fecha fin no puede ser anterior al inicio.");
        }
        BigDecimal sum = request.proteinPercent().add(request.carbsPercent()).add(request.fatPercent()).setScale(1, RoundingMode.HALF_UP);
        if (sum.compareTo(BigDecimal.valueOf(100).setScale(1, RoundingMode.HALF_UP)) != 0) {
            throw new BadRequestException("La suma de macros debe dar 100%.");
        }
    }

    private void lockUser(AppUser user) {
        users.findByIdForUpdate(user.getId()).orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
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
        return toPlanResponse(plan, nutritionPlans.findByUserOrderByStartDateDescIdDesc(plan.getUser()));
    }

    private NutritionPlanResponse toPlanResponse(NutritionPlan plan, List<NutritionPlan> timeline) {
        LocalDate effectiveEnd = NutritionPlanTimeline.effectiveEnd(plan, timeline);
        LocalDate today = LocalDate.now();
        boolean current = plan.getStatus() == NutritionPlanStatus.SCHEDULED && !plan.getStartDate().isAfter(today)
                && (effectiveEnd == null || !effectiveEnd.isBefore(today));
        return new NutritionPlanResponse(plan.getId(), plan.getName(), plan.getDailyCalories(), plan.getProteinPercent(),
                plan.getCarbsPercent(), plan.getFatPercent(), plan.getProteinGoalGrams(), plan.getCarbsGoalGrams(),
                plan.getFatGoalGrams(), plan.getStartDate(), plan.getEndDate(), current, plan.getStatus(), plan.getVersion(),
                effectiveEnd, plan.getId() == null ? "MANUAL" : plan.getStatus().name());
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
}
