package com.scalegrams.training;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.common.BadRequestException;
import com.scalegrams.common.ConflictException;
import com.scalegrams.common.NotFoundException;
import com.scalegrams.training.TrainingDtos.CompleteTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.CancelTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.CreateTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.DuplicateTrainingPlanRequest;
import com.scalegrams.training.TrainingDtos.PageResponse;
import com.scalegrams.training.TrainingDtos.ReorderRequest;
import com.scalegrams.training.TrainingDtos.TrainingCalendarDayResponse;
import com.scalegrams.training.TrainingDtos.TrainingCalendarSessionResponse;
import com.scalegrams.training.TrainingDtos.TrainingDashboardResponse;
import com.scalegrams.training.TrainingDtos.LegacyPlanDayResponse;
import com.scalegrams.training.TrainingDtos.TrainingExerciseResponse;
import com.scalegrams.training.TrainingDtos.TrainingCategoryResponse;
import com.scalegrams.training.TrainingDtos.TrainingModuleResponse;
import com.scalegrams.training.TrainingDtos.LegacyPlanExerciseResponse;
import com.scalegrams.training.TrainingDtos.LegacyTrainingPlanDetailResponse;
import com.scalegrams.training.TrainingDtos.LegacyTrainingPlanResponse;
import com.scalegrams.training.TrainingDtos.TrainingSessionExerciseRequest;
import com.scalegrams.training.TrainingDtos.TrainingSessionExerciseResponse;
import com.scalegrams.training.TrainingDtos.TrainingSessionResponse;
import com.scalegrams.training.TrainingDtos.TrainingSessionSummaryResponse;
import com.scalegrams.training.TrainingDtos.TrainingSetRequest;
import com.scalegrams.training.TrainingDtos.TrainingSetResponse;
import com.scalegrams.training.TrainingDtos.UpdateTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.UpsertExerciseRequest;
import com.scalegrams.training.TrainingDtos.UpsertTrainingCategoryRequest;
import com.scalegrams.training.TrainingDtos.LegacyPlanExerciseRequest;
import com.scalegrams.training.TrainingDtos.LegacyPlanRequest;
import com.scalegrams.training.TrainingDtos.LegacyPlanDayRequest;
import com.scalegrams.training.TrainingDtos.PlanDayRequest;
import com.scalegrams.training.TrainingDtos.PlanExerciseRequest;
import com.scalegrams.training.TrainingDtos.SkipTrainingPlanSessionRequest;
import com.scalegrams.training.TrainingDtos.TrainingPlanDetailResponse;
import com.scalegrams.training.TrainingDtos.TrainingPlanDayResponse;
import com.scalegrams.training.TrainingDtos.TrainingPlanExerciseResponse;
import com.scalegrams.training.TrainingDtos.TrainingPlanResolutionResponse;
import com.scalegrams.training.TrainingDtos.TrainingPlanResponse;
import com.scalegrams.training.TrainingDtos.TrainingPlanScheduleResponse;
import com.scalegrams.training.TrainingDtos.UpsertTrainingPlanRequest;
import com.scalegrams.training.TrainingDtos.WeeklyTrainingSummaryResponse;
import com.scalegrams.user.AppUser;

@Service
public class TrainingService {
    private final TrainingExerciseRepository exercises;
    private final TrainingCategoryRepository categories;
    private final TrainingPlanRepository presets;
    private final TrainingPlanDayRepository days;
    private final TrainingPlanExerciseRepository presetExercises;
    private final TrainingSessionRepository sessions;
    private final TrainingSessionExerciseRepository sessionExercises;
    private final TrainingSessionBaselineRepository baselines;

    public TrainingService(TrainingExerciseRepository exercises, TrainingCategoryRepository categories, TrainingPlanRepository presets,
            TrainingPlanDayRepository days, TrainingPlanExerciseRepository presetExercises,
            TrainingSessionRepository sessions, TrainingSessionExerciseRepository sessionExercises,
            TrainingSessionBaselineRepository baselines) {
        this.exercises = exercises;
        this.categories = categories;
        this.presets = presets;
        this.days = days;
        this.presetExercises = presetExercises;
        this.sessions = sessions;
        this.sessionExercises = sessionExercises;
        this.baselines = baselines;
    }

