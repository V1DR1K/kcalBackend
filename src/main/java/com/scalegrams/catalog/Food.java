package com.scalegrams.catalog;

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
import com.scalegrams.user.AppUser;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import com.scalegrams.nutrition.FoodNutrient;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.scalegrams.common.SearchTextNormalizer;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

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

    @Column(name = "search_name", nullable = false, columnDefinition = "text")
    private String searchName = "";

    @Column(name = "search_brand", nullable = false, columnDefinition = "text")
    private String searchBrand = "";

    @Column(name = "search_tags", nullable = false, columnDefinition = "text")
    private String searchTags = "";

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
    private BigDecimal cookedYieldFactor;
    @Enumerated(EnumType.STRING)
    private CookedYieldSource cookedYieldSource;
    @Column(length = 240)
    private String cookedYieldAssumption;
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
    private OffsetDateTime deletedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

    @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private java.util.List<FoodNutrient> nutrients = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @CollectionTable(name = "food_tags", joinColumns = @JoinColumn(name = "food_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();

    public void setName(String name) {
        this.name = name;
        refreshSearchIndex();
    }

    public void setBrand(String brand) {
        this.brand = brand;
        refreshSearchIndex();
    }

    public void setTags(Set<String> tags) {
        this.tags = tags == null ? new LinkedHashSet<>() : tags;
        refreshSearchIndex();
    }

    public void refreshSearchIndex() {
        this.searchName = SearchTextNormalizer.normalize(name);
        this.searchBrand = SearchTextNormalizer.normalize(brand);
        this.searchTags = tags == null ? "" : tags.stream()
                .map(SearchTextNormalizer::normalize)
                .filter(tag -> !tag.isBlank())
                .collect(Collectors.joining(" "));
    }

    @PrePersist
    @PreUpdate
    private void synchronizeSearchIndex() {
        refreshSearchIndex();
    }
}
