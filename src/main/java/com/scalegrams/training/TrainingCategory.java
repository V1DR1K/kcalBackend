package com.scalegrams.training;

import java.time.OffsetDateTime;

import com.scalegrams.user.AppUser;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "training_category")
@Getter
@Setter
@NoArgsConstructor
public class TrainingCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingModule module;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 80)
    private String normalizedName;

    @Column(name = "system_category", nullable = false)
    private boolean systemCategory;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    private OffsetDateTime deletedAt;
}
