package com.scalegrams.training;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPlanExerciseRepository extends JpaRepository<TrainingPlanExercise, Long> {
    boolean existsByExerciseAndDeletedAtIsNull(TrainingExercise exercise);
}
