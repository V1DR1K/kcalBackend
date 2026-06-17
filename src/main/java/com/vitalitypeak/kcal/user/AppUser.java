package com.vitalitypeak.kcal.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel = ActivityLevel.MODERATELY_ACTIVE;

    @Enumerated(EnumType.STRING)
    private FitnessGoal goal = FitnessGoal.MAINTAIN;

    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal targetWeightKg;
    private LocalDate birthDate;
    private Integer dailyCalorieGoal = 2200;
    private Integer proteinGoalGrams = 165;
    private Integer carbsGoalGrams = 220;
    private Integer fatGoalGrams = 75;
    private BigDecimal waterGoalLiters = BigDecimal.valueOf(3);
    private String planName = "Premium";
    private String nutritionStyle = "Balanceado";
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
