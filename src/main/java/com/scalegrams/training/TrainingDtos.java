package com.scalegrams.training;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages,
            boolean hasNext) {
    }

    public record UpsertExerciseRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @Size(max = 80) String category,
            @NotNull TrainingModule module,
            Boolean active) {
    }

    public record TrainingExerciseResponse(Long id, String name, String description, String category,
            TrainingModule module, boolean global, boolean editable, boolean active, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record LegacyPlanRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull TrainingModule module,
            Boolean active) {
    }

    public record DuplicateTrainingPlanRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record LegacyTrainingPlanResponse(Long id, String name, String description, TrainingModule module,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record LegacyTrainingPlanDetailResponse(Long id, String name, String description, TrainingModule module,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, List<LegacyPlanDayResponse> days) {
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
            @NotNull @PositiveOrZero Integer targetRepetitions,
            @PositiveOrZero BigDecimal targetWeightKg,
            @Size(max = 1000) String notes,
            Boolean active) {
    }

    public record LegacyPlanExerciseResponse(Long id, Long exerciseId, String exerciseName, int targetSets,
            int targetRepetitions, BigDecimal targetWeightKg, String notes, int position, boolean active,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record ReorderRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> ids) {
    }

    public record TrainingSetRequest(
            @NotNull @Positive Integer setNumber,
            @NotNull @PositiveOrZero Integer repetitions,
            @PositiveOrZero BigDecimal weightKg,
            boolean completed,
            @Size(max = 1000) String notes) {
    }

    public record TrainingSessionExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @PositiveOrZero Integer targetSets,
            @PositiveOrZero Integer targetRepetitions,
            @PositiveOrZero BigDecimal targetWeightKg,
            @Size(max = 1000) String notes,
            @Size(max = 100) List<@Valid TrainingSetRequest> sets) {
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
            @NotNull @Size(max = 100) List<@Valid TrainingSessionExerciseRequest> exercises) {
    }

    public record CompleteTrainingSessionRequest(OffsetDateTime finishedAt,
            @PositiveOrZero Integer durationMinutes) {
    }

    public record TrainingSetResponse(Long id, int setNumber, int repetitions, BigDecimal weightKg, boolean completed,
            String notes, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record TrainingSessionExerciseResponse(Long id, Long exerciseId, String exerciseName, Integer targetSets,
            Integer targetRepetitions, BigDecimal targetWeightKg, String notes, int position,
            List<TrainingSetResponse> sets) {
    }

    public record TrainingSessionResponse(Long id, LocalDate date, TrainingModule module, Long planId,
            Long planDayId, String title, TrainingSessionStatus status, OffsetDateTime startedAt,
            OffsetDateTime finishedAt, Integer durationMinutes, String notes, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, List<TrainingSessionExerciseResponse> exercises) {
    }

    public record TrainingSessionSummaryResponse(Long id, LocalDate date, TrainingModule module, Long planId,
            Long planDayId, String title, TrainingSessionStatus status, OffsetDateTime startedAt,
            OffsetDateTime finishedAt, Integer durationMinutes) {
    }

    public record TrainingCalendarSessionResponse(Long id, TrainingModule module, String title,
            TrainingSessionStatus status, Long planId, Long planDayId) {
    }

    public record TrainingCalendarDayResponse(LocalDate date, long sessionCount, long startedCount,
            long completedCount, long cancelledCount, long durationMinutes, List<TrainingModule> modules,
            List<TrainingCalendarSessionResponse> sessions, List<TrainingPlanScheduleResponse> plannedPlans) {
    }

    public record TrainingPlanScheduleResponse(Long planId, Long planDayId, String planDayName,
            TrainingModule module, boolean recommended) {
    }

    public record WeeklyTrainingSummaryResponse(long sessionCount, long totalMinutes, long totalSets) {
    }

    public record TrainingDashboardResponse(LocalDate date, List<TrainingPlanResponse> plans,
            TrainingSessionSummaryResponse recentSession, WeeklyTrainingSummaryResponse weeklySummary,
            List<TrainingExerciseResponse> exercises, List<TrainingPlanScheduleResponse> plannedPlans) {
    }

    public record PlanExerciseRequest(@NotNull @Positive Long exerciseId,
            @NotNull @PositiveOrZero Integer targetSets, @NotNull @PositiveOrZero Integer targetRepetitions,
            @PositiveOrZero BigDecimal targetWeightKg, @Size(max = 1000) String notes,
            @PositiveOrZero Integer position) {
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
            @NotEmpty @Size(max = 100) List<@Valid PlanDayRequest> days) {
    }

    public record TrainingPlanResponse(Long id, String name, String description, TrainingModule module,
            TrainingFrequencyMode frequencyMode, int targetSessionsPerWeek, LocalDate startDate, LocalDate endDate,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record TrainingPlanDetailResponse(Long id, String name, String description, TrainingModule module,
            TrainingFrequencyMode frequencyMode, int targetSessionsPerWeek, LocalDate startDate, LocalDate endDate,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, List<TrainingPlanDayResponse> days) {
    }

    public record TrainingPlanDayResponse(Long id, String name, String description, DayOfWeek dayOfWeek,
            int position, boolean active, List<TrainingPlanExerciseResponse> exercises) {
    }

    public record TrainingPlanExerciseResponse(Long id, Long exerciseId, String exerciseName, int targetSets,
            int targetRepetitions, BigDecimal targetWeightKg, String notes, int position, boolean active) {
    }

    public record TrainingPlanResolutionResponse(LocalDate date, boolean scheduled, Long planId, Long planDayId,
            String planDayName, TrainingModule module, TrainingFrequencyMode frequencyMode,
            TrainingSessionSummaryResponse session) {
    }

    public record SkipTrainingPlanSessionRequest(@NotNull LocalDate date, @Positive Long planDayId,
            @Size(max = 2000) String notes) {
    }
}
