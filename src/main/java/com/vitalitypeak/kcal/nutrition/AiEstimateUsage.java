package com.vitalitypeak.kcal.nutrition;

import java.time.LocalDate;

import com.vitalitypeak.kcal.user.AppUser;

import jakarta.persistence.Entity;
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
@Table(name = "ai_estimate_usage", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "usage_date" }))
@Getter
@Setter
@NoArgsConstructor
public class AiEstimateUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private AppUser user;

    private LocalDate usageDate;
    private int usedCount;
}
