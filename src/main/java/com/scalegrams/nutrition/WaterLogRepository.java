package com.scalegrams.nutrition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface WaterLogRepository extends JpaRepository<WaterLog, Long> {
    List<WaterLog> findByUserAndLogDate(AppUser user, LocalDate logDate);

    @Query("select coalesce(sum(log.liters), 0) from WaterLog log where log.user = :user and log.logDate = :date")
    BigDecimal sumLitersByUserAndLogDate(@Param("user") AppUser user, @Param("date") LocalDate date);

    Optional<WaterLog> findFirstByUserAndLogDateOrderByCreatedAtDesc(AppUser user, LocalDate logDate);
}
