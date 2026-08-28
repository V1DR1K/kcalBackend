package com.scalegrams.training;

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
@Table(name = "training_preset")
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_mode", nullable = false, length = 12)
    private TrainingFrequencyMode frequencyMode = TrainingFrequencyMode.FIXED;

    @Column(name = "target_sessions_per_week", nullable = false)
    private int targetSessionsPerWeek;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

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

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<TrainingPlanDay> days = new ArrayList<>();
}
