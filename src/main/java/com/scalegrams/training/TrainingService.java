package com.scalegrams.training;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.scalegrams.common.NotFoundException;
import com.scalegrams.training.TrainingDtos.CompleteTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.CreateTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.DuplicateTrainingPlanRequest;
import com.scalegrams.training.TrainingDtos.PageResponse;
import com.scalegrams.training.TrainingDtos.ReorderRequest;
import com.scalegrams.training.TrainingDtos.TrainingCalendarDayResponse;
import com.scalegrams.training.TrainingDtos.TrainingCalendarSessionResponse;
import com.scalegrams.training.TrainingDtos.TrainingDashboardResponse;
import com.scalegrams.training.TrainingDtos.LegacyPlanDayResponse;
import com.scalegrams.training.TrainingDtos.TrainingExerciseResponse;
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
    private final TrainingPlanRepository presets;
    private final TrainingPlanDayRepository days;
    private final TrainingPlanExerciseRepository presetExercises;
    private final TrainingSessionRepository sessions;
    private final TrainingSessionExerciseRepository sessionExercises;

    public TrainingService(TrainingExerciseRepository exercises, TrainingPlanRepository presets,
            TrainingPlanDayRepository days, TrainingPlanExerciseRepository presetExercises,
            TrainingSessionRepository sessions, TrainingSessionExerciseRepository sessionExercises) {
        this.exercises = exercises;
        this.presets = presets;
        this.days = days;
        this.presetExercises = presetExercises;
        this.sessions = sessions;
        this.sessionExercises = sessionExercises;
    }

    @Transactional(readOnly = true)
    public List<TrainingModuleResponse> modules() {
        return List.of(
                new TrainingModuleResponse(TrainingModule.GYM, "Gimnasio"),
                new TrainingModuleResponse(TrainingModule.CALISTHENICS, "Calistenia"));
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingExerciseResponse> exercises(AppUser user, String query, TrainingModule module,
            int page, int size) {
        Page<TrainingExercise> result = exercises.search(user, module, blankToNull(query),
                page(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return page(result, this::toExerciseResponse);
    }

    @Transactional
    public TrainingExerciseResponse createExercise(AppUser user, UpsertExerciseRequest request) {
        String name = normalized(request.name());
        TrainingExercise existing = exercises.findByOwnerAndModuleAndNameIgnoreCaseAndDeletedAtIsNull(user,
                request.module(), name).orElse(null);
        if (existing != null) {
            return toExerciseResponse(existing);
        }
        TrainingExercise exercise = new TrainingExercise();
        exercise.setOwner(user);
        exercise.setName(name);
        exercise.setModule(request.module());
        exercise.setDescription(blankToNull(request.description()));
        exercise.setCategory(blankToNull(request.category()));
        exercise.setGlobalExercise(false);
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
        exercise.setModule(request.module());
        exercise.setDescription(blankToNull(request.description()));
        exercise.setCategory(blankToNull(request.category()));
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
        applyPresetExercise(presetExercise, day.getPlan().getModule(), request);
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
        applyPresetExercise(presetExercise, day.getPlan().getModule(), request);
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
        return toPlanDetailResponse(presets.save(plan));
    }

    @Transactional
    public TrainingPlanDetailResponse updatePlan(AppUser user, Long id, UpsertTrainingPlanRequest request) {
        TrainingPlan plan = requirePresetDetail(user, id);
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
        TrainingSessionSummaryResponse session = sessions.search(user, date, date, date, plan.getModule(), null,
                id, day == null ? null : day.getId(), page(0, 1, Sort.unsorted())).stream().findFirst()
                .map(this::toSessionSummaryResponse).orElse(null);
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
        TrainingSession existing = sessions.search(user, request.date(), request.date(), request.date(), plan.getModule(),
                null, planId, day.getId(), page(0, 10, Sort.unsorted())).stream()
                .filter(item -> item.getStatus() == TrainingSessionStatus.COMPLETED
                        || item.getStatus() == TrainingSessionStatus.SKIPPED)
                .findFirst().orElse(null);
        if (existing != null) return toSessionResponse(existing);

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
        return toSessionResponse(sessions.save(skipped));
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
                validateWeight(request.module(), exerciseRequest.targetWeightKg());
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
        SessionSource source = resolveSource(user, request.module(), request.planId(), request.planDayId());
        validateScheduledSession(user, source, request.date());
        TrainingSession session = new TrainingSession();
        session.setUser(user);
        applySession(session, request.date(), request.module(), source, request.title(),
                request.status() == null ? TrainingSessionStatus.STARTED : request.status(), request.startedAt(),
                request.finishedAt(), request.durationMinutes(), request.notes());

        if (source.planDay() != null) {
            for (TrainingPlanExercise presetExercise : livePresetExercises(source.planDay())) {
                if (!presetExercise.isActive()) continue;
                addSessionExerciseSnapshot(session, presetExercise, session.getExercises().size());
            }
        }
        if (request.exercises() != null) {
            for (TrainingSessionExerciseRequest exerciseRequest : request.exercises()) {
                addSessionExercise(session, user, exerciseRequest, session.getExercises().size());
            }
        }
        sessions.save(session);
        return toSessionResponse(session);
    }

    @Transactional
    public TrainingSessionResponse updateSession(AppUser user, Long id, UpdateTrainingSessionRequest request) {
        TrainingSession session = requireSession(user, id);
        if (session.getStatus() == TrainingSessionStatus.COMPLETED) {
            throw new BadRequestException("Las sesiones completadas son históricas y no se pueden modificar.");
        }
        SessionSource source = resolveSource(user, request.module(), request.planId(), request.planDayId());
        validateScheduledSession(user, source, request.date());
        applySession(session, request.date(), request.module(), source, request.title(), request.status(), request.startedAt(),
                request.finishedAt(), request.durationMinutes(), request.notes());
        session.getExercises().clear();
        for (TrainingSessionExerciseRequest exerciseRequest : request.exercises()) {
            addSessionExercise(session, user, exerciseRequest, session.getExercises().size());
        }
        return toSessionResponse(session);
    }

    @Transactional
    public TrainingSessionResponse completeSession(AppUser user, Long id, CompleteTrainingSessionRequest request) {
        TrainingSession session = requireSession(user, id);
        if (session.getStatus() == TrainingSessionStatus.CANCELLED || session.getStatus() == TrainingSessionStatus.SKIPPED) {
            throw new BadRequestException("No podés completar una sesión cancelada u omitida.");
        }
        OffsetDateTime finishedAt = request != null && request.finishedAt() != null
                ? request.finishedAt() : OffsetDateTime.now();
        validateTimes(session.getStartedAt(), finishedAt);
        session.setStatus(TrainingSessionStatus.COMPLETED);
        session.setFinishedAt(finishedAt);
        session.setDurationMinutes(resolveDuration(session.getStartedAt(), finishedAt,
                request == null ? null : request.durationMinutes(), session.getDurationMinutes()));
        session.setUpdatedAt(OffsetDateTime.now());
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

        TrainingSessionSummaryResponse recentSession = sessions.search(user, date, date, date, null, null, null, null,
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
            LegacyPlanExerciseRequest request) {
        validateWeight(module, request.targetWeightKg());
        presetExercise.setTargetSets(request.targetSets());
        presetExercise.setTargetRepetitions(request.targetRepetitions());
        presetExercise.setTargetWeightKg(request.targetWeightKg());
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
        return presets.search(user, null, false, page(0, 50, Sort.unsorted())).stream()
                .map(plan -> {
                    if (plan.getFrequencyMode() == TrainingFrequencyMode.DYNAMIC && !date.equals(LocalDate.now())) {
                        return null;
                    }
                    TrainingPlanDay day = resolvePlanDay(user, plan, date);
                    return day == null ? null : new TrainingPlanScheduleResponse(plan.getId(), day.getId(), day.getName(),
                            plan.getModule(), true);
                })
                .filter(Objects::nonNull).toList();
    }

    private void applySession(TrainingSession session, LocalDate date, TrainingModule module, SessionSource source,
            String title, TrainingSessionStatus status, OffsetDateTime startedAt, OffsetDateTime finishedAt,
            Integer durationMinutes, String notes) {
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
        validateWeight(session.getModule(), source.getTargetWeightKg());
        TrainingSessionExercise snapshot = new TrainingSessionExercise();
        snapshot.setSession(session);
        snapshot.setSourceExercise(source.getExercise());
        snapshot.setExerciseName(source.getExercise().getName());
        snapshot.setTargetSets(source.getTargetSets());
        snapshot.setTargetRepetitions(source.getTargetRepetitions());
        snapshot.setTargetWeightKg(source.getTargetWeightKg());
        snapshot.setNotes(source.getNotes());
        snapshot.setPosition(position);
        session.getExercises().add(snapshot);
    }

    private void addSessionExercise(TrainingSession session, AppUser user, TrainingSessionExerciseRequest request,
            int position) {
        TrainingExercise exercise = requireSelectableExercise(user, request.exerciseId());
        validateExerciseModule(session.getModule(), exercise);
        validateWeight(session.getModule(), request.targetWeightKg());
        validateSets(session.getModule(), request.sets());

        TrainingSessionExercise sessionExercise = new TrainingSessionExercise();
        sessionExercise.setSession(session);
        sessionExercise.setSourceExercise(exercise);
        sessionExercise.setExerciseName(exercise.getName());
        sessionExercise.setTargetSets(request.targetSets());
        sessionExercise.setTargetRepetitions(request.targetRepetitions());
        sessionExercise.setTargetWeightKg(request.targetWeightKg());
        sessionExercise.setNotes(blankToNull(request.notes()));
        sessionExercise.setPosition(position);
        if (request.sets() != null) {
            for (TrainingSetRequest setRequest : request.sets()) {
                TrainingSet trainingSet = new TrainingSet();
                trainingSet.setSessionExercise(sessionExercise);
                trainingSet.setSetNumber(setRequest.setNumber());
                trainingSet.setRepetitions(setRequest.repetitions());
                trainingSet.setWeightKg(setRequest.weightKg());
                trainingSet.setCompleted(setRequest.completed());
                sessionExercise.getSets().add(trainingSet);
            }
        }
        session.getExercises().add(sessionExercise);
    }

    private void validateExerciseModule(TrainingModule module, TrainingExercise exercise) {
        if (exercise.getModule() != module) {
            throw new BadRequestException("El ejercicio pertenece a otro módulo de entrenamiento.");
        }
    }

    private void validateWeight(TrainingModule module, BigDecimal weightKg) {
        if (module == TrainingModule.CALISTHENICS && weightKg != null) {
            throw new BadRequestException("Calistenia no admite peso en kilogramos.");
        }
    }

    private void validateSets(TrainingModule module, List<TrainingSetRequest> setRequests) {
        if (setRequests == null) return;
        Set<Integer> numbers = setRequests.stream().map(TrainingSetRequest::setNumber).collect(Collectors.toSet());
        if (numbers.size() != setRequests.size()) {
            throw new BadRequestException("No puede haber números de serie repetidos en un ejercicio de sesión.");
        }
        for (TrainingSetRequest setRequest : setRequests) {
            validateWeight(module, setRequest.weightKg());
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
    }

    private TrainingExerciseResponse toExerciseResponse(TrainingExercise exercise) {
        return new TrainingExerciseResponse(exercise.getId(), exercise.getName(), exercise.getDescription(),
                exercise.getCategory(), exercise.getModule(), exercise.isGlobalExercise(),
                !exercise.isGlobalExercise(), exercise.isActive(),
                exercise.getCreatedAt(), exercise.getUpdatedAt());
    }

    private LegacyTrainingPlanResponse toPresetResponse(TrainingPlan preset) {
        return new LegacyTrainingPlanResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getModule(),
                preset.isActive(), preset.getCreatedAt(), preset.getUpdatedAt());
    }

    private TrainingPlanResponse toPlanResponse(TrainingPlan plan) {
        return new TrainingPlanResponse(plan.getId(), plan.getName(), plan.getDescription(), plan.getModule(),
                plan.getFrequencyMode(), plan.getTargetSessionsPerWeek(), plan.getStartDate(), plan.getEndDate(),
                plan.isActive(), plan.getCreatedAt(), plan.getUpdatedAt());
    }

    private TrainingPlanDetailResponse toPlanDetailResponse(TrainingPlan plan) {
        return new TrainingPlanDetailResponse(plan.getId(), plan.getName(), plan.getDescription(), plan.getModule(),
                plan.getFrequencyMode(), plan.getTargetSessionsPerWeek(), plan.getStartDate(), plan.getEndDate(),
                plan.isActive(), plan.getCreatedAt(), plan.getUpdatedAt(), liveDays(plan).stream()
                        .map(day -> new TrainingPlanDayResponse(day.getId(), day.getName(), day.getDescription(),
                                day.getDayOfWeek(), day.getPosition(), day.isActive(), livePresetExercises(day).stream()
                                        .map(exercise -> new TrainingPlanExerciseResponse(exercise.getId(),
                                                exercise.getExercise().getId(), exercise.getExercise().getName(),
                                                exercise.getTargetSets(), exercise.getTargetRepetitions(),
                                                exercise.getTargetWeightKg(), exercise.getNotes(), exercise.getPosition(),
                                                exercise.isActive())).toList())).toList());
    }

    private LegacyTrainingPlanDetailResponse toPresetDetailResponse(TrainingPlan preset) {
        return new LegacyTrainingPlanDetailResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getModule(),
                preset.isActive(), preset.getCreatedAt(), preset.getUpdatedAt(),
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
                presetExercise.getCreatedAt(), presetExercise.getUpdatedAt());
    }

    private TrainingSessionResponse toSessionResponse(TrainingSession session) {
        return new TrainingSessionResponse(session.getId(), session.getSessionDate(), session.getModule(),
                idOf(session.getSourcePlan()), idOf(session.getSourcePlanDay()), session.getTitle(), session.getStatus(),
                session.getStartedAt(), session.getFinishedAt(), session.getDurationMinutes(), session.getNotes(),
                session.getCreatedAt(), session.getUpdatedAt(),
                session.getExercises().stream().sorted(Comparator.comparingInt(TrainingSessionExercise::getPosition)
                        .thenComparing(TrainingSessionExercise::getId)).map(this::toSessionExerciseResponse).toList());
    }

    private TrainingSessionSummaryResponse toSessionSummaryResponse(TrainingSession session) {
        return new TrainingSessionSummaryResponse(session.getId(), session.getSessionDate(), session.getModule(),
                idOf(session.getSourcePlan()), idOf(session.getSourcePlanDay()), session.getTitle(), session.getStatus(),
                session.getStartedAt(), session.getFinishedAt(), session.getDurationMinutes());
    }

    private TrainingSessionExerciseResponse toSessionExerciseResponse(TrainingSessionExercise exercise) {
        return new TrainingSessionExerciseResponse(exercise.getId(), idOf(exercise.getSourceExercise()),
                exercise.getExerciseName(), exercise.getTargetSets(), exercise.getTargetRepetitions(),
                exercise.getTargetWeightKg(), exercise.getNotes(), exercise.getPosition(),
                exercise.getSets().stream().sorted(Comparator.comparingInt(TrainingSet::getSetNumber)
                        .thenComparing(TrainingSet::getId)).map(this::toSetResponse).toList());
    }

    private TrainingSetResponse toSetResponse(TrainingSet trainingSet) {
        return new TrainingSetResponse(trainingSet.getId(), trainingSet.getSetNumber(), trainingSet.getRepetitions(),
                trainingSet.getWeightKg(), trainingSet.isCompleted(), trainingSet.getNotes(),
                trainingSet.getCreatedAt(), trainingSet.getUpdatedAt());
    }

    private TrainingCalendarDayResponse toCalendarDay(AppUser user, LocalDate date, List<TrainingSession> daySessions) {
        long started = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.STARTED).count();
        long completed = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.COMPLETED).count();
        long cancelled = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.CANCELLED).count();
        long duration = daySessions.stream().map(TrainingSession::getDurationMinutes).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
        List<TrainingModule> modules = daySessions.stream().map(TrainingSession::getModule)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TrainingModule.class))).stream().sorted().toList();
        List<TrainingCalendarSessionResponse> sessions = daySessions.stream().map(session -> new TrainingCalendarSessionResponse(
                session.getId(), session.getModule(), session.getTitle(), session.getStatus(),
                idOf(session.getSourcePlan()), idOf(session.getSourcePlanDay()))).toList();
        return new TrainingCalendarDayResponse(date, daySessions.size(), started, completed, cancelled, duration, modules,
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
        return ((TrainingExercise) entity).getId();
    }

    private static String normalized(String value) {
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record SessionSource(TrainingPlan plan, TrainingPlanDay planDay) {
    }
}
