package com.scalegrams.training;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPresetExerciseRepository extends JpaRepository<TrainingPresetExercise, Long> {
    boolean existsByExerciseAndDeletedAtIsNull(TrainingExercise exercise);
}
