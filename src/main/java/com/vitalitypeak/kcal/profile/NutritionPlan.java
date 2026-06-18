package com.vitalitypeak.kcal.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.vitalitypeak.kcal.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(indexes = {
        @Index(name = "idx_nutrition_plan_user_start", columnList = "user_id,startDate"),
        @Index(name = "idx_nutrition_plan_user_end", columnList = "user_id,endDate")
})
@Getter
@Setter
@NoArgsConstructor
public class NutritionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Integer dailyCalories;

    @Column(nullable = false)
    private BigDecimal proteinPercent;

    @Column(nullable = false)
    private BigDecimal carbsPercent;

    @Column(nullable = false)
    private BigDecimal fatPercent;

    @Column(nullable = false)
    private Integer proteinGoalGrams;

    @Column(nullable = false)
    private Integer carbsGoalGrams;

    @Column(nullable = false)
    private Integer fatGoalGrams;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
