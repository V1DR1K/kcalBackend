package com.scalegrams.training;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.scalegrams.user.AppUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_session")
@Getter
@Setter
@NoArgsConstructor
public class TrainingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingModule module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_preset_id")
    private TrainingPlan sourcePlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_training_day_id")
    private TrainingPlanDay sourcePlanDay;

    @Column(length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingSessionStatus status = TrainingSessionStatus.IN_PROGRESS;

    @Version
    @Column(nullable = false)
    private Long version;

    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Integer durationMinutes;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "baseline_captured", nullable = false)
    private boolean baselineCaptured;

    @Column(name = "baseline_plan_version")
    private Long baselinePlanVersion;

    @Column(name = "baseline_plan_day_version")
    private Long baselinePlanDayVersion;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<TrainingSessionExercise> exercises = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<TrainingSessionBaseline> baseline = new ArrayList<>();
}
