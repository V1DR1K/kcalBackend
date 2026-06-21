package com.vitalitypeak.kcal.nutrition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.vitalitypeak.kcal.user.AppUser;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {
    @EntityGraph(attributePaths = {"food", "food.tags", "recipe"})
    List<FoodLog> findByUserAndLogDate(AppUser user, LocalDate logDate);

    @EntityGraph(attributePaths = {"food", "food.tags", "recipe"})
    List<FoodLog> findByUserAndLogDateBetween(AppUser user, LocalDate start, LocalDate end);

    Optional<FoodLog> findByIdAndUser(Long id, AppUser user);
}
