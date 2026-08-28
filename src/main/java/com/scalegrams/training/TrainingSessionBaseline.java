package com.scalegrams.training;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_session_baseline")
@Getter
@Setter
@NoArgsConstructor
public class TrainingSessionBaseline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private TrainingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_exercise_id")
    private TrainingPlanExercise planExercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_exercise_id")
    private TrainingExercise catalogExercise;

    @Column(name = "exercise_name", nullable = false, length = 120)
    private String exerciseName;

    @Column(nullable = false)
    private int position;

    @Column(name = "plan_version", nullable = false)
    private Long planVersion;

    @Column(name = "plan_day_version", nullable = false)
    private Long planDayVersion;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
