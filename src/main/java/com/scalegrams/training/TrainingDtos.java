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
            TrainingModule module, boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record UpsertPresetRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull TrainingModule module,
            Boolean active) {
    }

    public record DuplicatePresetRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record TrainingPresetResponse(Long id, String name, String description, TrainingModule module,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record TrainingPresetDetailResponse(Long id, String name, String description, TrainingModule module,
            boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt, List<TrainingDayResponse> days) {
    }

    public record UpsertTrainingDayRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            DayOfWeek dayOfWeek,
            Boolean active) {
    }

    public record TrainingDayResponse(Long id, String name, String description, DayOfWeek dayOfWeek, int position, boolean active,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, List<TrainingPresetExerciseResponse> exercises) {
    }

    public record UpsertPresetExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @NotNull @PositiveOrZero Integer targetSets,
            @NotNull @PositiveOrZero Integer targetRepetitions,
            @PositiveOrZero BigDecimal targetWeightKg,
            @Size(max = 1000) String notes,
            Boolean active) {
    }

    public record TrainingPresetExerciseResponse(Long id, Long exerciseId, String exerciseName, int targetSets,
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
            @Positive Long presetId,
            @Positive Long trainingDayId,
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
            @Positive Long presetId,
            @Positive Long trainingDayId,
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

    public record TrainingSessionResponse(Long id, LocalDate date, TrainingModule module, Long presetId,
            Long trainingDayId, String title, TrainingSessionStatus status, OffsetDateTime startedAt,
            OffsetDateTime finishedAt, Integer durationMinutes, String notes, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, List<TrainingSessionExerciseResponse> exercises) {
    }

    public record TrainingSessionSummaryResponse(Long id, LocalDate date, TrainingModule module, Long presetId,
            Long trainingDayId, String title, TrainingSessionStatus status, OffsetDateTime startedAt,
            OffsetDateTime finishedAt, Integer durationMinutes) {
    }

    public record TrainingCalendarSessionResponse(Long id, TrainingModule module, String title,
            TrainingSessionStatus status, Long presetId, Long trainingDayId) {
    }

    public record TrainingCalendarDayResponse(LocalDate date, long sessionCount, long startedCount,
            long completedCount, long cancelledCount, long durationMinutes, List<TrainingModule> modules,
            List<TrainingCalendarSessionResponse> sessions) {
    }

    public record WeeklyTrainingSummaryResponse(long sessionCount, long totalMinutes, long totalSets) {
    }

    public record TrainingDashboardResponse(LocalDate date, List<TrainingPresetResponse> routines,
            TrainingSessionSummaryResponse recentSession, WeeklyTrainingSummaryResponse weeklySummary,
            List<TrainingExerciseResponse> exercises) {
    }
}
