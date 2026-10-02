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
@Table(name = "training_cardio_service_event")
@Getter
@Setter
@NoArgsConstructor
public class TrainingCardioServiceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrainingEquipment equipment;

    @Column(name = "serviced_at", nullable = false)
    private OffsetDateTime servicedAt;

    @Column(length = 2000)
    private String notes;

    @jakarta.persistence.Version
    @Column(nullable = false)
    private Long version = 0L;

    private OffsetDateTime updatedAt = OffsetDateTime.now();
    private OffsetDateTime annulledAt;
    private Long annulledByUserId;
    @Column(length = 2000)
    private String annulmentReason;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
