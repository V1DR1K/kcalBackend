package com.scalegrams.nutrition;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.scalegrams.user.AppUser;

public interface AiEstimateUsageRepository extends JpaRepository<AiEstimateUsage, Long> {
    Optional<AiEstimateUsage> findByUserAndUsageDate(AppUser user, LocalDate usageDate);
}
