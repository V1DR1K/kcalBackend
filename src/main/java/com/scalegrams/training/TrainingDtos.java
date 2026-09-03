package com.scalegrams.training;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class TrainingDtos {
    public record TrainingModuleResponse(TrainingModule code, String label) {
    }

    public record UpsertCardioRecordRequest(@NotNull OffsetDateTime recordedAt,
            @NotNull @PositiveOrZero BigDecimal distanceKm, @NotNull @Positive Integer durationMinutes,
            boolean inclined, TrainingEquipment equipment) {
    }

    public record CardioRecordResponse(Long id, TrainingEquipment equipment, OffsetDateTime recordedAt,
            BigDecimal distanceKm, int durationMinutes, boolean inclined, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record CreateCardioServiceRequest(@NotNull TrainingEquipment equipment, @NotNull OffsetDateTime servicedAt,
            @Size(max = 2000) String notes) {
    }

    public record CardioServiceResponse(Long id, TrainingEquipment equipment, OffsetDateTime servicedAt, String notes,
            OffsetDateTime createdAt) {
    }

    public record CardioSummaryResponse(TrainingEquipment equipment, int thresholdMinutes, long totalDurationMinutes,
            long remainingMinutes, boolean due, CardioServiceResponse latestService) {
    }

    public record TrainingCategoryResponse(Long id, String name, TrainingModule module, boolean system,
            boolean editable, boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages,
            boolean hasNext) {
    }

    public record UpsertExerciseRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @Size(max = 80) String category,
            @NotNull TrainingModule module,
            Boolean active,
            Boolean global,
            Long categoryId,
            @Size(max = 80) String code,
            @Size(max = 80) @JsonDeserialize(using = StringOrStringListDeserializer.class) List<String> primaryMuscles,
            @Size(max = 80) @JsonDeserialize(using = StringOrStringListDeserializer.class) List<String> secondaryMuscles,
            TrainingEquipment equipment,
            TrainingDifficulty difficulty,
            TrainingRegistrationType registrationType,
            Boolean unilateral,
            Boolean externalLoad) {
    }

    public record TrainingExerciseResponse(Long id, String name, String description, String category,
            TrainingModule module, boolean global, boolean editable, boolean active, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, Long categoryId, String normalizedName, String code,
            List<String> primaryMuscles, List<String> secondaryMuscles, TrainingEquipment equipment,
            TrainingDifficulty difficulty, TrainingRegistrationType registrationType, boolean unilateral,
            boolean externalLoad, boolean systemExercise) {
    }

    public record UpsertTrainingCategoryRequest(@NotBlank @Size(max = 80) String name,
            @NotNull TrainingModule module, Boolean active) {
    }

    public record LegacyPlanRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull TrainingModule module,
            Boolean active,
            Long version) {
    }

    public record DuplicateTrainingPlanRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record LegacyTrainingPlanResponse(Long id, String name, String description, TrainingModule module,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
    }

    public record LegacyTrainingPlanDetailResponse(Long id, String name, String description, TrainingModule module,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version,
            List<LegacyPlanDayResponse> days) {
    }

    public record LegacyPlanDayRequest(
            @Size(max = 120) String name,
            @Size(max = 1000) String description,
            DayOfWeek dayOfWeek,
            Boolean active) {
    }

    public record LegacyPlanDayResponse(Long id, String name, String description, DayOfWeek dayOfWeek, int position, boolean active,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, List<LegacyPlanExerciseResponse> exercises) {
    }

    public record LegacyPlanExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @NotNull @PositiveOrZero Integer targetSets,
            @PositiveOrZero Integer targetRepetitions,
            @PositiveOrZero BigDecimal targetWeightKg,
            @Size(max = 1000) String notes,
            Boolean active,
            TrainingRegistrationType registrationType,
            @PositiveOrZero Integer targetSeconds,
            @PositiveOrZero BigDecimal targetDistanceMeters) {
    }

    public record LegacyPlanExerciseResponse(Long id, Long exerciseId, String exerciseName, Integer targetSets,
            Integer targetRepetitions, BigDecimal targetWeightKg, String notes, int position, boolean active,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, TrainingRegistrationType registrationType,
            Integer targetSeconds, BigDecimal targetDistanceMeters) {
    }

    public record ReorderRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> ids) {
    }

    public record TrainingSetRequest(
            @NotNull @Positive Integer setNumber,
            @PositiveOrZero Integer repetitions,
            @PositiveOrZero BigDecimal weightKg,
            boolean completed,
            @Size(max = 1000) String notes,
            @PositiveOrZero Integer seconds,
            @PositiveOrZero BigDecimal distanceMeters) {
    }

    public record TrainingSessionExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @PositiveOrZero Integer targetSets,
            @PositiveOrZero Integer targetRepetitions,
            @PositiveOrZero BigDecimal targetWeightKg,
            @Size(max = 1000) String notes,
            @Size(max = 100) List<@Valid TrainingSetRequest> sets,
            TrainingRegistrationType registrationType,
            @PositiveOrZero Integer targetSeconds,
            @PositiveOrZero BigDecimal targetDistanceMeters,
            @Positive Long id,
            @PositiveOrZero Integer position) {
    }

    public record CreateTrainingSessionRequest(
            @NotNull LocalDate date,
            @NotNull TrainingModule module,
            @Positive @com.fasterxml.jackson.annotation.JsonAlias("presetId") Long planId,
            @Positive @com.fasterxml.jackson.annotation.JsonAlias("trainingDayId") Long planDayId,
            @Size(max = 160) String title,
            TrainingSessionStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            @PositiveOrZero Integer durationMinutes,
            @Size(max = 2000) String notes,
            @Size(max = 100) List<@Valid TrainingSessionExerciseRequest> exercises) {
    }

    public record UpdateTrainingSessionRequest(
            @NotNull LocalDate date,
            @NotNull TrainingModule module,
            @Positive @com.fasterxml.jackson.annotation.JsonAlias("presetId") Long planId,
            @Positive @com.fasterxml.jackson.annotation.JsonAlias("trainingDayId") Long planDayId,
            @Size(max = 160) String title,
            @NotNull TrainingSessionStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            @PositiveOrZero Integer durationMinutes,
            @Size(max = 2000) String notes,
            @Size(max = 100) List<@Valid TrainingSessionExerciseRequest> exercises,
            @NotNull Long version) {
    }

    public record CompleteTrainingSessionRequest(OffsetDateTime finishedAt,
            @PositiveOrZero Integer durationMinutes, @NotNull Long version, Boolean persistPlanChanges) {
    }

    public record CancelTrainingSessionRequest(@Size(max = 2000) String notes, @NotNull Long version) {
    }

    public record TrainingSetResponse(Long id, int setNumber, Integer repetitions, BigDecimal weightKg, boolean completed,
            String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt, Integer seconds,
            BigDecimal distanceMeters) {
    }

    public record TrainingSessionExerciseResponse(Long id, Long exerciseId, String exerciseName, Integer targetSets,
            Integer targetRepetitions, BigDecimal targetWeightKg, String notes, int position,
            List<TrainingSetResponse> sets, TrainingRegistrationType registrationType, Integer targetSeconds,
            BigDecimal targetDistanceMeters, Long sourcePlanExerciseId, TrainingSessionExerciseOrigin origin) {
    }

    public record TrainingSessionResponse(Long id, LocalDate date, TrainingModule module, Long planId,
            Long planDayId, String title, TrainingSessionStatus status, OffsetDateTime startedAt,
            OffsetDateTime finishedAt, Integer durationMinutes, String notes, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, Long version, List<TrainingSessionExerciseResponse> exercises) {
    }

    public record TrainingSessionSummaryResponse(Long id, LocalDate date, TrainingModule module, Long planId,
            Long planDayId, String title, TrainingSessionStatus status, OffsetDateTime startedAt,
            OffsetDateTime finishedAt, Integer durationMinutes, Long version) {
    }

    public record TrainingCalendarSessionResponse(Long id, TrainingModule module, String title,
            TrainingSessionStatus status, Long planId, Long planDayId, Long version) {
    }

    public record TrainingCalendarDayResponse(LocalDate date, long sessionCount, long inProgressCount,
            long completedCount, long cancelledCount, long durationMinutes, List<TrainingModule> modules,
            List<TrainingCalendarSessionResponse> sessions, List<TrainingPlanScheduleResponse> plannedPlans) {
    }

    public record TrainingPlanScheduleResponse(Long planId, Long planDayId, String planDayName,
            TrainingModule module, boolean recommended, Long sessionId, TrainingSessionStatus sessionStatus) {
    }

    public record WeeklyTrainingSummaryResponse(long sessionCount, long totalMinutes, long totalSets) {
    }

    public record TrainingDashboardResponse(LocalDate date, List<TrainingPlanResponse> plans,
            TrainingSessionSummaryResponse recentSession, WeeklyTrainingSummaryResponse weeklySummary,
            List<TrainingExerciseResponse> exercises, List<TrainingPlanScheduleResponse> plannedPlans) {
    }

    public record PlanExerciseRequest(@NotNull @Positive Long exerciseId,
            @PositiveOrZero Integer targetSets, @PositiveOrZero Integer targetRepetitions,
            @PositiveOrZero BigDecimal targetWeightKg, @Size(max = 1000) String notes,
            @PositiveOrZero Integer position, TrainingRegistrationType registrationType,
            @PositiveOrZero Integer targetSeconds, @PositiveOrZero BigDecimal targetDistanceMeters) {
    }

    public record PlanDayRequest(@NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description, DayOfWeek dayOfWeek,
            @NotEmpty @Size(max = 100) List<@Valid PlanExerciseRequest> exercises,
            @PositiveOrZero Integer position) {
    }

    public record UpsertTrainingPlanRequest(@NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description, @NotNull TrainingModule module,
            @NotNull TrainingFrequencyMode frequencyMode, @NotNull @Positive Integer targetSessionsPerWeek,
            @NotNull LocalDate startDate, LocalDate endDate, Boolean active,
            @NotEmpty @Size(max = 100) List<@Valid PlanDayRequest> days, Long version) {
    }

    public record TrainingPlanResponse(Long id, String name, String description, TrainingModule module,
            TrainingFrequencyMode frequencyMode, int targetSessionsPerWeek, LocalDate startDate, LocalDate endDate,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {
    }

    public record TrainingPlanDetailResponse(Long id, String name, String description, TrainingModule module,
            TrainingFrequencyMode frequencyMode, int targetSessionsPerWeek, LocalDate startDate, LocalDate endDate,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version,
            List<TrainingPlanDayResponse> days) {
    }

    public record TrainingPlanDayResponse(Long id, String name, String description, DayOfWeek dayOfWeek,
            int position, boolean active, List<TrainingPlanExerciseResponse> exercises) {
    }

    public record TrainingPlanExerciseResponse(Long id, Long exerciseId, String exerciseName, Integer targetSets,
            Integer targetRepetitions, BigDecimal targetWeightKg, String notes, int position, boolean active,
            TrainingRegistrationType registrationType, Integer targetSeconds, BigDecimal targetDistanceMeters) {
    }

    public record TrainingPlanResolutionResponse(LocalDate date, boolean scheduled, Long planId, Long planDayId,
            String planDayName, TrainingModule module, TrainingFrequencyMode frequencyMode,
            TrainingSessionSummaryResponse session) {
    }

    public record SkipTrainingPlanSessionRequest(@NotNull LocalDate date, @Positive Long planDayId,
            @Size(max = 2000) String notes) {
    }
}
