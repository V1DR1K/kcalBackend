package com.scalegrams.nutrition;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.scalegrams.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class AiCapture {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiCaptureTarget targetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiCaptureStatus status = AiCaptureStatus.DRAFT;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String draftJson;

    @Column(columnDefinition = "TEXT")
    private String jevDecisionJson;

    private Long confirmedLogId;

    private Long confirmedFoodId;

    private Long confirmedRecipeId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

    @Version
    private long version;

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
