package com.scalegrams.training;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scalegrams.common.CurrentUser;
import com.scalegrams.training.TrainingDtos.CompleteTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.CreateTrainingSessionRequest;
import com.scalegrams.training.TrainingDtos.DuplicatePresetRequest;
import com.scalegrams.training.TrainingDtos.PageResponse;
import com.scalegrams.training.TrainingDtos.ReorderRequest;
import com.scalegrams.training.TrainingDtos.TrainingCalendarDayResponse;
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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/training")
public class TrainingController {
    private final TrainingService trainingService;
    private final CurrentUser currentUser;

    public TrainingController(TrainingService trainingService, CurrentUser currentUser) {
        this.trainingService = trainingService;
        this.currentUser = currentUser;
    }

    @GetMapping("/modules")
    List<TrainingModuleResponse> modules() {
        return trainingService.modules();
    }

    @GetMapping("/exercises")
    PageResponse<TrainingExerciseResponse> exercises(Authentication authentication,
            @RequestParam(required = false) String q, @RequestParam(required = false) TrainingModule module,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return trainingService.exercises(currentUser.from(authentication), q, module, page, size);
    }

    @PostMapping("/exercises")
    TrainingExerciseResponse createExercise(Authentication authentication,
            @Valid @RequestBody UpsertExerciseRequest request) {
        return trainingService.createExercise(currentUser.from(authentication), request);
    }

    @PutMapping("/exercises/{id}")
    TrainingExerciseResponse updateExercise(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpsertExerciseRequest request) {
        return trainingService.updateExercise(currentUser.from(authentication), id, request);
    }

