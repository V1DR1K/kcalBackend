package com.vitalitypeak.kcal.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.vitalitypeak.kcal.catalog.Food;
import com.vitalitypeak.kcal.catalog.FoodUnit;
import com.vitalitypeak.kcal.recipe.Recipe;
import com.vitalitypeak.kcal.user.AppUser;

import jakarta.persistence.Entity;
import jakarta.persistence.CascadeType;
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
    private Integer calories;
    private BigDecimal proteinGrams;
    private BigDecimal carbsGrams;
    private BigDecimal fatGrams;
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
