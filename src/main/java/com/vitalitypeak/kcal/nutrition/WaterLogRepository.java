package com.vitalitypeak.kcal.nutrition;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vitalitypeak.kcal.user.AppUser;

public interface WaterLogRepository extends JpaRepository<WaterLog, Long> {
    List<WaterLog> findByUserAndLogDate(AppUser user, LocalDate logDate);
}
