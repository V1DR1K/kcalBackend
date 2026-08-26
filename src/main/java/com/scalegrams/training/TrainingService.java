package com.scalegrams.training;

import java.math.BigDecimal;
import java.time.Duration;
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
import com.scalegrams.training.TrainingDtos.DuplicatePresetRequest;
import com.scalegrams.training.TrainingDtos.PageResponse;
import com.scalegrams.training.TrainingDtos.ReorderRequest;
import com.scalegrams.training.TrainingDtos.TrainingCalendarDayResponse;
import com.scalegrams.training.TrainingDtos.TrainingCalendarSessionResponse;
import com.scalegrams.training.TrainingDtos.TrainingDashboardResponse;
import com.scalegrams.training.TrainingDtos.TrainingDayResponse;
import com.scalegrams.training.TrainingDtos.TrainingExerciseResponse;
import com.scalegrams.training.TrainingDtos.TrainingModuleResponse;
import com.scalegrams.training.TrainingDtos.TrainingPresetDetailResponse;
import com.scalegrams.training.TrainingDtos.TrainingPresetExerciseResponse;
import com.scalegrams.training.TrainingDtos.TrainingPresetResponse;
import com.scalegrams.training.TrainingDtos.TrainingSessionExerciseRequest;
import com.scalegrams.training.TrainingDtos.TrainingSessionExerciseResponse;
import com.scalegrams.training.TrainingDtos.TrainingSessionResponse;
import com.scalegrams.training.TrainingDtos.TrainingSessionSummaryResponse;
import com.scalegrams.training.TrainingDtos.TrainingSetRequest;
import com.scalegrams.training.TrainingDtos.TrainingSetResponse;
import com.scalegrams.training.TrainingDtos.UpdateTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.UpsertExerciseRequest;
import com.scalegrams.training.TrainingDtos.UpsertPresetExerciseRequest;
import com.scalegrams.training.TrainingDtos.UpsertPresetRequest;
import com.scalegrams.training.TrainingDtos.UpsertTrainingDayRequest;
import com.scalegrams.training.TrainingDtos.WeeklyTrainingSummaryResponse;
import com.scalegrams.user.AppUser;

@Service
public class TrainingService {
    private final TrainingExerciseRepository exercises;
    private final TrainingPresetRepository presets;
    private final TrainingDayRepository days;
    private final TrainingPresetExerciseRepository presetExercises;
    private final TrainingSessionRepository sessions;
    private final TrainingSessionExerciseRepository sessionExercises;

    public TrainingService(TrainingExerciseRepository exercises, TrainingPresetRepository presets,
            TrainingDayRepository days, TrainingPresetExerciseRepository presetExercises,
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
        exercise.setActive(request.active() == null || request.active());
        return toExerciseResponse(exercises.save(exercise));
    }

    @Transactional
    public TrainingExerciseResponse updateExercise(AppUser user, Long id, UpsertExerciseRequest request) {
        TrainingExercise exercise = requireExercise(user, id);
        String name = normalized(request.name());
        if (exercises.existsLiveName(user, request.module(), name, exercise.getId())) {
            throw new BadRequestException("Ya existe un ejercicio con ese nombre para este módulo.");
        }
        if (exercise.getModule() != request.module() && presetExercises.existsByExerciseAndDeletedAtIsNull(exercise)) {
            throw new BadRequestException("No podés cambiar el módulo de un ejercicio usado en una rutina.");
        }
        exercise.setName(name);
        exercise.setModule(request.module());
        if (request.active() != null) exercise.setActive(request.active());
        exercise.setUpdatedAt(OffsetDateTime.now());
        return toExerciseResponse(exercise);
    }

