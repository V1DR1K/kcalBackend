package com.scalegrams.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.scalegrams.catalog.Food;
import com.scalegrams.catalog.FoodUnit;
import com.scalegrams.recipe.Recipe;
import com.scalegrams.user.AppUser;

import jakarta.persistence.Entity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FoodLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    private Food food;

    @ManyToOne(fetch = FetchType.LAZY)
    private Recipe recipe;

    @OneToMany(mappedBy = "foodLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodLogRecipeIngredient> recipeIngredients = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private MealItemType itemType = MealItemType.FOOD;

    @Enumerated(EnumType.STRING)
    private MealType mealType;

    @Enumerated(EnumType.STRING)
    private FoodUnit unit;

    private LocalDate logDate;
    private BigDecimal quantity;
    @Column(name = "recipe_raw_total_weight_grams")
    private BigDecimal recipeRawTotalWeightGrams;
    @Column(name = "recipe_cooked_total_weight_grams")
    private BigDecimal recipeCookedTotalWeightGrams;
    private Integer calories;
    private BigDecimal proteinGrams;
    private BigDecimal carbsGrams;
    private BigDecimal fatGrams;
    private String aiEstimateName;
    private Integer aiEstimateConfidence;
    @Column(columnDefinition = "TEXT")
    private String aiEstimateDetails;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "foodLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodLogNutrient> nutrientSnapshot = new ArrayList<>();
}
