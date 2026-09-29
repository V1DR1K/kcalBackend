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

    @Query(value = """
            SELECT fl.id FROM food_log fl
            JOIN (
              SELECT log_date, meal_type FROM food_log
              WHERE user_id = :userId AND log_date BETWEEN :startDate AND :endDate
              GROUP BY log_date, meal_type
              ORDER BY log_date DESC, MAX(created_at) DESC, meal_type
              LIMIT :groupLimit
            ) recent ON recent.log_date = fl.log_date AND recent.meal_type = fl.meal_type
            WHERE fl.user_id = :userId
            ORDER BY fl.log_date DESC, fl.meal_type, fl.created_at DESC, fl.id DESC
            """, nativeQuery = true)
    List<Long> findRecentMealGroupLogIds(@Param("userId") Long userId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("groupLimit") int groupLimit);

    @EntityGraph(attributePaths = {"food", "food.tags", "recipe", "recipe.ingredients", "recipe.ingredients.food"})
    List<FoodLog> findByIdIn(List<Long> ids);

    Optional<FoodLog> findByIdAndUser(Long id, AppUser user);

    @EntityGraph(attributePaths = {"recipe", "recipeIngredients", "recipeIngredients.food"})
    Optional<FoodLog> findByIdAndUserAndItemType(Long id, AppUser user, MealItemType itemType);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select log from FoodLog log where log.id = :id and log.user = :user")
    Optional<FoodLog> findOwnedForCatalog(@Param("id") Long id, @Param("user") AppUser user);

    List<FoodLog> findByUserAndMealTypeAndLogDate(AppUser user, MealType mealType, LocalDate logDate);

    @EntityGraph(attributePaths = {"food", "food.tags", "recipe", "recipe.ingredients", "recipe.ingredients.food"})
    @Query("select log from FoodLog log where log.user = :user and log.mealType = :mealType and log.logDate = :logDate")
    List<FoodLog> findByUserAndMealTypeAndLogDateWithRecipeIngredients(@Param("user") AppUser user,
            @Param("mealType") MealType mealType, @Param("logDate") LocalDate logDate);

    @EntityGraph(attributePaths = {"food", "food.tags", "recipe", "recipeIngredients", "recipeIngredients.food"})
    @Query("select log from FoodLog log where log.user = :user and log.mealType = :mealType and log.logDate = :logDate")
    List<FoodLog> findByUserAndMealTypeAndLogDateWithAdjustedIngredients(@Param("user") AppUser user,
            @Param("mealType") MealType mealType, @Param("logDate") LocalDate logDate);

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