    @Transactional
    public void deleteExercise(AppUser user, Long id) {
        TrainingExercise exercise = requireExercise(user, id);
        OffsetDateTime now = OffsetDateTime.now();
        exercise.setActive(false);
        exercise.setDeletedAt(now);
        exercise.setUpdatedAt(now);
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingPresetResponse> presets(AppUser user, TrainingModule module, boolean includeInactive,
            int page, int size) {
        Page<TrainingPreset> result = presets.search(user, module, includeInactive,
                page(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return page(result, this::toPresetResponse);
    }

    @Transactional(readOnly = true)
    public TrainingPresetDetailResponse preset(AppUser user, Long id) {
        return toPresetDetailResponse(requirePresetDetail(user, id));
    }

    @Transactional
    public TrainingPresetDetailResponse createPreset(AppUser user, UpsertPresetRequest request) {
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, request.module(), name, null);
        TrainingPreset preset = new TrainingPreset();
        preset.setOwner(user);
        applyPreset(preset, request, name);
        presets.save(preset);
        return toPresetDetailResponse(preset);
    }

    @Transactional
    public TrainingPresetDetailResponse updatePreset(AppUser user, Long id, UpsertPresetRequest request) {
        TrainingPreset preset = requirePresetDetail(user, id);
        if (preset.getModule() != request.module() && !livePresetExercises(preset).isEmpty()) {
            throw new BadRequestException("No podés cambiar el módulo de una rutina que tiene ejercicios.");
        }
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, request.module(), name, preset.getId());
        applyPreset(preset, request, name);
        return toPresetDetailResponse(preset);
    }

    @Transactional
    public void deletePreset(AppUser user, Long id) {
        TrainingPreset preset = requirePreset(user, id);
        OffsetDateTime now = OffsetDateTime.now();
        preset.setActive(false);
        preset.setDeletedAt(now);
        preset.setUpdatedAt(now);
    }

    @Transactional
    public TrainingPresetDetailResponse duplicatePreset(AppUser user, Long id, DuplicatePresetRequest request) {
        TrainingPreset source = requirePresetDetail(user, id);
        String name = normalized(request.name());
        ensurePresetNameAvailable(user, source.getModule(), name, null);

        TrainingPreset copy = new TrainingPreset();
        copy.setOwner(user);
        copy.setName(name);
        copy.setDescription(source.getDescription());
        copy.setModule(source.getModule());
        copy.setActive(true);

        int dayPosition = 0;
        for (TrainingDay sourceDay : liveDays(source)) {
            TrainingDay dayCopy = new TrainingDay();
            dayCopy.setPreset(copy);
            dayCopy.setDayOfWeek(sourceDay.getDayOfWeek());
            dayCopy.setPosition(dayPosition++);
            dayCopy.setActive(sourceDay.isActive());
            copy.getDays().add(dayCopy);

            int exercisePosition = 0;
            for (TrainingPresetExercise sourceExercise : livePresetExercises(sourceDay)) {
                TrainingPresetExercise exerciseCopy = new TrainingPresetExercise();
                exerciseCopy.setTrainingDay(dayCopy);
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
    public TrainingDayResponse createDay(AppUser user, Long presetId, UpsertTrainingDayRequest request) {
        TrainingPreset preset = requirePresetDetail(user, presetId);
        TrainingDay day = new TrainingDay();
        day.setPreset(preset);
        day.setDayOfWeek(request.dayOfWeek());
        day.setActive(request.active() == null || request.active());
        day.setPosition(liveDays(preset).size());
        preset.getDays().add(day);
        touch(preset);
        presets.save(preset);
        return toDayResponse(day);
    }

    @Transactional
    public TrainingDayResponse updateDay(AppUser user, Long presetId, Long dayId, UpsertTrainingDayRequest request) {
        TrainingDay day = requireDay(user, presetId, dayId);
        day.setDayOfWeek(request.dayOfWeek());
        if (request.active() != null) day.setActive(request.active());
        day.setUpdatedAt(OffsetDateTime.now());
        touch(day.getPreset());
        return toDayResponse(day);
    }

    @Transactional
    public void deleteDay(AppUser user, Long presetId, Long dayId) {
        TrainingDay day = requireDay(user, presetId, dayId);
        OffsetDateTime now = OffsetDateTime.now();
        day.setActive(false);
        day.setDeletedAt(now);
        day.setUpdatedAt(now);
        touch(day.getPreset());
    }

    @Transactional
    public List<TrainingDayResponse> reorderDays(AppUser user, Long presetId, ReorderRequest request) {
        TrainingPreset preset = requirePresetDetail(user, presetId);
        List<TrainingDay> ordered = ordered(liveDays(preset), request.ids(), "días de la rutina");
        OffsetDateTime now = OffsetDateTime.now();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setPosition(index);
            ordered.get(index).setUpdatedAt(now);
        }
        touch(preset);
        return ordered.stream().map(this::toDayResponse).toList();
    }

    @Transactional
    public TrainingPresetExerciseResponse createPresetExercise(AppUser user, Long presetId, Long dayId,
            UpsertPresetExerciseRequest request) {
        TrainingDay day = requireDay(user, presetId, dayId);
        TrainingExercise exercise = requireSelectableExercise(user, request.exerciseId());
        validateExerciseModule(day.getPreset().getModule(), exercise);
        ensureDayExerciseAvailable(day, exercise, null);

        TrainingPresetExercise presetExercise = new TrainingPresetExercise();
        presetExercise.setTrainingDay(day);
        presetExercise.setExercise(exercise);
        presetExercise.setPosition(livePresetExercises(day).size());
        applyPresetExercise(presetExercise, day.getPreset().getModule(), request);
        day.getExercises().add(presetExercise);
        touch(day.getPreset());
        days.save(day);
        return toPresetExerciseResponse(presetExercise);
    }

    @Transactional
    public TrainingPresetExerciseResponse updatePresetExercise(AppUser user, Long presetId, Long dayId,
            Long presetExerciseId, UpsertPresetExerciseRequest request) {
        TrainingDay day = requireDay(user, presetId, dayId);
        TrainingPresetExercise presetExercise = requirePresetExercise(day, presetExerciseId);
        TrainingExercise exercise = requireSelectableExercise(user, request.exerciseId());
        validateExerciseModule(day.getPreset().getModule(), exercise);
        ensureDayExerciseAvailable(day, exercise, presetExercise.getId());
        presetExercise.setExercise(exercise);
        applyPresetExercise(presetExercise, day.getPreset().getModule(), request);
        touch(day.getPreset());
        return toPresetExerciseResponse(presetExercise);
    }

    @Transactional
    public void deletePresetExercise(AppUser user, Long presetId, Long dayId, Long presetExerciseId) {
        TrainingDay day = requireDay(user, presetId, dayId);
        TrainingPresetExercise presetExercise = requirePresetExercise(day, presetExerciseId);
        OffsetDateTime now = OffsetDateTime.now();
        presetExercise.setActive(false);
        presetExercise.setDeletedAt(now);
        presetExercise.setUpdatedAt(now);
        touch(day.getPreset());
    }

    @Transactional
    public List<TrainingPresetExerciseResponse> reorderPresetExercises(AppUser user, Long presetId, Long dayId,
            ReorderRequest request) {
        TrainingDay day = requireDay(user, presetId, dayId);
        List<TrainingPresetExercise> ordered = ordered(livePresetExercises(day), request.ids(), "ejercicios del día");
        OffsetDateTime now = OffsetDateTime.now();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setPosition(index);
            ordered.get(index).setUpdatedAt(now);
        }
        touch(day.getPreset());
        return ordered.stream().map(this::toPresetExerciseResponse).toList();
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
        SessionSource source = resolveSource(user, request.module(), request.presetId(), request.trainingDayId());
        TrainingSession session = new TrainingSession();
        session.setUser(user);
        applySession(session, request.date(), request.module(), source, request.title(),
                request.status() == null ? TrainingSessionStatus.STARTED : request.status(), request.startedAt(),
                request.finishedAt(), request.durationMinutes(), request.notes());

        if (source.trainingDay() != null) {
            for (TrainingPresetExercise presetExercise : livePresetExercises(source.trainingDay())) {
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
        SessionSource source = resolveSource(user, request.module(), request.presetId(), request.trainingDayId());
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
        if (session.getStatus() == TrainingSessionStatus.CANCELLED) {
            throw new BadRequestException("No podés completar una sesión cancelada.");
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
        sessions.delete(requireSession(user, id));
    }

    @Transactional(readOnly = true)
    public List<TrainingCalendarDayResponse> calendar(AppUser user, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Map<LocalDate, List<TrainingSession>> byDate = sessions.findForCalendar(user, from, to).stream()
                .collect(Collectors.groupingBy(TrainingSession::getSessionDate, LinkedHashMap::new, Collectors.toList()));
        return byDate.entrySet().stream().map(entry -> toCalendarDay(entry.getKey(), entry.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public TrainingDashboardResponse dashboard(AppUser user, LocalDate date) {
        List<TrainingPresetResponse> routines = presets.search(user, null, true, 0, 5)
                .stream()
                .limit(4)
                .map(this::toPresetResponse)
                .toList();

        TrainingSessionSummaryResponse recentSession = sessions.search(user, date, date, date, null, null, null, null, 0, 1)
                .stream()
                .findFirst()
                .map(this::toSessionSummaryResponse)
                .orElse(null);

        long sessionCount = sessions.search(user, date, date, date, null, null, null, null, 0, 50)
                .getTotalElements();
        long totalMinutes = sessions.search(user, date, date, date, null, null, null, null, 0, 50)
                .stream()
                .filter(s -> s.getDurationMinutes() != null)
                .mapToLong(s -> s.getDurationMinutes())
                .sum();
        long totalSets = sessions.search(user, date, date, date, null, null, null, null, 0, 50)
                .flatMap(s -> s.getExercises().stream())
                .flatMap(e -> e.getSets().stream())
                .filter(s -> s.getRepetitions() != null)
                .count();

        List<TrainingExerciseResponse> exercises = exercises(user, "", null, 0, 50)
                .getContent()
                .stream()
                .limit(50)
                .map(this::toExerciseResponse)
                .toList();

        WeeklyTrainingSummaryResponse weeklySummary = new WeeklyTrainingSummaryResponse(
                sessionCount, totalMinutes, totalSets);

        return new TrainingDashboardResponse(date, routines, recentSession, weeklySummary, exercises);
    }

    private TrainingExercise requireExercise(AppUser user, Long id) {
        return exercises.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Ejercicio no encontrado."));
    }

    private TrainingExercise requireSelectableExercise(AppUser user, Long id) {
        TrainingExercise exercise = requireExercise(user, id);
        if (!exercise.isActive()) {
            throw new BadRequestException("El ejercicio está archivado.");
        }
        return exercise;
    }

    private TrainingPreset requirePreset(AppUser user, Long id) {
        return presets.findByIdAndOwnerAndDeletedAtIsNull(id, user)
                .orElseThrow(() -> new NotFoundException("Rutina no encontrada."));
    }

    private TrainingPreset requirePresetDetail(AppUser user, Long id) {
        return presets.findDetailByIdAndOwner(id, user)
                .orElseThrow(() -> new NotFoundException("Rutina no encontrada."));
    }

    private TrainingPreset requirePresetReference(AppUser user, Long id) {
        return presets.findByIdAndOwner(id, user)
                .orElseThrow(() -> new NotFoundException("Rutina no encontrada."));
    }

    private TrainingDay requireDay(AppUser user, Long presetId, Long dayId) {
        TrainingDay day = requireDayForUser(user, dayId);
        if (!Objects.equals(day.getPreset().getId(), presetId)) {
            throw new NotFoundException("Día de rutina no encontrado.");
        }
        return day;
    }

    private TrainingDay requireDayForUser(AppUser user, Long dayId) {
        return days.findDetailByIdAndOwner(dayId, user)
                .orElseThrow(() -> new NotFoundException("Día de rutina no encontrado."));
    }

    private TrainingDay requireDayReference(AppUser user, Long dayId) {
        return days.findByIdAndOwner(dayId, user)
                .orElseThrow(() -> new NotFoundException("Día de rutina no encontrado."));
    }

    private TrainingPresetExercise requirePresetExercise(TrainingDay day, Long id) {
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

    private void applyPreset(TrainingPreset preset, UpsertPresetRequest request, String name) {
        preset.setName(name);
        preset.setDescription(blankToNull(request.description()));
        preset.setModule(request.module());
        if (request.active() != null) preset.setActive(request.active());
        else if (preset.getId() == null) preset.setActive(true);
        preset.setUpdatedAt(OffsetDateTime.now());
    }

    private void applyPresetExercise(TrainingPresetExercise presetExercise, TrainingModule module,
            UpsertPresetExerciseRequest request) {
        validateWeight(module, request.targetWeightKg());
        presetExercise.setTargetSets(request.targetSets());
        presetExercise.setTargetRepetitions(request.targetRepetitions());
        presetExercise.setTargetWeightKg(request.targetWeightKg());
        presetExercise.setNotes(blankToNull(request.notes()));
        if (request.active() != null) presetExercise.setActive(request.active());
        else if (presetExercise.getId() == null) presetExercise.setActive(true);
        presetExercise.setUpdatedAt(OffsetDateTime.now());
    }

    private void ensureDayExerciseAvailable(TrainingDay day, TrainingExercise exercise, Long excludedId) {
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
            TrainingDay day = requireDayForUser(user, trainingDayId);
            TrainingPreset preset = day.getPreset();
            if (presetId != null && !Objects.equals(preset.getId(), presetId)) {
                throw new BadRequestException("El día no pertenece a la rutina indicada.");
            }
            validateSource(module, preset, day);
            return new SessionSource(preset, day);
        }

        TrainingPreset preset = requirePreset(user, presetId);
        if (!preset.isActive()) {
            throw new BadRequestException("La rutina está archivada.");
        }
        if (preset.getModule() != module) {
            throw new BadRequestException("La rutina pertenece a otro módulo de entrenamiento.");
        }
        return new SessionSource(preset, null);
    }

    private void validateSource(TrainingModule module, TrainingPreset preset, TrainingDay day) {
        if (!preset.isActive() || !day.isActive()) {
            throw new BadRequestException("La rutina o el día de origen está archivado.");
        }
        if (preset.getModule() != module) {
            throw new BadRequestException("El día de rutina pertenece a otro módulo de entrenamiento.");
        }
    }

    private void applySession(TrainingSession session, LocalDate date, TrainingModule module, SessionSource source,
            String title, TrainingSessionStatus status, OffsetDateTime startedAt, OffsetDateTime finishedAt,
            Integer durationMinutes, String notes) {
        validateTimes(startedAt, finishedAt);
        session.setSessionDate(date);
        session.setModule(module);
        session.setSourcePreset(source.preset());
        session.setSourceTrainingDay(source.trainingDay());
        session.setTitle(blankToNull(title));
        session.setStatus(status);
        session.setStartedAt(startedAt);
        session.setFinishedAt(finishedAt);
        session.setDurationMinutes(resolveDuration(startedAt, finishedAt, durationMinutes, null));
        session.setNotes(blankToNull(notes));
        session.setUpdatedAt(OffsetDateTime.now());
    }

    private void addSessionExerciseSnapshot(TrainingSession session, TrainingPresetExercise source, int position) {
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
                exercise.getCategory(), exercise.getModule(), exercise.isActive(),
                exercise.getCreatedAt(), exercise.getUpdatedAt());
    }

    private TrainingPresetResponse toPresetResponse(TrainingPreset preset) {
        return new TrainingPresetResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getModule(),
                preset.isActive(), preset.getCreatedAt(), preset.getUpdatedAt());
    }

    private TrainingPresetDetailResponse toPresetDetailResponse(TrainingPreset preset) {
        return new TrainingPresetDetailResponse(preset.getId(), preset.getName(), preset.getDescription(), preset.getModule(),
                preset.isActive(), preset.getCreatedAt(), preset.getUpdatedAt(),
                liveDays(preset).stream().map(this::toDayResponse).toList());
    }

    private TrainingDayResponse toDayResponse(TrainingDay day) {
        return new TrainingDayResponse(day.getId(), day.getDayOfWeek(), day.getPosition(), day.isActive(),
                day.getCreatedAt(), day.getUpdatedAt(),
                livePresetExercises(day).stream().map(this::toPresetExerciseResponse).toList());
    }

    private TrainingPresetExerciseResponse toPresetExerciseResponse(TrainingPresetExercise presetExercise) {
        TrainingExercise exercise = presetExercise.getExercise();
        return new TrainingPresetExerciseResponse(presetExercise.getId(), exercise.getId(), exercise.getName(),
                presetExercise.getTargetSets(), presetExercise.getTargetRepetitions(), presetExercise.getTargetWeightKg(),
                presetExercise.getNotes(), presetExercise.getPosition(), presetExercise.isActive(),
                presetExercise.getCreatedAt(), presetExercise.getUpdatedAt());
    }

    private TrainingSessionResponse toSessionResponse(TrainingSession session) {
        return new TrainingSessionResponse(session.getId(), session.getSessionDate(), session.getModule(),
                idOf(session.getSourcePreset()), idOf(session.getSourceTrainingDay()), session.getTitle(), session.getStatus(),
                session.getStartedAt(), session.getFinishedAt(), session.getDurationMinutes(), session.getNotes(),
                session.getCreatedAt(), session.getUpdatedAt(),
                session.getExercises().stream().sorted(Comparator.comparingInt(TrainingSessionExercise::getPosition)
                        .thenComparing(TrainingSessionExercise::getId)).map(this::toSessionExerciseResponse).toList());
    }

    private TrainingSessionSummaryResponse toSessionSummaryResponse(TrainingSession session) {
        return new TrainingSessionSummaryResponse(session.getId(), session.getSessionDate(), session.getModule(),
                idOf(session.getSourcePreset()), idOf(session.getSourceTrainingDay()), session.getTitle(), session.getStatus(),
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
                trainingSet.getWeightKg(), trainingSet.getCompleted(), trainingSet.getNotes(),
                trainingSet.getCreatedAt(), trainingSet.getUpdatedAt());
    }

    private TrainingCalendarDayResponse toCalendarDay(LocalDate date, List<TrainingSession> daySessions) {
        long started = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.STARTED).count();
        long completed = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.COMPLETED).count();
        long cancelled = daySessions.stream().filter(session -> session.getStatus() == TrainingSessionStatus.CANCELLED).count();
        long duration = daySessions.stream().map(TrainingSession::getDurationMinutes).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
        List<TrainingModule> modules = daySessions.stream().map(TrainingSession::getModule)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TrainingModule.class))).stream().sorted().toList();
        List<TrainingCalendarSessionResponse> sessions = daySessions.stream().map(session -> new TrainingCalendarSessionResponse(
                session.getId(), session.getModule(), session.getTitle(), session.getStatus(),
                idOf(session.getSourcePreset()), idOf(session.getSourceTrainingDay()))).toList();
        return new TrainingCalendarDayResponse(date, daySessions.size(), started, completed, cancelled, duration, modules, sessions);
    }

    private List<TrainingDay> liveDays(TrainingPreset preset) {
        return preset.getDays().stream().filter(day -> day.getDeletedAt() == null)
                .sorted(Comparator.comparingInt(TrainingDay::getPosition).thenComparing(TrainingDay::getId)).toList();
    }

    private List<TrainingPresetExercise> livePresetExercises(TrainingPreset preset) {
        return preset.getDays().stream().filter(day -> day.getDeletedAt() == null)
                .flatMap(day -> livePresetExercises(day).stream()).toList();
    }

    private List<TrainingPresetExercise> livePresetExercises(TrainingDay day) {
        return day.getExercises().stream().filter(exercise -> exercise.getDeletedAt() == null)
                .sorted(Comparator.comparingInt(TrainingPresetExercise::getPosition)
                        .thenComparing(TrainingPresetExercise::getId)).toList();
    }

    private <T> List<T> ordered(List<T> current, List<Long> requestedIds, String label) {
        Map<Long, T> byId = new LinkedHashMap<>();
        for (T item : current) {
            Long id = item instanceof TrainingDay day ? day.getId() : ((TrainingPresetExercise) item).getId();
            byId.put(id, item);
        }
        if (byId.size() != requestedIds.size() || requestedIds.stream().distinct().count() != requestedIds.size()
                || !byId.keySet().containsAll(requestedIds)) {
            throw new BadRequestException("El orden debe incluir exactamente todos los " + label + ".");
        }
        return requestedIds.stream().map(byId::get).toList();
    }

    private void touch(TrainingPreset preset) {
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
        if (entity instanceof TrainingPreset preset) return preset.getId();
        if (entity instanceof TrainingDay day) return day.getId();
        return ((TrainingExercise) entity).getId();
    }

    private static String normalized(String value) {
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record SessionSource(TrainingPreset preset, TrainingDay trainingDay) {
    }
}
