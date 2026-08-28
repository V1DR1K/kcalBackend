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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_set")
@Getter
@Setter
@NoArgsConstructor
public class TrainingSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_exercise_id", nullable = false)
    private TrainingSessionExercise sessionExercise;

    @Column(nullable = false)
    private int setNumber;

    private Integer repetitions;

    private BigDecimal weightKg;

    private Integer seconds;

    @Column(name = "distance_meters", precision = 12, scale = 3)
    private BigDecimal distanceMeters;

    @Column(nullable = false)
    private boolean completed;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