    @DeleteMapping("/exercises/{id}")
    ResponseEntity<Void> deleteExercise(Authentication authentication, @PathVariable Long id) {
        trainingService.deleteExercise(currentUser.from(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/presets")
    PageResponse<TrainingPresetResponse> presets(Authentication authentication,
            @RequestParam(required = false) TrainingModule module,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return trainingService.presets(currentUser.from(authentication), module, includeInactive, page, size);
    }

    @GetMapping("/presets/{id}")
    TrainingPresetDetailResponse preset(Authentication authentication, @PathVariable Long id) {
        return trainingService.preset(currentUser.from(authentication), id);
    }

    @PostMapping("/presets")
    TrainingPresetDetailResponse createPreset(Authentication authentication, @Valid @RequestBody UpsertPresetRequest request) {
        return trainingService.createPreset(currentUser.from(authentication), request);
    }

    @PutMapping("/presets/{id}")
    TrainingPresetDetailResponse updatePreset(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpsertPresetRequest request) {
        return trainingService.updatePreset(currentUser.from(authentication), id, request);
    }

    @DeleteMapping("/presets/{id}")
    ResponseEntity<Void> deletePreset(Authentication authentication, @PathVariable Long id) {
        trainingService.deletePreset(currentUser.from(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/presets/{id}/duplicate")
    TrainingPresetDetailResponse duplicatePreset(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody DuplicatePresetRequest request) {
        return trainingService.duplicatePreset(currentUser.from(authentication), id, request);
    }

    @PostMapping("/presets/{presetId}/days")
    TrainingDayResponse createDay(Authentication authentication, @PathVariable Long presetId,
            @Valid @RequestBody UpsertTrainingDayRequest request) {
        return trainingService.createDay(currentUser.from(authentication), presetId, request);
    }

    @PutMapping("/presets/{presetId}/days/{dayId}")
    TrainingDayResponse updateDay(Authentication authentication, @PathVariable Long presetId, @PathVariable Long dayId,
            @Valid @RequestBody UpsertTrainingDayRequest request) {
        return trainingService.updateDay(currentUser.from(authentication), presetId, dayId, request);
    }

    @DeleteMapping("/presets/{presetId}/days/{dayId}")
    ResponseEntity<Void> deleteDay(Authentication authentication, @PathVariable Long presetId, @PathVariable Long dayId) {
        trainingService.deleteDay(currentUser.from(authentication), presetId, dayId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/presets/{presetId}/days/reorder")
    List<TrainingDayResponse> reorderDays(Authentication authentication, @PathVariable Long presetId,
            @Valid @RequestBody ReorderRequest request) {
        return trainingService.reorderDays(currentUser.from(authentication), presetId, request);
    }

    @PostMapping("/presets/{presetId}/days/{dayId}/exercises")
    TrainingPresetExerciseResponse createPresetExercise(Authentication authentication, @PathVariable Long presetId,
            @PathVariable Long dayId, @Valid @RequestBody UpsertPresetExerciseRequest request) {
        return trainingService.createPresetExercise(currentUser.from(authentication), presetId, dayId, request);
    }

    @PutMapping("/presets/{presetId}/days/{dayId}/exercises/{presetExerciseId}")
    TrainingPresetExerciseResponse updatePresetExercise(Authentication authentication, @PathVariable Long presetId,
            @PathVariable Long dayId, @PathVariable Long presetExerciseId,
            @Valid @RequestBody UpsertPresetExerciseRequest request) {
        return trainingService.updatePresetExercise(currentUser.from(authentication), presetId, dayId,
                presetExerciseId, request);
    }

    @DeleteMapping("/presets/{presetId}/days/{dayId}/exercises/{presetExerciseId}")
    ResponseEntity<Void> deletePresetExercise(Authentication authentication, @PathVariable Long presetId,
            @PathVariable Long dayId, @PathVariable Long presetExerciseId) {
        trainingService.deletePresetExercise(currentUser.from(authentication), presetId, dayId, presetExerciseId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/presets/{presetId}/days/{dayId}/exercises/reorder")
    List<TrainingPresetExerciseResponse> reorderPresetExercises(Authentication authentication,
            @PathVariable Long presetId, @PathVariable Long dayId, @Valid @RequestBody ReorderRequest request) {
        return trainingService.reorderPresetExercises(currentUser.from(authentication), presetId, dayId, request);
    }

    @GetMapping("/sessions")
    PageResponse<TrainingSessionSummaryResponse> sessions(Authentication authentication,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) LocalDate date, @RequestParam(required = false) TrainingModule module,
            @RequestParam(required = false) TrainingSessionStatus status,
            @RequestParam(required = false) Long presetId, @RequestParam(required = false) Long trainingDayId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return trainingService.sessions(currentUser.from(authentication), from, to, date, module, status, presetId,
                trainingDayId, page, size);
    }

    @GetMapping("/sessions/{id}")
    TrainingSessionResponse session(Authentication authentication, @PathVariable Long id) {
        return trainingService.session(currentUser.from(authentication), id);
    }

    @PostMapping("/sessions")
    TrainingSessionResponse createSession(Authentication authentication,
            @Valid @RequestBody CreateTrainingSessionRequest request) {
        return trainingService.createSession(currentUser.from(authentication), request);
    }

    @PutMapping("/sessions/{id}")
    TrainingSessionResponse updateSession(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody UpdateTrainingSessionRequest request) {
        return trainingService.updateSession(currentUser.from(authentication), id, request);
    }

    @PostMapping("/sessions/{id}/complete")
    TrainingSessionResponse completeSession(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody(required = false) CompleteTrainingSessionRequest request) {
        return trainingService.completeSession(currentUser.from(authentication), id, request);
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> deleteSession(Authentication authentication, @PathVariable Long id) {
        trainingService.deleteSession(currentUser.from(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calendar")
    List<TrainingCalendarDayResponse> calendar(Authentication authentication, @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return trainingService.calendar(currentUser.from(authentication), from, to);
    }

    @GetMapping("/dashboard")
    TrainingDashboardResponse dashboard(Authentication authentication, @RequestParam(required = false) LocalDate date) {
        return trainingService.dashboard(currentUser.from(authentication), date);
    }
}
