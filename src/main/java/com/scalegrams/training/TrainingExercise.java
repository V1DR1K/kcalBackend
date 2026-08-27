package com.scalegrams.training;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import com.scalegrams.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "training_exercise")
@Getter
@Setter
@NoArgsConstructor
public class TrainingExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private TrainingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingModule module;

    @ElementCollection
    @CollectionTable(name = "training_exercise_primary_muscle", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "muscle", nullable = false, length = 80)
    private Set<String> primaryMuscles = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "training_exercise_secondary_muscle", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "muscle", nullable = false, length = 80)
    private Set<String> secondaryMuscles = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingEquipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingDifficulty difficulty = TrainingDifficulty.BEGINNER;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false, length = 35)
    private TrainingRegistrationType registrationType = TrainingRegistrationType.REPETITIONS;

    @Column(nullable = false)
    private boolean unilateral;

    @Column(name = "external_load", nullable = false)
    private boolean externalLoad;

    @Column(name = "system_exercise", nullable = false)
    private boolean systemExercise;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    private OffsetDateTime deletedAt;

    public boolean isGlobalExercise() {
        return systemExercise;
    }

    public void setGlobalExercise(boolean value) {
        this.systemExercise = value;
    }
}
