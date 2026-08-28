package com.scalegrams.training;

import java.math.BigDecimal;
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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_preset_exercise")
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlanExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_day_id", nullable = false)
    private TrainingPlanDay planDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private TrainingExercise exercise;

    private Integer targetSets;

    private Integer targetRepetitions;

    private BigDecimal targetWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false, length = 35)
    private TrainingRegistrationType registrationType = TrainingRegistrationType.REPETITIONS;

    @Column(name = "target_seconds")
    private Integer targetSeconds;

    @Column(name = "target_distance_meters", precision = 12, scale = 3)
    private BigDecimal targetDistanceMeters;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private Long version;

    private OffsetDateTime deletedAt;
}
