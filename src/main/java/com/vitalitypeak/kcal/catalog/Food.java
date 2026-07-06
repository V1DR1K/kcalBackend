package com.vitalitypeak.kcal.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.vitalitypeak.kcal.user.AppUser;
import jakarta.persistence.ManyToOne;

@Entity
@BatchSize(size = 50)
@Getter
@Setter
@NoArgsConstructor
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(unique = true)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category = FoodCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodUnit baseUnit = FoodUnit.GRAM;

    private BigDecimal baseQuantity = BigDecimal.valueOf(100);
    private Integer calories;
    private BigDecimal proteinGrams;
    private BigDecimal carbsGrams;
    private BigDecimal fatGrams;
    @Enumerated(EnumType.STRING)
    private FoodPreparation preparation = FoodPreparation.UNSPECIFIED;
    private String preparationSource;
    private String preparationGroup;
    private String servingName;
    private BigDecimal servingWeightGrams;
    @Column(length = 500)
    private String imageUrl;
    @Column(length = 500)
    private String imageObjectKey;
    private String source = "LOCAL";
    private String sourceId;
    private OffsetDateTime lastSyncedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;
    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @CollectionTable(name = "food_tags", joinColumns = @JoinColumn(name = "food_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();
}
