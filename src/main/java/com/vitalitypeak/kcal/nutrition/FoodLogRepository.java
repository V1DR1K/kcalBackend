package com.vitalitypeak.kcal.nutrition;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vitalitypeak.kcal.user.AppUser;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {
    List<FoodLog> findByUserAndLogDate(AppUser user, LocalDate logDate);

    List<FoodLog> findByUserAndLogDateBetween(AppUser user, LocalDate start, LocalDate end);
}