    @Transactional(readOnly = true)
    public List<TrainingModuleResponse> modules() {
        return List.of(
                new TrainingModuleResponse(TrainingModule.GYM, "Gimnasio"),
                new TrainingModuleResponse(TrainingModule.CALISTHENICS, "Calistenia"));
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingCategoryResponse> categories(AppUser user, String query, TrainingModule module,
            boolean includeInactive, int page, int size) {
        Page<TrainingCategory> result = categories.search(user, module, query == null ? "" : query.trim(),
                includeInactive, page(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return page(result, this::toCategoryResponse);
    }

    @Transactional
    public TrainingCategoryResponse createCategory(AppUser user, UpsertTrainingCategoryRequest request) {
        String name = normalized(request.name());
        String normalizedName = normalizedKey(name);
        if (categories.existsOwnedName(user, request.module(), normalizedName, null)
                || categories.findSystem(request.module(), normalizedName).isPresent()) {
            throw new BadRequestException("Ya existe una categoría con ese nombre para este módulo.");
        }
        TrainingCategory category = new TrainingCategory();
        category.setOwner(user);
        category.setModule(request.module());
        category.setName(name);
        category.setNormalizedName(normalizedName);
        category.setSystemCategory(false);
        category.setActive(request.active() == null || request.active());
        return toCategoryResponse(categories.save(category));
    }

    @Transactional
    public TrainingCategoryResponse updateCategory(AppUser user, Long id, UpsertTrainingCategoryRequest request) {
        TrainingCategory category = categories.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada."));
        String name = normalized(request.name());
        String normalizedName = normalizedKey(name);
        if (categories.existsOwnedName(user, request.module(), normalizedName, id)
                || categories.findSystem(request.module(), normalizedName).isPresent()) {
            throw new BadRequestException("Ya existe una categoría con ese nombre para este módulo.");
        }
        if (category.getModule() != request.module()) {
            throw new BadRequestException("No podés cambiar el módulo de una categoría.");
        }
        category.setName(name);
        category.setNormalizedName(normalizedName);
        if (request.active() != null) category.setActive(request.active());
        category.setUpdatedAt(OffsetDateTime.now());
        return toCategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(AppUser user, Long id) {
        TrainingCategory category = categories.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada."));
        OffsetDateTime now = OffsetDateTime.now();
        category.setActive(false);
        category.setDeletedAt(now);
        category.setUpdatedAt(now);
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingExerciseResponse> exercises(AppUser user, String query, TrainingModule module,
            Long categoryId, String categoryName, TrainingEquipment equipment, TrainingDifficulty difficulty,
            TrainingRegistrationType registrationType, boolean includeInactive, int page, int size) {
        Page<TrainingExercise> result = exercises.search(user, module, query == null ? "" : query.trim(), categoryId,
                categoryName == null ? null : normalizedKey(categoryName), equipment, difficulty, registrationType,
                includeInactive, page(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return page(result, this::toExerciseResponse);
    }

    public PageResponse<TrainingExerciseResponse> exercises(AppUser user, String query, TrainingModule module,
            int page, int size) {
        return exercises(user, query, module, null, null, null, null, null, false, page, size);
    }

    @Transactional(readOnly = true)
    public TrainingExerciseResponse exercise(AppUser user, Long id) {
        return toExerciseResponse(exercises.findSelectable(id, user)
                .orElseThrow(() -> new NotFoundException("Ejercicio no encontrado.")));
    }

    @Transactional
    public TrainingExerciseResponse createExercise(AppUser user, UpsertExerciseRequest request) {
        String name = normalized(request.name());
        boolean global = Boolean.TRUE.equals(request.global());
        TrainingExercise existing = global
                ? exercises.findGlobalByModuleAndName(request.module(), name).orElse(null)
                : exercises.findByOwnerAndModuleAndNameIgnoreCaseAndDeletedAtIsNull(user, request.module(), name)
                        .orElse(null);
        if (existing != null) {
            return toExerciseResponse(existing);
        }
        TrainingExercise exercise = new TrainingExercise();
        exercise.setOwner(global ? null : user);
        exercise.setName(name);
        exercise.setNormalizedName(normalizedKey(name));
        exercise.setModule(request.module());
        exercise.setDescription(blankToNull(request.description()));
        exercise.setCategory(resolveCategory(user, request.module(), request.categoryId(), request.category(), global));
        exercise.setGlobalExercise(global);
        exercise.setCode(resolveExerciseCode(user, request.module(), name, request.code(), null, global));
        exercise.setPrimaryMuscles(normalizedValues(request.primaryMuscles()));
        exercise.setSecondaryMuscles(normalizedValues(request.secondaryMuscles()));
        exercise.setEquipment(request.equipment() == null ? defaultEquipment(request.module()) : request.equipment());
        exercise.setDifficulty(request.difficulty() == null ? TrainingDifficulty.BEGINNER : request.difficulty());
        exercise.setRegistrationType(request.registrationType() == null ? defaultRegistrationType(request.module()) : request.registrationType());
        exercise.setUnilateral(Boolean.TRUE.equals(request.unilateral()));
        exercise.setExternalLoad(request.externalLoad() == null ? request.module() == TrainingModule.GYM : request.externalLoad());
        exercise.setActive(request.active() == null || request.active());
        return toExerciseResponse(exercises.save(exercise));
    }

    @Transactional
    public TrainingExerciseResponse updateExercise(AppUser user, Long id, UpsertExerciseRequest request) {
        TrainingExercise exercise = requireExercise(user, id);
        if (exercise.isGlobalExercise()) {
            throw new NotFoundException("El ejercicio global es de solo lectura.");
        }
        String name = normalized(request.name());
        if (exercises.existsLiveName(user, request.module(), name, exercise.getId())) {
            throw new BadRequestException("Ya existe un ejercicio con ese nombre para este módulo.");
        }
        if (exercise.getModule() != request.module() && presetExercises.existsByExerciseAndDeletedAtIsNull(exercise)) {
            throw new BadRequestException("No podés cambiar el módulo de un ejercicio usado en una rutina.");
        }
        exercise.setName(name);
        exercise.setNormalizedName(normalizedKey(name));
        exercise.setModule(request.module());
        exercise.setDescription(blankToNull(request.description()));
        exercise.setCategory(resolveCategory(user, request.module(), request.categoryId(), request.category(), false));
        exercise.setCode(resolveExerciseCode(user, request.module(), name, request.code(), exercise.getId(), false));
        exercise.setPrimaryMuscles(normalizedValues(request.primaryMuscles()));
        exercise.setSecondaryMuscles(normalizedValues(request.secondaryMuscles()));
        if (request.equipment() != null) exercise.setEquipment(request.equipment());
        if (request.difficulty() != null) exercise.setDifficulty(request.difficulty());
        if (request.registrationType() != null) exercise.setRegistrationType(request.registrationType());
        if (request.unilateral() != null) exercise.setUnilateral(request.unilateral());
        if (request.externalLoad() != null) exercise.setExternalLoad(request.externalLoad());
        if (request.active() != null) exercise.setActive(request.active());
        exercise.setUpdatedAt(OffsetDateTime.now());
        return toExerciseResponse(exercise);
    }

    @Transactional
    public void deleteExercise(AppUser user, Long id) {
        TrainingExercise exercise = requireExercise(user, id);
        if (exercise.isGlobalExercise()) {
            throw new NotFoundException("El ejercicio global es de solo lectura.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        exercise.setActive(false);
        exercise.setDeletedAt(now);
        exercise.setUpdatedAt(now);
    }

    @Transactional(readOnly = true)
    public PageResponse<LegacyTrainingPlanResponse> presets(AppUser user, TrainingModule module, boolean includeInactive,
            int page, int size) {
        Page<TrainingPlan> result = presets.search(user, module, includeInactive,
                page(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return page(result, this::toPresetResponse);
    }

    @Transactional(readOnly = true)
    public LegacyTrainingPlanDetailResponse preset(AppUser user, Long id) {
        return toPresetDetailResponse(requirePresetDetail(user, id));
    }

    @Transactional
    public LegacyTrainingPlanDetailResponse createPreset(AppUser user, LegacyPlanRequest request) {
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, request.module(), name, null);
        TrainingPlan preset = new TrainingPlan();
        preset.setOwner(user);
        applyPreset(preset, request, name);
        if (preset.isActive()) closeActivePlans(user, preset.getModule(), null, LocalDate.now());
        presets.save(preset);
        return toPresetDetailResponse(preset);
    }

    @Transactional
    public LegacyTrainingPlanDetailResponse updatePreset(AppUser user, Long id, LegacyPlanRequest request) {
        TrainingPlan preset = requirePresetDetail(user, id);
        checkVersion(request.version(), preset.getVersion(), "La rutina");
        if (preset.getModule() != request.module() && !livePresetExercises(preset).isEmpty()) {
            throw new BadRequestException("No podés cambiar el módulo de una rutina que tiene ejercicios.");
        }
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, request.module(), name, preset.getId());
        applyPreset(preset, request, name);
        if (preset.isActive()) closeActivePlans(user, preset.getModule(), preset.getId(), LocalDate.now());
        return toPresetDetailResponse(preset);
    }

    @Transactional
    public void deletePreset(AppUser user, Long id) {
        TrainingPlan preset = requirePreset(user, id);
        OffsetDateTime now = OffsetDateTime.now();
        preset.setActive(false);
        preset.setDeletedAt(now);
        preset.setUpdatedAt(now);
    }

    @Transactional
    public LegacyTrainingPlanDetailResponse duplicatePreset(AppUser user, Long id, DuplicateTrainingPlanRequest request) {
        TrainingPlan source = requirePresetDetail(user, id);
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, source.getModule(), name, null);

        TrainingPlan copy = new TrainingPlan();
        copy.setOwner(user);
        copy.setName(name);
        copy.setDescription(source.getDescription());
        copy.setModule(source.getModule());
        copy.setFrequencyMode(source.getFrequencyMode());
        copy.setTargetSessionsPerWeek(source.getTargetSessionsPerWeek());
        copy.setStartDate(LocalDate.now());
        copy.setEndDate(null);
        copy.setActive(true);
        closeActivePlans(user, copy.getModule(), null, LocalDate.now());

        int dayPosition = 0;
        for (TrainingPlanDay sourceDay : liveDays(source)) {
            TrainingPlanDay dayCopy = new TrainingPlanDay();
            dayCopy.setPlan(copy);
            dayCopy.setName(sourceDay.getName());
            dayCopy.setDescription(sourceDay.getDescription());
            dayCopy.setDayOfWeek(sourceDay.getDayOfWeek());
            dayCopy.setPosition(dayPosition++);
            dayCopy.setActive(sourceDay.isActive());
            copy.getDays().add(dayCopy);

            int exercisePosition = 0;
            for (TrainingPlanExercise sourceExercise : livePresetExercises(sourceDay)) {
                TrainingPlanExercise exerciseCopy = new TrainingPlanExercise();
                exerciseCopy.setPlanDay(dayCopy);
                exerciseCopy.setExercise(sourceExercise.getExercise());
                exerciseCopy.setTargetSets(sourceExercise.getTargetSets());
                exerciseCopy.setTargetRepetitions(sourceExercise.getTargetRepetitions());
                exerciseCopy.setTargetWeightKg(sourceExercise.getTargetWeightKg());
                exerciseCopy.setRegistrationType(sourceExercise.getRegistrationType());
                exerciseCopy.setTargetSeconds(sourceExercise.getTargetSeconds());
                exerciseCopy.setTargetDistanceMeters(sourceExercise.getTargetDistanceMeters());
                exerciseCopy.setNotes(sourceExercise.getNotes());
                exerciseCopy.setPosition(exercisePosition++);
                exerciseCopy.setActive(sourceExercise.isActive());
                dayCopy.getExercises().add(exerciseCopy);
            }
        }
        presets.save(copy);
        return toPresetDetailResponse(copy);
    }

    @Transactional
    public LegacyPlanDayResponse createDay(AppUser user, Long presetId, LegacyPlanDayRequest request) {
        TrainingPlan preset = requirePresetDetail(user, presetId);
        validateLegacyDayMode(preset, request.dayOfWeek(), null);
        TrainingPlanDay day = new TrainingPlanDay();
        day.setPlan(preset);
        day.setName(request.name() == null || request.name().isBlank() ? "Día " + (day.getPosition() + 1) : normalized(request.name()));
        day.setDescription(blankToNull(request.description()));
        day.setDayOfWeek(request.dayOfWeek());
        day.setActive(request.active() == null || request.active());
        day.setPosition(liveDays(preset).size());
        preset.getDays().add(day);
        touch(preset);
        days.saveAndFlush(day);
        presets.save(preset);
        return toDayResponse(day);
    }

    @Transactional
    public LegacyPlanDayResponse updateDay(AppUser user, Long presetId, Long dayId, LegacyPlanDayRequest request) {
        TrainingPlanDay day = requireDay(user, presetId, dayId);
        validateLegacyDayMode(day.getPlan(), request.dayOfWeek(), day.getId());
        day.setName(request.name() == null || request.name().isBlank() ? day.getName() : normalized(request.name()));
        day.setDescription(blankToNull(request.description()));
        day.setDayOfWeek(request.dayOfWeek());
        if (request.active() != null) day.setActive(request.active());
        day.setUpdatedAt(OffsetDateTime.now());
        touch(day.getPlan());
        return toDayResponse(day);
    }

    @Transactional
    public void deleteDay(AppUser user, Long presetId, Long dayId) {
        TrainingPlanDay day = requireDay(user, presetId, dayId);
        OffsetDateTime now = OffsetDateTime.now();
        day.setActive(false);
        day.setDeletedAt(now);
        day.setUpdatedAt(now);
        touch(day.getPlan());
    }

    @Transactional
    public List<LegacyPlanDayResponse> reorderDays(AppUser user, Long presetId, ReorderRequest request) {
        TrainingPlan preset = requirePresetDetail(user, presetId);
        List<TrainingPlanDay> ordered = ordered(liveDays(preset), request.ids(), "días de la rutina");
        OffsetDateTime now = OffsetDateTime.now();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setPosition(index);
            ordered.get(index).setUpdatedAt(now);
        }
        touch(preset);
        return ordered.stream().map(this::toDayResponse).toList();
    }

    @Transactional
    public LegacyPlanExerciseResponse createPresetExercise(AppUser user, Long presetId, Long dayId,
            LegacyPlanExerciseRequest request) {
        TrainingPlanDay day = requireDay(user, presetId, dayId);
        TrainingExercise exercise = requireSelectableExercise(user, request.exerciseId());
        validateExerciseModule(day.getPlan().getModule(), exercise);
        ensureDayExerciseAvailable(day, exercise, null);

        TrainingPlanExercise presetExercise = new TrainingPlanExercise();
        presetExercise.setPlanDay(day);
        presetExercise.setExercise(exercise);
        presetExercise.setPosition(livePresetExercises(day).size());
        applyPresetExercise(presetExercise, day.getPlan().getModule(), exercise, request);
        day.getExercises().add(presetExercise);
        touch(day.getPlan());
        days.save(day);
        return toPresetExerciseResponse(presetExercise);
    }

    @Transactional
    public LegacyPlanExerciseResponse updatePresetExercise(AppUser user, Long presetId, Long dayId,
            Long presetExerciseId, LegacyPlanExerciseRequest request) {
        TrainingPlanDay day = requireDay(user, presetId, dayId);
        TrainingPlanExercise presetExercise = requirePresetExercise(day, presetExerciseId);
        TrainingExercise exercise = requireSelectableExercise(user, request.exerciseId());
        validateExerciseModule(day.getPlan().getModule(), exercise);
        ensureDayExerciseAvailable(day, exercise, presetExercise.getId());
        presetExercise.setExercise(exercise);
        applyPresetExercise(presetExercise, day.getPlan().getModule(), exercise, request);
        touch(day.getPlan());
        return toPresetExerciseResponse(presetExercise);
    }

    @Transactional
    public void deletePresetExercise(AppUser user, Long presetId, Long dayId, Long presetExerciseId) {
        TrainingPlanDay day = requireDay(user, presetId, dayId);
        TrainingPlanExercise presetExercise = requirePresetExercise(day, presetExerciseId);
        OffsetDateTime now = OffsetDateTime.now();
        presetExercise.setActive(false);
        presetExercise.setDeletedAt(now);
        presetExercise.setUpdatedAt(now);
        touch(day.getPlan());
    }

    @Transactional
    public List<LegacyPlanExerciseResponse> reorderPresetExercises(AppUser user, Long presetId, Long dayId,
            ReorderRequest request) {
        TrainingPlanDay day = requireDay(user, presetId, dayId);
        List<TrainingPlanExercise> ordered = ordered(livePresetExercises(day), request.ids(), "ejercicios del día");
        OffsetDateTime now = OffsetDateTime.now();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setPosition(index);
            ordered.get(index).setUpdatedAt(now);
        }
        touch(day.getPlan());
        return ordered.stream().map(this::toPresetExerciseResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingPlanResponse> plans(AppUser user, TrainingModule module, boolean includeInactive,
            int page, int size) {
        Page<TrainingPlan> result = presets.search(user, module, includeInactive,
                page(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return page(result, this::toPlanResponse);
    }

    @Transactional(readOnly = true)
    public TrainingPlanDetailResponse plan(AppUser user, Long id) {
        return toPlanDetailResponse(requirePresetDetail(user, id));
    }

    @Transactional
    public TrainingPlanDetailResponse createPlan(AppUser user, UpsertTrainingPlanRequest request) {
        validatePlanRequest(user, request);
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, request.module(), name, null);
        TrainingPlan plan = new TrainingPlan();
        plan.setOwner(user);
        applyPlan(plan, request, name);
        if (plan.isActive()) closeActivePlans(user, plan.getModule(), null, plan.getStartDate());
        addPlanStructure(user, plan, request.days());
        return toPlanDetailResponse(presets.saveAndFlush(plan));
    }

    @Transactional
    public TrainingPlanDetailResponse updatePlan(AppUser user, Long id, UpsertTrainingPlanRequest request) {
        TrainingPlan plan = requirePresetDetail(user, id);
        checkVersion(request.version(), plan.getVersion(), "La rutina");
        validatePlanRequest(user, request);
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, request.module(), name, id);
        OffsetDateTime now = OffsetDateTime.now();
        for (TrainingPlanDay oldDay : liveDays(plan)) {
            oldDay.setActive(false);
            oldDay.setDeletedAt(now);
            oldDay.setUpdatedAt(now);
            for (TrainingPlanExercise oldExercise : livePresetExercises(oldDay)) {
                oldExercise.setActive(false);
                oldExercise.setDeletedAt(now);
                oldExercise.setUpdatedAt(now);
            }
        }
        applyPlan(plan, request, name);
        if (plan.isActive()) closeActivePlans(user, plan.getModule(), plan.getId(), plan.getStartDate());
        addPlanStructure(user, plan, request.days());
        presets.saveAndFlush(plan);
        return toPlanDetailResponse(plan);
    }

    @Transactional
    public TrainingPlanDetailResponse duplicatePlan(AppUser user, Long id, DuplicateTrainingPlanRequest request) {
        LegacyTrainingPlanDetailResponse copy = duplicatePreset(user, id, request);
        return plan(user, copy.id());
    }

    @Transactional(readOnly = true)
    public TrainingPlanResolutionResponse resolvePlan(AppUser user, Long id, LocalDate date) {
        TrainingPlan plan = requirePresetDetail(user, id);
        TrainingPlanDay day = resolvePlanDay(user, plan, date);
        TrainingSessionSummaryResponse session = day == null ? null
                : sessions.findBlockingForSchedule(user, date, id, day.getId()).stream().findFirst()
                        .map(this::toSessionSummaryResponse).orElseGet(() -> sessions.search(user, date, date, date,
                                plan.getModule(), null, id, day.getId(), page(0, 1, Sort.unsorted())).stream().findFirst()
                                        .map(this::toSessionSummaryResponse).orElse(null));
        return new TrainingPlanResolutionResponse(date, day != null, plan.getId(), idOf(day),
                day == null ? null : day.getName(), plan.getModule(), plan.getFrequencyMode(), session);
    }

    @Transactional
    public TrainingSessionResponse skipPlanSession(AppUser user, Long planId, SkipTrainingPlanSessionRequest request) {
        TrainingPlan plan = requirePreset(user, planId);
        if (plan.getFrequencyMode() != TrainingFrequencyMode.DYNAMIC) {
            throw new BadRequestException("Solo se pueden omitir sesiones de planes dinámicos.");
        }
        TrainingPlanDay day = resolvePlanDay(user, plan, request.date());
        if (day == null) {
            throw new BadRequestException("La fecha está fuera de la vigencia del plan.");
        }
        if (request.planDayId() != null && !Objects.equals(request.planDayId(), day.getId())) {
            throw new BadRequestException("La sesión no corresponde al siguiente día dinámico.");
        }
        TrainingSession existing = sessions.findBlockingForSchedule(user, request.date(), planId, day.getId())
                .stream().findFirst().orElse(null);
        if (existing != null) {
            if (existing.getStatus() == TrainingSessionStatus.IN_PROGRESS) {
                throw new ConflictException("Ya existe una sesión IN_PROGRESS para este plan y fecha: "
                        + existing.getId() + ".");
            }
            return toSessionResponse(existing);
        }

        TrainingSession skipped = new TrainingSession();
        skipped.setUser(user);
        skipped.setSessionDate(request.date());
        skipped.setModule(plan.getModule());
        skipped.setSourcePlan(plan);
        skipped.setSourcePlanDay(day);
        skipped.setTitle(day.getName());
        skipped.setStatus(TrainingSessionStatus.SKIPPED);
        skipped.setNotes(blankToNull(request.notes()));
        skipped.setStartedAt(null);
        skipped.setFinishedAt(OffsetDateTime.now());
        return toSessionResponse(sessions.saveAndFlush(skipped));
    }

    private void validatePlanRequest(AppUser user, UpsertTrainingPlanRequest request) {
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("La fecha final no puede ser anterior a la inicial.");
        }
        Set<DayOfWeek> fixedDays = EnumSet.noneOf(DayOfWeek.class);
        Set<Integer> positions = new java.util.HashSet<>();
        for (int index = 0; index < request.days().size(); index++) {
            PlanDayRequest day = request.days().get(index);
            if (!positions.add(day.position() == null ? index : day.position())) {
                throw new BadRequestException("Las posiciones de los días deben ser únicas.");
            }
            if (request.frequencyMode() == TrainingFrequencyMode.FIXED) {
                if (day.dayOfWeek() == null || !fixedDays.add(day.dayOfWeek())) {
                    throw new BadRequestException("Un plan fijo requiere días de semana únicos.");
                }
            } else if (day.dayOfWeek() != null) {
                throw new BadRequestException("Un plan dinámico usa posiciones únicas y no admite día de semana.");
            }
            Set<Long> exerciseIds = new java.util.HashSet<>();
            for (PlanExerciseRequest exerciseRequest : day.exercises()) {
                if (!exerciseIds.add(exerciseRequest.exerciseId())) {
                    throw new BadRequestException("No puede haber ejercicios repetidos en un día.");
                }
                TrainingExercise exercise = requireSelectableExercise(user, exerciseRequest.exerciseId());
                validateExerciseModule(request.module(), exercise);
                TrainingRegistrationType type = validateRegistrationType(exercise, exerciseRequest.registrationType());
                validateMetrics(request.module(), type, exerciseRequest.targetRepetitions(),
                        exerciseRequest.targetWeightKg(), exerciseRequest.targetSeconds(),
                        exerciseRequest.targetDistanceMeters());
            }
        }
    }

    private void applyPlan(TrainingPlan plan, UpsertTrainingPlanRequest request, String name) {
        plan.setName(name);
        plan.setDescription(blankToNull(request.description()));
        plan.setModule(request.module());
        plan.setFrequencyMode(request.frequencyMode());
        plan.setTargetSessionsPerWeek(request.targetSessionsPerWeek());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        if (request.active() != null) plan.setActive(request.active());
        else if (plan.getId() == null) plan.setActive(true);
        plan.setUpdatedAt(OffsetDateTime.now());
    }

    private void addPlanStructure(AppUser user, TrainingPlan plan, List<PlanDayRequest> dayRequests) {
        for (int index = 0; index < dayRequests.size(); index++) {
            PlanDayRequest request = dayRequests.get(index);
            TrainingPlanDay day = new TrainingPlanDay();
            day.setPlan(plan);
            day.setName(normalized(request.name()));
            day.setDescription(blankToNull(request.description()));
            day.setDayOfWeek(request.dayOfWeek());
            day.setPosition(request.position() == null ? index : request.position());
            day.setActive(true);
            for (int exerciseIndex = 0; exerciseIndex < request.exercises().size(); exerciseIndex++) {
                PlanExerciseRequest requestExercise = request.exercises().get(exerciseIndex);
                TrainingPlanExercise exercise = new TrainingPlanExercise();
                exercise.setPlanDay(day);
                exercise.setExercise(requireSelectableExercise(user, requestExercise.exerciseId()));
                exercise.setTargetSets(requestExercise.targetSets());
                exercise.setTargetRepetitions(requestExercise.targetRepetitions());
                exercise.setTargetWeightKg(requestExercise.targetWeightKg());
                exercise.setRegistrationType(effectiveRegistrationType(exercise.getExercise(), requestExercise.registrationType()));
                exercise.setTargetSeconds(requestExercise.targetSeconds());
                exercise.setTargetDistanceMeters(requestExercise.targetDistanceMeters());
                exercise.setNotes(blankToNull(requestExercise.notes()));
                exercise.setPosition(requestExercise.position() == null ? exerciseIndex : requestExercise.position());
                exercise.setActive(true);
                day.getExercises().add(exercise);
            }
            plan.getDays().add(day);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingSessionSummaryResponse> sessions(AppUser user, LocalDate from, LocalDate to,
            LocalDate date, TrainingModule module, TrainingSessionStatus status, Long presetId, Long trainingDayId,
            int page, int size) {
        validateDateRange(from, to);
        if (presetId != null) requirePresetReference(user, presetId);
        if (trainingDayId != null) requireDayReference(user, trainingDayId);
        Page<TrainingSession> result = sessions.search(user, from, to, date, module, status, presetId, trainingDayId,
                page(page, size, Sort.by(Sort.Order.desc("sessionDate"), Sort.Order.desc("id"))));
        return page(result, this::toSessionSummaryResponse);
    }

    @Transactional(readOnly = true)
    public TrainingSessionResponse session(AppUser user, Long id) {
        return toSessionResponse(requireSession(user, id));
    }

    @Transactional
    public TrainingSessionResponse createSession(AppUser user, CreateTrainingSessionRequest request) {
        if (request.status() != null && request.status() != TrainingSessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Una sesión nueva siempre comienza en IN_PROGRESS.");
        }
        SessionSource source = resolveSource(user, request.module(), request.planId(), request.planDayId());
        validateScheduledSession(user, source, request.date());
        if (source.plan() != null) {
            sessions.findBlockingForSchedule(user, request.date(), source.plan().getId(), source.planDay().getId())
                    .stream().findFirst().ifPresent(existing -> {
                        throw new ConflictException("Ya existe una sesión de este plan para la fecha indicada. "
                                + "Continuá la sesión " + existing.getId() + ".");
                    });
        }
        TrainingSession session = new TrainingSession();
        session.setUser(user);
        OffsetDateTime startedAt = request.startedAt() == null ? OffsetDateTime.now() : request.startedAt();
        applySession(session, request.date(), request.module(), source, request.title(),
                TrainingSessionStatus.IN_PROGRESS, startedAt, null, request.durationMinutes(), request.notes());
        session.setBaselineCaptured(source.planDay() != null);
        if (source.planDay() != null) {
            session.setBaselinePlanVersion(versionOrZero(source.plan()));
            session.setBaselinePlanDayVersion(versionOrZero(source.planDay()));
        }

        if (source.planDay() != null) {
            for (TrainingPlanExercise presetExercise : livePresetExercises(source.planDay())) {
                if (!presetExercise.isActive()) continue;
                addSessionExerciseSnapshot(session, presetExercise, session.getExercises().size());
            }
        } else if (request.exercises() != null) {
            for (TrainingSessionExerciseRequest exerciseRequest : request.exercises()) {
                addSessionExercise(session, user, exerciseRequest, session.getExercises().size());
            }
        }
        sessions.saveAndFlush(session);
        saveBaseline(session, source);
        return toSessionResponse(session);
    }

    @Transactional
    public TrainingSessionResponse updateSession(AppUser user, Long id, UpdateTrainingSessionRequest request) {
        TrainingSession session = requireSession(user, id);
        checkVersion(request.version(), session.getVersion(), "La sesión");
        if (session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Solo se pueden modificar sesiones IN_PROGRESS.");
        }
        if (request.status() != TrainingSessionStatus.IN_PROGRESS) {
            throw new BadRequestException("PUT solo puede mantener una sesión IN_PROGRESS. Usá complete o cancel.");
        }
        SessionSource source = resolveSource(user, request.module(), request.planId(), request.planDayId());
        validateScheduledSession(user, source, request.date());
        if (!Objects.equals(idOf(session.getSourcePlan()), idOf(source.plan()))
                || !Objects.equals(idOf(session.getSourcePlanDay()), idOf(source.planDay()))) {
            throw new BadRequestException("No podés cambiar el plan de origen de una sesión ya iniciada.");
        }
        if (source.plan() != null) {
            sessions.findBlockingForSchedule(user, request.date(), source.plan().getId(), source.planDay().getId())
                    .stream().filter(existing -> !Objects.equals(existing.getId(), session.getId())).findFirst()
                    .ifPresent(existing -> {
                        throw new ConflictException("Ya existe otra sesión para este plan y fecha: " + existing.getId() + ".");
                    });
        }
        applySession(session, request.date(), request.module(), source, request.title(), request.status(), request.startedAt(),
                request.finishedAt(), request.durationMinutes(), request.notes());
        replaceSessionExercises(session, user, request.exercises());
        sessions.saveAndFlush(session);
        return toSessionResponse(session);
    }

    @Transactional
    public TrainingSessionResponse completeSession(AppUser user, Long id, CompleteTrainingSessionRequest request) {
        TrainingSession session = requireSession(user, id);
        checkVersion(request.version(), session.getVersion(), "La sesión");
        if (session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Solo se puede completar una sesión IN_PROGRESS.");
        }
        if (Boolean.TRUE.equals(request.persistPlanChanges())) {
            persistPlanChanges(session, user);
        }
        OffsetDateTime finishedAt = request.finishedAt() != null ? request.finishedAt() : OffsetDateTime.now();
        validateTimes(session.getStartedAt(), finishedAt);
        session.setStatus(TrainingSessionStatus.COMPLETED);
        session.setFinishedAt(finishedAt);
        session.setDurationMinutes(resolveDuration(session.getStartedAt(), finishedAt,
                request.durationMinutes(), session.getDurationMinutes()));
        session.setUpdatedAt(OffsetDateTime.now());
        sessions.saveAndFlush(session);
        return toSessionResponse(session);
    }

    @Transactional
    public TrainingSessionResponse cancelSession(AppUser user, Long id, CancelTrainingSessionRequest request) {
        TrainingSession session = requireSession(user, id);
        checkVersion(request.version(), session.getVersion(), "La sesión");
        if (session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new BadRequestException("Solo se puede cancelar una sesión IN_PROGRESS.");
        }
        session.setStatus(TrainingSessionStatus.CANCELLED);
        session.setFinishedAt(OffsetDateTime.now());
        session.setNotes(request.notes() == null ? session.getNotes() : blankToNull(request.notes()));
        session.setUpdatedAt(OffsetDateTime.now());
        sessions.saveAndFlush(session);
        return toSessionResponse(session);
    }

    @Transactional
    public void deleteSession(AppUser user, Long id) {
        TrainingSession session = requireSession(user, id);
        if (session.getStatus() == TrainingSessionStatus.COMPLETED) {
            throw new BadRequestException("Las sesiones completadas son históricas y no se pueden borrar.");
        }
        sessions.delete(session);
    }

    @Transactional(readOnly = true)
    public List<TrainingCalendarDayResponse> calendar(AppUser user, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Map<LocalDate, List<TrainingSession>> byDate = sessions.findForCalendar(user, from, to).stream()
                .collect(Collectors.groupingBy(TrainingSession::getSessionDate, LinkedHashMap::new, Collectors.toList()));
        List<TrainingCalendarDayResponse> calendar = new java.util.ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            calendar.add(toCalendarDay(user, date, byDate.getOrDefault(date, List.of())));
        }
        return calendar;
    }

    @Transactional(readOnly = true)
    public TrainingDashboardResponse dashboard(AppUser user, LocalDate date) {
        date = date == null ? LocalDate.now() : date;
        List<TrainingPlanResponse> routines = presets.search(user, null, true, page(0, 5, Sort.unsorted()))
                .stream()
                .limit(4)
                .map(this::toPlanResponse)
                .toList();

        TrainingSessionSummaryResponse recentSession = sessions.search(user, null, null, null, null,
                TrainingSessionStatus.COMPLETED, null, null,
                page(0, 1, Sort.by(Sort.Order.desc("sessionDate"), Sort.Order.desc("id"))))
                .stream()
                .findFirst()
                .map(this::toSessionSummaryResponse)
                .orElse(null);

        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<TrainingSession> weekSessions = sessions.search(user, weekStart, weekEnd, null, null, null, null, null,
                page(0, 50, Sort.by(Sort.Order.desc("sessionDate"), Sort.Order.desc("id")))).getContent();
        long sessionCount = weekSessions.stream()
                .filter(s -> s.getStatus() == TrainingSessionStatus.COMPLETED).count();
        long totalMinutes = weekSessions.stream()
                .filter(s -> s.getStatus() == TrainingSessionStatus.COMPLETED && s.getDurationMinutes() != null)
                .mapToLong(s -> s.getDurationMinutes())
                .sum();
        long totalSets = weekSessions.stream()
                .filter(s -> s.getStatus() == TrainingSessionStatus.COMPLETED)
                .flatMap(s -> s.getExercises().stream())
                .flatMap(e -> e.getSets().stream())
                .count();

        List<TrainingExerciseResponse> exercises = exercises(user, "", null, 0, 50)
                .items()
                .stream()
                .limit(50)
                .toList();

        WeeklyTrainingSummaryResponse weeklySummary = new WeeklyTrainingSummaryResponse(
                sessionCount, totalMinutes, totalSets);

        return new TrainingDashboardResponse(date, routines, recentSession, weeklySummary,
                exercises, schedulesForDate(user, date));
    }

    private TrainingExercise requireExercise(AppUser user, Long id) {
        return exercises.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Ejercicio no encontrado."));
    }

    private TrainingExercise requireSelectableExercise(AppUser user, Long id) {
        TrainingExercise exercise = exercises.findSelectable(id, user)
                .orElseThrow(() -> new NotFoundException("Ejercicio no encontrado."));
        if (!exercise.isActive()) {
            throw new BadRequestException("El ejercicio está archivado.");
        }
        return exercise;
    }

    private TrainingPlan requirePreset(AppUser user, Long id) {
        return presets.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Rutina no encontrada."));
    }

    private TrainingPlan requirePresetDetail(AppUser user, Long id) {
        return presets.findDetailByIdAndOwner(id, user)
                .orElseThrow(() -> new NotFoundException("Rutina no encontrada."));
    }

    private TrainingPlan requirePresetReference(AppUser user, Long id) {
        return presets.findByIdAndOwner(id, user)
                .orElseThrow(() -> new NotFoundException("Rutina no encontrada."));
    }

    private TrainingPlanDay requireDay(AppUser user, Long presetId, Long dayId) {
        TrainingPlanDay day = requireDayForUser(user, dayId);
        if (!Objects.equals(day.getPlan().getId(), presetId)) {
            throw new NotFoundException("Día de rutina no encontrado.");
        }
        return day;
    }

    private TrainingPlanDay requireDayForUser(AppUser user, Long dayId) {
        return days.findDetailByIdAndOwner(dayId, user)
                .orElseThrow(() -> new NotFoundException("Día de rutina no encontrado."));
    }

    private TrainingPlanDay requireDayReference(AppUser user, Long dayId) {
        return days.findByIdAndOwner(dayId, user)
                .orElseThrow(() -> new NotFoundException("Día de rutina no encontrado."));
    }

    private TrainingPlanExercise requirePresetExercise(TrainingPlanDay day, Long id) {
        return day.getExercises().stream()
                .filter(item -> item.getDeletedAt() == null && Objects.equals(item.getId(), id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Ejercicio de rutina no encontrado."));
    }

    private TrainingSession requireSession(AppUser user, Long id) {
        TrainingSession session = sessions.findDetailByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Sesión de entrenamiento no encontrada."));
        sessionExercises.findAllWithSetsBySessionId(session.getId());
        return session;
    }

    private void ensurePresetNameAvailable(AppUser user, TrainingModule module, String name, Long excludedId) {
        if (presets.existsLiveName(user, module, name, excludedId)) {
            throw new BadRequestException("Ya existe una rutina con ese nombre para este módulo.");
        }
    }

    private void applyPreset(TrainingPlan preset, LegacyPlanRequest request, String name) {
        preset.setName(name);
        preset.setDescription(blankToNull(request.description()));
        preset.setModule(request.module());
        if (preset.getTargetSessionsPerWeek() <= 0) {
            preset.setTargetSessionsPerWeek(1);
        }
        if (request.active() != null) preset.setActive(request.active());
        else if (preset.getId() == null) preset.setActive(true);
        preset.setUpdatedAt(OffsetDateTime.now());
    }

    private void closeActivePlans(AppUser user, TrainingModule module, Long excludedId, LocalDate newStartDate) {
        LocalDate closeDate = newStartDate == null ? LocalDate.now() : newStartDate.minusDays(1);
        for (TrainingPlan previous : presets.findByOwnerAndModuleAndActiveTrueAndDeletedAtIsNull(user, module)) {
            if (Objects.equals(previous.getId(), excludedId)) continue;
            previous.setActive(false);
            if (previous.getStartDate() == null || !closeDate.isBefore(previous.getStartDate())) {
                previous.setEndDate(closeDate);
            }
            previous.setUpdatedAt(OffsetDateTime.now());
        }
    }

    private void applyPresetExercise(TrainingPlanExercise presetExercise, TrainingModule module,
            TrainingExercise exercise, LegacyPlanExerciseRequest request) {
        TrainingRegistrationType type = validateRegistrationType(exercise, request.registrationType());
        validateMetrics(module, type, request.targetRepetitions(), request.targetWeightKg(), request.targetSeconds(),
                request.targetDistanceMeters());
        presetExercise.setTargetSets(request.targetSets());
        presetExercise.setTargetRepetitions(request.targetRepetitions());
        presetExercise.setTargetWeightKg(request.targetWeightKg());
        presetExercise.setRegistrationType(type);
        presetExercise.setTargetSeconds(request.targetSeconds());
        presetExercise.setTargetDistanceMeters(request.targetDistanceMeters());
        presetExercise.setNotes(blankToNull(request.notes()));
        if (request.active() != null) presetExercise.setActive(request.active());
        else if (presetExercise.getId() == null) presetExercise.setActive(true);
        presetExercise.setUpdatedAt(OffsetDateTime.now());
    }

    private void ensureDayExerciseAvailable(TrainingPlanDay day, TrainingExercise exercise, Long excludedId) {
        boolean duplicate = day.getExercises().stream()
                .filter(item -> item.getDeletedAt() == null)
                .anyMatch(item -> Objects.equals(item.getExercise().getId(), exercise.getId())
                        && !Objects.equals(item.getId(), excludedId));
        if (duplicate) {
            throw new BadRequestException("El ejercicio ya pertenece a este día de rutina.");
        }
    }

    private SessionSource resolveSource(AppUser user, TrainingModule module, Long presetId, Long trainingDayId) {
        if (trainingDayId == null && presetId == null) return new SessionSource(null, null);

        if (trainingDayId != null) {
            TrainingPlanDay day = requireDayForUser(user, trainingDayId);
            TrainingPlan preset = day.getPlan();
            if (presetId != null && !Objects.equals(preset.getId(), presetId)) {
                throw new BadRequestException("El día no pertenece a la rutina indicada.");
            }
            validateSource(module, preset, day);
            return new SessionSource(preset, day);
        }

        TrainingPlan preset = requirePreset(user, presetId);
        if (!preset.isActive()) {
            throw new BadRequestException("La rutina está archivada.");
        }
        if (preset.getModule() != module) {
            throw new BadRequestException("La rutina pertenece a otro módulo de entrenamiento.");
        }
        return new SessionSource(preset, null);
    }

    private void validateSource(TrainingModule module, TrainingPlan preset, TrainingPlanDay day) {
        if (!preset.isActive() || !day.isActive()) {
            throw new BadRequestException("La rutina o el día de origen está archivado.");
        }
        if (preset.getModule() != module) {
            throw new BadRequestException("El día de rutina pertenece a otro módulo de entrenamiento.");
        }
    }

    private void validateLegacyDayMode(TrainingPlan plan, DayOfWeek dayOfWeek, Long excludedId) {
        if (plan.getFrequencyMode() == TrainingFrequencyMode.DYNAMIC && dayOfWeek != null) {
            throw new BadRequestException("Un plan dinámico no admite día de semana.");
        }
        if (plan.getFrequencyMode() == TrainingFrequencyMode.FIXED && dayOfWeek == null) {
            throw new BadRequestException("Un plan fijo requiere día de semana.");
        }
        if (dayOfWeek != null && plan.getDays().stream().anyMatch(day -> day.getDeletedAt() == null
                && day.getDayOfWeek() == dayOfWeek && !Objects.equals(day.getId(), excludedId))) {
            throw new BadRequestException("El día de semana ya está asignado a este plan.");
        }
    }

    private void validateScheduledSession(AppUser user, SessionSource source, LocalDate date) {
        if (source.plan() == null) return;
        if (source.planDay() == null) {
            throw new BadRequestException("Una sesión asociada a un plan requiere planDayId.");
        }
        TrainingPlan plan = source.plan();
        if ((plan.getStartDate() != null && date.isBefore(plan.getStartDate()))
                || (plan.getEndDate() != null && date.isAfter(plan.getEndDate()))) {
            throw new BadRequestException("La fecha está fuera de la vigencia del plan.");
        }
        TrainingPlanDay expected = resolvePlanDay(user, plan, date);
        if (expected == null || !Objects.equals(expected.getId(), source.planDay().getId())) {
            throw new BadRequestException(plan.getFrequencyMode() == TrainingFrequencyMode.FIXED
                    ? "La sesión solo puede registrarse en el día fijo asignado."
                    : "La sesión no corresponde al siguiente día del plan dinámico.");
        }
    }

    private TrainingPlanDay resolvePlanDay(AppUser user, TrainingPlan plan, LocalDate date) {
        if (!plan.isActive() || (plan.getStartDate() != null && date.isBefore(plan.getStartDate()))
                || (plan.getEndDate() != null && date.isAfter(plan.getEndDate()))) return null;
        List<TrainingPlanDay> live = liveDays(plan);
        if (live.isEmpty()) return null;
        if (plan.getFrequencyMode() == TrainingFrequencyMode.FIXED) {
            return live.stream().filter(day -> day.getDayOfWeek() == date.getDayOfWeek()).findFirst().orElse(null);
        }
        long completedOrSkipped = sessions.countAdvancingSessions(user, plan.getId(), date);
        return live.get((int) (completedOrSkipped % live.size()));
    }

    private List<TrainingPlanScheduleResponse> schedulesForDate(AppUser user, LocalDate date) {
        Map<String, TrainingSession> blockingSessions = sessions.findBlockingForDate(user, date).stream()
                .collect(Collectors.toMap(session -> scheduleKey(session.getSourcePlan(), session.getSourcePlanDay()),
                        session -> session, (first, ignored) -> first));
        return presets.search(user, null, false, page(0, 50, Sort.unsorted())).stream()
                .map(plan -> {
                    if (plan.getFrequencyMode() == TrainingFrequencyMode.DYNAMIC && !date.equals(LocalDate.now())) {
                        return null;
                    }
                    TrainingPlanDay day = resolvePlanDay(user, plan, date);
                    if (day == null) return null;
                    TrainingSession blocking = blockingSessions.get(scheduleKey(plan, day));
                    if (blocking != null && blocking.getStatus() != TrainingSessionStatus.IN_PROGRESS) return null;
                    return new TrainingPlanScheduleResponse(plan.getId(), day.getId(), day.getName(), plan.getModule(),
                            blocking == null, blocking == null ? null : blocking.getId(),
                            blocking == null ? null : blocking.getStatus());
                })
                .filter(Objects::nonNull).toList();
    }

    private void applySession(TrainingSession session, LocalDate date, TrainingModule module, SessionSource source,
            String title, TrainingSessionStatus status, OffsetDateTime startedAt, OffsetDateTime finishedAt,
            Integer durationMinutes, String notes) {
        if (status == TrainingSessionStatus.IN_PROGRESS && finishedAt != null) {
            throw new BadRequestException("Una sesión IN_PROGRESS no puede tener fecha de finalización.");
        }
        validateTimes(startedAt, finishedAt);
        session.setSessionDate(date);
        session.setModule(module);
        session.setSourcePlan(source.plan());
        session.setSourcePlanDay(source.planDay());
        session.setTitle(blankToNull(title));
        session.setStatus(status);
        session.setStartedAt(startedAt);
        session.setFinishedAt(finishedAt);
        session.setDurationMinutes(resolveDuration(startedAt, finishedAt, durationMinutes, null));
        session.setNotes(blankToNull(notes));
        session.setUpdatedAt(OffsetDateTime.now());
    }

    private void addSessionExerciseSnapshot(TrainingSession session, TrainingPlanExercise source, int position) {
        TrainingSessionExercise snapshot = new TrainingSessionExercise();
        snapshot.setSession(session);
        snapshot.setSourceExercise(source.getExercise());
        snapshot.setSourcePlanExercise(source);
        snapshot.setExerciseName(source.getExercise().getName());
        // The session starts from structure only. Plan targets are not performed metrics.
        snapshot.setOrigin(TrainingSessionExerciseOrigin.PLAN);
        snapshot.setRegistrationType(effectiveRegistrationType(source.getExercise(), source.getRegistrationType()));
        snapshot.setPosition(position);
        session.getExercises().add(snapshot);
    }

    private void addSessionExercise(TrainingSession session, AppUser user, TrainingSessionExerciseRequest request,
            int position) {
        TrainingExercise exercise = requireSelectableExercise(user, request.exerciseId());
        validateExerciseModule(session.getModule(), exercise);
        TrainingRegistrationType type = validateRegistrationType(exercise, request.registrationType());
        validateMetrics(session.getModule(), type, request.targetRepetitions(), request.targetWeightKg(),
                request.targetSeconds(), request.targetDistanceMeters());
        validateSets(session.getModule(), type, request.sets());

        TrainingSessionExercise sessionExercise = new TrainingSessionExercise();
        sessionExercise.setSession(session);
        sessionExercise.setSourceExercise(exercise);
        sessionExercise.setOrigin(TrainingSessionExerciseOrigin.ADDED);
        sessionExercise.setExerciseName(exercise.getName());
        sessionExercise.setTargetSets(request.targetSets());
        sessionExercise.setTargetRepetitions(request.targetRepetitions());
        sessionExercise.setTargetWeightKg(request.targetWeightKg());
        sessionExercise.setRegistrationType(type);
        sessionExercise.setTargetSeconds(request.targetSeconds());
        sessionExercise.setTargetDistanceMeters(request.targetDistanceMeters());
        sessionExercise.setNotes(blankToNull(request.notes()));
        sessionExercise.setPosition(position);
        if (request.sets() != null) {
            for (TrainingSetRequest setRequest : request.sets()) {
                TrainingSet trainingSet = new TrainingSet();
                trainingSet.setSessionExercise(sessionExercise);
                trainingSet.setSetNumber(setRequest.setNumber());
                trainingSet.setRepetitions(setRequest.repetitions());
                trainingSet.setWeightKg(setRequest.weightKg());
                trainingSet.setSeconds(setRequest.seconds());
                trainingSet.setDistanceMeters(setRequest.distanceMeters());
                trainingSet.setCompleted(setRequest.completed());
                trainingSet.setNotes(blankToNull(setRequest.notes()));
                sessionExercise.getSets().add(trainingSet);
            }
        }
        session.getExercises().add(sessionExercise);
    }

    private void saveBaseline(TrainingSession session, SessionSource source) {
        if (source.planDay() == null) return;
        for (TrainingPlanExercise planExercise : livePresetExercises(source.planDay())) {
            TrainingSessionBaseline baseline = new TrainingSessionBaseline();
            baseline.setSession(session);
            baseline.setPlanExercise(planExercise);
            baseline.setCatalogExercise(planExercise.getExercise());
            baseline.setExerciseName(planExercise.getExercise().getName());
            baseline.setPosition(planExercise.getPosition());
            baseline.setPlanVersion(versionOrZero(source.plan()));
            baseline.setPlanDayVersion(versionOrZero(source.planDay()));
            session.getBaseline().add(baseline);
        }
        baselines.saveAll(session.getBaseline());
    }

    private void replaceSessionExercises(TrainingSession session, AppUser user,
            List<TrainingSessionExerciseRequest> requests) {
        List<TrainingSessionExerciseRequest> desiredRequests = requests == null ? List.of() : requests;
        Map<Long, TrainingSessionExercise> existingById = session.getExercises().stream()
                .filter(exercise -> exercise.getId() != null)
                .collect(Collectors.toMap(TrainingSessionExercise::getId, exercise -> exercise));
        Set<TrainingSessionExercise> retained = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<Long> exerciseIds = new java.util.HashSet<>();
        Set<Integer> positions = new java.util.HashSet<>();

        for (int index = 0; index < desiredRequests.size(); index++) {
            TrainingSessionExerciseRequest request = desiredRequests.get(index);
            if (!exerciseIds.add(request.exerciseId())) {
                throw new BadRequestException("No puede haber ejercicios repetidos en una sesión.");
            }
            int position = request.position() == null ? index : request.position();
            if (!positions.add(position)) {
                throw new BadRequestException("Las posiciones de los ejercicios deben ser únicas.");
            }

            TrainingSessionExercise existing = request.id() == null ? session.getExercises().stream()
                    .filter(candidate -> !retained.contains(candidate)
                            && candidate.getSourceExercise() != null
                            && Objects.equals(candidate.getSourceExercise().getId(), request.exerciseId()))
                    .findFirst().orElse(null) : existingById.get(request.id());
            if (request.id() != null && existing == null) {
                throw new NotFoundException("El ejercicio de sesión no pertenece a esta sesión.");
            }
            if (existing == null) {
                addSessionExercise(session, user, request, position);
                existing = session.getExercises().get(session.getExercises().size() - 1);
            } else {
                if (existing.getOrigin() == TrainingSessionExerciseOrigin.PLAN
                        && (existing.getSourceExercise() == null
                                || !Objects.equals(existing.getSourceExercise().getId(), request.exerciseId()))) {
                    throw new BadRequestException("No podés cambiar el ejercicio original de un snapshot de plan.");
                }
                applyExistingSessionExercise(existing, session, user, request);
                existing.setPosition(position);
            }
            retained.add(existing);
        }

        session.getExercises().removeIf(existing -> !retained.contains(existing));
        session.getExercises().sort(Comparator.comparingInt(TrainingSessionExercise::getPosition)
                .thenComparing(exercise -> exercise.getId() == null ? Long.MAX_VALUE : exercise.getId()));
    }

    private void applyExistingSessionExercise(TrainingSessionExercise sessionExercise, TrainingSession session,
            AppUser user, TrainingSessionExerciseRequest request) {
        TrainingExercise exercise = sessionExercise.getSourceExercise();
        if (sessionExercise.getOrigin() != TrainingSessionExerciseOrigin.PLAN) {
            exercise = requireSelectableExercise(user, request.exerciseId());
            validateExerciseModule(session.getModule(), exercise);
            sessionExercise.setSourceExercise(exercise);
            sessionExercise.setExerciseName(exercise.getName());
        }
        TrainingRegistrationType type = validateRegistrationType(exercise, request.registrationType());
        validateMetrics(session.getModule(), type, request.targetRepetitions(), request.targetWeightKg(),
                request.targetSeconds(), request.targetDistanceMeters());
        validateSets(session.getModule(), type, request.sets());
        sessionExercise.setTargetSets(request.targetSets());
        sessionExercise.setTargetRepetitions(request.targetRepetitions());
        sessionExercise.setTargetWeightKg(request.targetWeightKg());
        sessionExercise.setRegistrationType(type);
        sessionExercise.setTargetSeconds(request.targetSeconds());
        sessionExercise.setTargetDistanceMeters(request.targetDistanceMeters());
        sessionExercise.setNotes(blankToNull(request.notes()));
        sessionExercise.setUpdatedAt(OffsetDateTime.now());
        sessionExercise.getSets().clear();
        if (request.sets() != null) {
            for (TrainingSetRequest setRequest : request.sets()) {
                TrainingSet trainingSet = new TrainingSet();
                trainingSet.setSessionExercise(sessionExercise);
                trainingSet.setSetNumber(setRequest.setNumber());
                trainingSet.setRepetitions(setRequest.repetitions());
                trainingSet.setWeightKg(setRequest.weightKg());
                trainingSet.setSeconds(setRequest.seconds());
                trainingSet.setDistanceMeters(setRequest.distanceMeters());
                trainingSet.setCompleted(setRequest.completed());
                trainingSet.setNotes(blankToNull(setRequest.notes()));
                sessionExercise.getSets().add(trainingSet);
            }
        }
    }

    private void persistPlanChanges(TrainingSession session, AppUser user) {
        TrainingPlan plan = session.getSourcePlan();
        TrainingPlanDay day = session.getSourcePlanDay();
        if (plan == null || day == null) {
            throw new ConflictException("La sesión no tiene un plan y día de origen para guardar cambios.");
        }
        if (!plan.isActive() || !day.isActive() || plan.getDeletedAt() != null || day.getDeletedAt() != null) {
            throw new ConflictException("La rutina o el día de origen ya no está activo.");
        }
        List<TrainingSessionBaseline> baseline = baselines.findBySessionIdOrderByPositionAscIdAsc(session.getId());
        if (!session.isBaselineCaptured()) {
            throw new ConflictException("La sesión no tiene baseline de estructura para guardar cambios.");
        }
        Long planVersion = session.getBaselinePlanVersion();
        Long dayVersion = session.getBaselinePlanDayVersion();
        if (!baseline.isEmpty()) {
            planVersion = baseline.get(0).getPlanVersion();
            dayVersion = baseline.get(0).getPlanDayVersion();
        }
        if (!Objects.equals(planVersion, versionOrZero(plan)) || !Objects.equals(dayVersion, versionOrZero(day))) {
            throw new ConflictException("La rutina o el día cambió desde que se inició la sesión. Recargá y reintentá.");
        }

        Map<Long, TrainingPlanExercise> currentById = livePresetExercises(day).stream()
                .collect(Collectors.toMap(TrainingPlanExercise::getId, exercise -> exercise));
        if (baseline.stream().anyMatch(item -> item.getPlanExercise() == null || item.getCatalogExercise() == null)) {
            throw new ConflictException("El baseline de la sesión ya no referencia ejercicios válidos.");
        }
        Set<Long> baselineIds = baseline.stream().map(item -> item.getPlanExercise().getId()).collect(Collectors.toSet());
        Set<Long> selectedPlanIds = new java.util.HashSet<>();
        Set<Long> selectedExerciseIds = new java.util.HashSet<>();
        int position = 0;

        List<TrainingSessionExercise> orderedExercises = session.getExercises().stream()
                .sorted(Comparator.comparingInt(TrainingSessionExercise::getPosition)
                        .thenComparing(exercise -> exercise.getId() == null ? Long.MAX_VALUE : exercise.getId()))
                .toList();
        for (TrainingSessionExercise sessionExercise : orderedExercises) {
            if (sessionExercise.getSourceExercise() == null) {
                throw new BadRequestException("Cada ejercicio de sesión debe pertenecer al catálogo.");
            }
            Long catalogId = sessionExercise.getSourceExercise().getId();
            if (!selectedExerciseIds.add(catalogId)) {
                throw new BadRequestException("No puede haber ejercicios repetidos al guardar cambios en la rutina.");
            }
            if (sessionExercise.getOrigin() == TrainingSessionExerciseOrigin.PLAN) {
                TrainingPlanExercise planExercise = sessionExercise.getSourcePlanExercise();
                if (planExercise == null || !baselineIds.contains(planExercise.getId())) {
                    throw new ConflictException("El origen de un ejercicio de plan ya no coincide con el baseline.");
                }
                TrainingPlanExercise current = currentById.get(planExercise.getId());
                if (current == null || !Objects.equals(current.getExercise().getId(), catalogId)) {
                    throw new ConflictException("Un ejercicio del plan cambió desde que se inició la sesión.");
                }
                if (!selectedPlanIds.add(planExercise.getId())) {
                    throw new BadRequestException("No puede haber ejercicios de plan repetidos en una sesión.");
                }
                current.setPosition(position++);
                current.setUpdatedAt(OffsetDateTime.now());
            } else {
                TrainingExercise exercise = requireSelectableExercise(user, catalogId);
                validateExerciseModule(plan.getModule(), exercise);
                TrainingPlanExercise added = new TrainingPlanExercise();
                added.setPlanDay(day);
                added.setExercise(exercise);
                added.setTargetSets(sessionExercise.getTargetSets());
                added.setTargetRepetitions(sessionExercise.getTargetRepetitions());
                added.setTargetWeightKg(sessionExercise.getTargetWeightKg());
                added.setRegistrationType(validateRegistrationType(exercise, sessionExercise.getRegistrationType()));
                added.setTargetSeconds(sessionExercise.getTargetSeconds());
                added.setTargetDistanceMeters(sessionExercise.getTargetDistanceMeters());
                added.setNotes(sessionExercise.getNotes());
                added.setPosition(position++);
                added.setActive(true);
                day.getExercises().add(added);
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (TrainingPlanExercise existing : livePresetExercises(day)) {
            if (baselineIds.contains(existing.getId()) && !selectedPlanIds.contains(existing.getId())) {
                existing.setActive(false);
                existing.setDeletedAt(now);
                existing.setUpdatedAt(now);
            }
        }
        touch(day);
        touch(plan);
        days.saveAndFlush(day);
    }

    private void validateExerciseModule(TrainingModule module, TrainingExercise exercise) {
        if (exercise.getModule() != module) {
            throw new BadRequestException("El ejercicio pertenece a otro módulo de entrenamiento.");
        }
    }

    private TrainingCategory resolveCategory(AppUser user, TrainingModule module, Long categoryId, String legacyName,
            boolean global) {
        if (categoryId != null) {
            TrainingCategory category = categories.findSelectable(categoryId, user)
                    .orElseThrow(() -> new BadRequestException("La categoría no existe o no está disponible."));
            if (category.getModule() != module) {
                throw new BadRequestException("La categoría pertenece a otro módulo de entrenamiento.");
            }
            if (global && !category.isSystemCategory()) {
                throw new BadRequestException("Un ejercicio global debe usar una categoría base.");
            }
            if (!category.isActive()) {
                throw new BadRequestException("La categoría está archivada.");
            }
            return category;
        }
        if (legacyName != null && !legacyName.isBlank()) {
            String key = normalizedKey(legacyName);
            var category = categories.findSystem(module, key);
            if (!global) {
                category = category.or(() -> categories.findByOwnerAndModuleAndNormalizedNameAndDeletedAtIsNull(user,
                        module, key));
            }
            return category.filter(TrainingCategory::isActive)
                    .orElseThrow(() -> new BadRequestException("La categoría indicada no existe para este módulo."));
        }
        return categories.findSystem(module, normalizedKey("ACONDICIONAMIENTO"))
                .orElseThrow(() -> new BadRequestException("No hay una categoría predeterminada disponible."));
    }

    private String resolveExerciseCode(AppUser user, TrainingModule module, String name, String requested, Long excludedId,
            boolean global) {
        String code = blankToNull(requested);
        if (code == null) {
            String slug = normalizedKey(name).replaceAll("[^a-z0-9]+", "-");
            slug = slug.replaceAll("^-|-$", "");
            if (slug.length() > 42) slug = slug.substring(0, 42);
            String scope = global ? "global" : user.getId() == null ? "new" : user.getId().toString();
            code = "SG-" + module.name() + "-" + slug + "-" + Integer.toUnsignedString(
                    (module.name() + ":" + normalizedKey(name) + ":" + scope).hashCode(), 36);
        } else {
            code = code.toUpperCase(Locale.ROOT);
        }
        if (exercises.existsLiveCode(code, excludedId)) {
            throw new BadRequestException("Ya existe un ejercicio con ese código para este módulo.");
        }
        return code;
    }

    private TrainingRegistrationType defaultRegistrationType(TrainingModule module) {
        return module == TrainingModule.GYM ? TrainingRegistrationType.WEIGHT_AND_REPETITIONS
                : TrainingRegistrationType.REPETITIONS;
    }

    private TrainingEquipment defaultEquipment(TrainingModule module) {
        return module == TrainingModule.GYM ? TrainingEquipment.NONE : TrainingEquipment.BODYWEIGHT;
    }

    private TrainingRegistrationType effectiveRegistrationType(TrainingExercise exercise,
            TrainingRegistrationType requested) {
        return validateRegistrationType(exercise, requested);
    }

    private TrainingRegistrationType validateRegistrationType(TrainingExercise exercise,
            TrainingRegistrationType requested) {
        TrainingRegistrationType actual = exercise.getRegistrationType() == null ? defaultRegistrationType(exercise.getModule())
                : exercise.getRegistrationType();
        if (requested != null && requested != actual) {
            throw new BadRequestException("El tipo de registro no coincide con el tipo del ejercicio.");
        }
        return actual;
    }

    private void validateMetrics(TrainingModule module, TrainingRegistrationType type, Integer repetitions,
            BigDecimal weightKg, Integer seconds, BigDecimal distanceMeters) {
        validateWeight(module, weightKg);
        if (repetitions != null && !supportsRepetitions(type)) {
            throw new BadRequestException("Las repeticiones no corresponden a este tipo de registro.");
        }
        if (weightKg != null && type != TrainingRegistrationType.WEIGHT_AND_REPETITIONS) {
            throw new BadRequestException("El peso solo corresponde a ejercicios de peso y repeticiones.");
        }
        if (seconds != null && type != TrainingRegistrationType.TIME
                && type != TrainingRegistrationType.REPETITIONS_AND_TIME) {
            throw new BadRequestException("El tiempo solo corresponde a ejercicios con registro de tiempo.");
        }
        if (distanceMeters != null && type != TrainingRegistrationType.DISTANCE) {
            throw new BadRequestException("La distancia solo corresponde a ejercicios de distancia.");
        }
        if (type == TrainingRegistrationType.DISTANCE && repetitions != null) {
            throw new BadRequestException("La distancia no admite repeticiones.");
        }
    }

    private void validateSetMetrics(TrainingRegistrationType type, TrainingSetRequest request) {
        if (request.completed() && supportsRepetitions(type) && request.repetitions() == null) {
            throw new BadRequestException("Una serie completada requiere repeticiones.");
        }
        if (request.completed() && (type == TrainingRegistrationType.TIME
                || type == TrainingRegistrationType.REPETITIONS_AND_TIME) && request.seconds() == null) {
            throw new BadRequestException("Este tipo de registro requiere segundos por serie.");
        }
        if (request.completed() && type == TrainingRegistrationType.DISTANCE && request.distanceMeters() == null) {
            throw new BadRequestException("Este tipo de registro requiere distancia por serie.");
        }
        if (type != TrainingRegistrationType.DISTANCE && request.distanceMeters() != null) {
            throw new BadRequestException("La distancia solo corresponde a ejercicios de distancia.");
        }
        if (request.seconds() != null && type != TrainingRegistrationType.TIME
                && type != TrainingRegistrationType.REPETITIONS_AND_TIME) {
            throw new BadRequestException("El tiempo solo corresponde a ejercicios con registro de tiempo.");
        }
        if (request.repetitions() != null && !supportsRepetitions(type)) {
            throw new BadRequestException("Las repeticiones no corresponden a este tipo de registro.");
        }
    }

    private boolean supportsRepetitions(TrainingRegistrationType type) {
        return type == TrainingRegistrationType.REPETITIONS
                || type == TrainingRegistrationType.WEIGHT_AND_REPETITIONS
                || type == TrainingRegistrationType.REPETITIONS_AND_TIME;
    }

    private void validateWeight(TrainingModule module, BigDecimal weightKg) {
        if (module == TrainingModule.CALISTHENICS && weightKg != null) {
            throw new BadRequestException("Calistenia no admite peso en kilogramos.");
        }
    }

    private void validateSets(TrainingModule module, TrainingRegistrationType type, List<TrainingSetRequest> setRequests) {
        if (setRequests == null) return;
        Set<Integer> numbers = setRequests.stream().map(TrainingSetRequest::setNumber).collect(Collectors.toSet());
        if (numbers.size() != setRequests.size()) {
            throw new BadRequestException("No puede haber números de serie repetidos en un ejercicio de sesión.");
        }
        for (TrainingSetRequest setRequest : setRequests) {
            validateWeight(module, setRequest.weightKg());
            if (setRequest.weightKg() != null && type != TrainingRegistrationType.WEIGHT_AND_REPETITIONS) {
                throw new BadRequestException("El peso solo corresponde a ejercicios de peso y repeticiones.");
            }
            validateSetMetrics(type, setRequest);
        }
    }

    private void validateTimes(OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new BadRequestException("La finalización no puede ser anterior al inicio.");
        }
    }

    private Integer resolveDuration(OffsetDateTime startedAt, OffsetDateTime finishedAt, Integer requested,
            Integer fallback) {
        if (requested != null) return requested;
        if (startedAt == null || finishedAt == null) return fallback;
        long minutes = Duration.between(startedAt, finishedAt).toMinutes();
        if (minutes > Integer.MAX_VALUE) {
            throw new BadRequestException("La duración de la sesión es demasiado extensa.");
        }
        return (int) minutes;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadRequestException("La fecha final no puede ser anterior a la inicial.");
        }
        if (from != null && to != null && from.plusDays(93).isBefore(to)) {
            throw new BadRequestException("El rango máximo de consulta es de 94 días.");
        }
    }

    private TrainingExerciseResponse toExerciseResponse(TrainingExercise exercise) {
        return new TrainingExerciseResponse(exercise.getId(), exercise.getName(), exercise.getDescription(),
                exercise.getCategory().getName(), exercise.getModule(), exercise.isGlobalExercise(),
                !exercise.isGlobalExercise(), exercise.isActive(),
                exercise.getCreatedAt(), exercise.getUpdatedAt(), exercise.getCategory().getId(),
                exercise.getNormalizedName(), exercise.getCode(), List.copyOf(exercise.getPrimaryMuscles()),
                List.copyOf(exercise.getSecondaryMuscles()), exercise.getEquipment(), exercise.getDifficulty(),
                exercise.getRegistrationType(), exercise.isUnilateral(), exercise.isExternalLoad(),
                exercise.isSystemExercise());
    }

    private TrainingCategoryResponse toCategoryResponse(TrainingCategory category) {
        return new TrainingCategoryResponse(category.getId(), category.getName(), category.getModule(),
                category.isSystemCategory(), !category.isSystemCategory(), category.isActive(),
                category.getCreatedAt(), category.getUpdatedAt());
    }

    private LegacyTrainingPlanResponse toPresetResponse(TrainingPlan preset) {
        return new LegacyTrainingPlanResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getModule(),
                preset.isActive(), preset.getCreatedAt(), preset.getUpdatedAt(), preset.getVersion());
    }

    private TrainingPlanResponse toPlanResponse(TrainingPlan plan) {
        return new TrainingPlanResponse(plan.getId(), plan.getName(), plan.getDescription(), plan.getModule(),
                plan.getFrequencyMode(), plan.getTargetSessionsPerWeek(), plan.getStartDate(), plan.getEndDate(),
                plan.isActive(), plan.getCreatedAt(), plan.getUpdatedAt(), plan.getVersion());
    }

    private TrainingPlanDetailResponse toPlanDetailResponse(TrainingPlan plan) {
        return new TrainingPlanDetailResponse(plan.getId(), plan.getName(), plan.getDescription(), plan.getModule(),
                plan.getFrequencyMode(), plan.getTargetSessionsPerWeek(), plan.getStartDate(), plan.getEndDate(),
                plan.isActive(), plan.getCreatedAt(), plan.getUpdatedAt(), plan.getVersion(), liveDays(plan).stream()
                        .map(day -> new TrainingPlanDayResponse(day.getId(), day.getName(), day.getDescription(),
                                day.getDayOfWeek(), day.getPosition(), day.isActive(), livePresetExercises(day).stream()
                                        .map(exercise -> new TrainingPlanExerciseResponse(exercise.getId(),
                                                exercise.getExercise().getId(), exercise.getExercise().getName(),
                                                exercise.getTargetSets(), exercise.getTargetRepetitions(),
                                                exercise.getTargetWeightKg(), exercise.getNotes(), exercise.getPosition(),
                                                exercise.isActive(), exercise.getRegistrationType(),
                                                exercise.getTargetSeconds(), exercise.getTargetDistanceMeters())).toList())).toList());
    }

    private LegacyTrainingPlanDetailResponse toPresetDetailResponse(TrainingPlan preset) {
        return new LegacyTrainingPlanDetailResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getModule(),
                preset.isActive(), preset.getCreatedAt(), preset.getUpdatedAt(), preset.getVersion(),
                liveDays(preset).stream().map(this::toDayResponse).toList());
    }

    private LegacyPlanDayResponse toDayResponse(TrainingPlanDay day) {
        return new LegacyPlanDayResponse(day.getId(), day.getName(), day.getDescription(), day.getDayOfWeek(), day.getPosition(), day.isActive(),
                day.getCreatedAt(), day.getUpdatedAt(),
                livePresetExercises(day).stream().map(this::toPresetExerciseResponse).toList());
    }

    private LegacyPlanExerciseResponse toPresetExerciseResponse(TrainingPlanExercise presetExercise) {
        TrainingExercise exercise = presetExercise.getExercise();
        return new LegacyPlanExerciseResponse(presetExercise.getId(), exercise.getId(), exercise.getName(),
                presetExercise.getTargetSets(), presetExercise.getTargetRepetitions(), presetExercise.getTargetWeightKg(),
                presetExercise.getNotes(), presetExercise.getPosition(), presetExercise.isActive(),
                presetExercise.getCreatedAt(), presetExercise.getUpdatedAt(), presetExercise.getRegistrationType(),
                presetExercise.getTargetSeconds(), presetExercise.getTargetDistanceMeters());
    }

    private TrainingSessionResponse toSessionResponse(TrainingSession session) {
        return new TrainingSessionResponse(session.getId(), session.getSessionDate(), session.getModule(),
                idOf(session.getSourcePlan()), idOf(session.getSourcePlanDay()), session.getTitle(), session.getStatus(),
                session.getStartedAt(), session.getFinishedAt(), session.getDurationMinutes(), session.getNotes(),
                session.getCreatedAt(), session.getUpdatedAt(), session.getVersion(),
                session.getExercises().stream().sorted(Comparator.comparingInt(TrainingSessionExercise::getPosition)
                        .thenComparing(TrainingSessionExercise::getId)).map(this::toSessionExerciseResponse).toList());
    }

    private TrainingSessionSummaryResponse toSessionSummaryResponse(TrainingSession session) {
        return new TrainingSessionSummaryResponse(session.getId(), session.getSessionDate(), session.getModule(),
                idOf(session.getSourcePlan()), idOf(session.getSourcePlanDay()), session.getTitle(), session.getStatus(),
                session.getStartedAt(), session.getFinishedAt(), session.getDurationMinutes(), session.getVersion());
    }

    private TrainingSessionExerciseResponse toSessionExerciseResponse(TrainingSessionExercise exercise) {
        return new TrainingSessionExerciseResponse(exercise.getId(), idOf(exercise.getSourceExercise()),
                exercise.getExerciseName(), exercise.getTargetSets(), exercise.getTargetRepetitions(),
                exercise.getTargetWeightKg(), exercise.getNotes(), exercise.getPosition(),
                exercise.getSets().stream().sorted(Comparator.comparingInt(TrainingSet::getSetNumber)
                         .thenComparing(TrainingSet::getId)).map(this::toSetResponse).toList(),
                exercise.getRegistrationType(), exercise.getTargetSeconds(), exercise.getTargetDistanceMeters(),
                idOf(exercise.getSourcePlanExercise()), exercise.getOrigin());
    }

    private TrainingSetResponse toSetResponse(TrainingSet trainingSet) {
        return new TrainingSetResponse(trainingSet.getId(), trainingSet.getSetNumber(), trainingSet.getRepetitions(),
                trainingSet.getWeightKg(), trainingSet.isCompleted(), trainingSet.getNotes(),
                trainingSet.getCreatedAt(), trainingSet.getUpdatedAt(), trainingSet.getSeconds(),
                trainingSet.getDistanceMeters());
    }

    private TrainingCalendarDayResponse toCalendarDay(AppUser user, LocalDate date, List<TrainingSession> daySessions) {
        long inProgress = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.IN_PROGRESS).count();
        long completed = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.COMPLETED).count();
        long cancelled = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.CANCELLED).count();
        long duration = daySessions.stream().map(TrainingSession::getDurationMinutes).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
        List<TrainingModule> modules = daySessions.stream().map(TrainingSession::getModule)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TrainingModule.class))).stream().sorted().toList();
        List<TrainingCalendarSessionResponse> sessions = daySessions.stream().map(session -> new TrainingCalendarSessionResponse(
                session.getId(), session.getModule(), session.getTitle(), session.getStatus(),
                idOf(session.getSourcePlan()), idOf(session.getSourcePlanDay()), session.getVersion())).toList();
        return new TrainingCalendarDayResponse(date, daySessions.size(), inProgress, completed, cancelled, duration, modules,
                sessions, schedulesForDate(user, date));
    }

    private List<TrainingPlanDay> liveDays(TrainingPlan preset) {
        return preset.getDays().stream().filter(day -> day.getDeletedAt() == null)
                .sorted(Comparator.comparingInt(TrainingPlanDay::getPosition).thenComparing(TrainingPlanDay::getId)).toList();
    }

    private List<TrainingPlanExercise> livePresetExercises(TrainingPlan preset) {
        return preset.getDays().stream().filter(day -> day.getDeletedAt() == null)
                .flatMap(day -> livePresetExercises(day).stream()).toList();
    }

    private List<TrainingPlanExercise> livePresetExercises(TrainingPlanDay day) {
        return day.getExercises().stream().filter(exercise -> exercise.getDeletedAt() == null)
                .sorted(Comparator.comparingInt(TrainingPlanExercise::getPosition)
                        .thenComparing(TrainingPlanExercise::getId)).toList();
    }

    private <T> List<T> ordered(List<T> current, List<Long> requestedIds, String label) {
        Map<Long, T> byId = new LinkedHashMap<>();
        for (T item : current) {
            Long id = item instanceof TrainingPlanDay day ? day.getId() : ((TrainingPlanExercise) item).getId();
            byId.put(id, item);
        }
        if (byId.size() != requestedIds.size() || requestedIds.stream().distinct().count() != requestedIds.size()
                || !byId.keySet().containsAll(requestedIds)) {
            throw new BadRequestException("El orden debe incluir exactamente todos los " + label + ".");
        }
        return requestedIds.stream().map(byId::get).toList();
    }

    private void touch(TrainingPlan preset) {
        preset.setUpdatedAt(OffsetDateTime.now());
    }

    private void touch(TrainingPlanDay day) {
        day.setUpdatedAt(OffsetDateTime.now());
    }

    private void checkVersion(Long requested, Long actual, String resource) {
        if (requested != null && !Objects.equals(requested, versionOrZeroValue(actual))) {
            throw new ConflictException(resource + " está desactualizada. Recargá y volvé a intentar.");
        }
    }

    private static long versionOrZeroValue(Long version) {
        return version == null ? 0L : version;
    }

    private static Long versionOrZero(Object entity) {
        if (entity instanceof TrainingPlan plan) return versionOrZeroValue(plan.getVersion());
        if (entity instanceof TrainingPlanDay day) return versionOrZeroValue(day.getVersion());
        return 0L;
    }

    private static String scheduleKey(TrainingPlan plan, TrainingPlanDay day) {
        return idOf(plan) + ":" + idOf(day);
    }

    private Pageable page(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 50), sort);
    }

    private <S, T> PageResponse<T> page(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    private static Long idOf(Object entity) {
        if (entity == null) return null;
        if (entity instanceof TrainingPlan preset) return preset.getId();
        if (entity instanceof TrainingPlanDay day) return day.getId();
        if (entity instanceof TrainingPlanExercise planExercise) return planExercise.getId();
        if (entity instanceof TrainingSessionExercise sessionExercise) return sessionExercise.getId();
        return ((TrainingExercise) entity).getId();
    }

    private static String normalized(String value) {
        return value.trim();
    }

    private static String normalizedKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizedValues(List<String> values) {
        if (values == null) return new LinkedHashSet<>();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record SessionSource(TrainingPlan plan, TrainingPlanDay planDay) {
    }
}
