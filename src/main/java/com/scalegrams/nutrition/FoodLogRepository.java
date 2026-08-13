package com.scalegrams.nutrition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {
    @EntityGraph(attributePaths = {"food", "food.tags", "recipe", "recipe.ingredients", "recipe.ingredients.food"})
    List<FoodLog> findByUserAndLogDate(AppUser user, LocalDate logDate);

    @EntityGraph(attributePaths = {"food", "food.tags", "recipe", "recipe.ingredients", "recipe.ingredients.food"})
    List<FoodLog> findByUserAndLogDateBetween(AppUser user, LocalDate start, LocalDate end);

    Optional<FoodLog> findByIdAndUser(Long id, AppUser user);

    List<FoodLog> findByUserAndMealTypeAndLogDate(AppUser user, MealType mealType, LocalDate logDate);

    boolean existsByRecipeId(Long recipeId);

    @Query("""
            select log.logDate as date,
                   coalesce(sum(log.proteinGrams), 0) as proteinGrams,
                   coalesce(sum(log.carbsGrams), 0) as carbsGrams,
                   coalesce(sum(log.fatGrams), 0) as fatGrams
            from FoodLog log
            where log.user = :user and log.logDate between :start and :end
            group by log.logDate
            order by log.logDate
            """)
    List<DayNutritionProjection> summarizeByDate(@Param("user") AppUser user,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    interface DayNutritionProjection {
        LocalDate getDate();
        java.math.BigDecimal getProteinGrams();
        java.math.BigDecimal getCarbsGrams();
        java.math.BigDecimal getFatGrams();
    }
}
