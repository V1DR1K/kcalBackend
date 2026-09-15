package com.scalegrams.training;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.training.TrainingDtos.DuplicateTrainingPlanRequest;
import com.scalegrams.training.TrainingDtos.PageResponse;
import com.scalegrams.training.TrainingDtos.SkipTrainingPlanSessionRequest;
import com.scalegrams.training.TrainingModule;
import com.scalegrams.training.TrainingDtos.TrainingPlanDetailResponse;
import com.scalegrams.training.TrainingDtos.TrainingPlanResolutionResponse;
import com.scalegrams.training.TrainingDtos.TrainingPlanResponse;
import com.scalegrams.training.TrainingDtos.TrainingSessionResponse;
import com.scalegrams.training.TrainingDtos.UpsertTrainingPlanRequest;
import com.scalegrams.user.AppUser;

/** Canonical /plans application boundary. Legacy /presets remains on TrainingService temporarily. */
@Service
public class TrainingPlanService {
    private final TrainingService trainingService;

    public TrainingPlanService(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainingPlanResponse> search(AppUser user, TrainingModule module, boolean includeInactive,
            int page, int size) {
        return trainingService.plans(user, module, includeInactive, page, size);
    }

    @Transactional(readOnly = true)
    public TrainingPlanDetailResponse find(AppUser user, Long id) {
        return trainingService.plan(user, id);
    }

    @Transactional
    public TrainingPlanDetailResponse create(AppUser user, UpsertTrainingPlanRequest request) {
        return trainingService.createPlan(user, request);
    }

    @Transactional
    public TrainingPlanDetailResponse update(AppUser user, Long id, UpsertTrainingPlanRequest request) {
        return trainingService.updatePlan(user, id, request);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        trainingService.deletePreset(user, id);
    }

    @Transactional
    public TrainingPlanDetailResponse duplicate(AppUser user, Long id, DuplicateTrainingPlanRequest request) {
        return trainingService.duplicatePlan(user, id, request);
    }

    @Transactional(readOnly = true)
    public TrainingPlanResolutionResponse resolve(AppUser user, Long id, LocalDate date) {
        return trainingService.resolvePlan(user, id, date);
    }

    @Transactional
    public TrainingSessionResponse skipSession(AppUser user, Long id, SkipTrainingPlanSessionRequest request) {
        return trainingService.skipPlanSession(user, id, request);
    }
}
