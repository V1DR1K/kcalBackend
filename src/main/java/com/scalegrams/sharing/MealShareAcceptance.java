package com.scalegrams.sharing;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.scalegrams.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meal_share_acceptance", uniqueConstraints = @UniqueConstraint(name = "uk_meal_share_acceptance_recipient", columnNames = { "share_id", "recipient_id" }))
@Getter
@Setter
@NoArgsConstructor
public class MealShareAcceptance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_id", nullable = false)
    private MealShare share;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "target_meal_type", nullable = false, length = 40)
    private String targetMealType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
