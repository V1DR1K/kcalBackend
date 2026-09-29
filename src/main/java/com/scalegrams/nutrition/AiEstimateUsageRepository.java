package com.scalegrams.nutrition;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.scalegrams.user.AppUser;

public interface AiEstimateUsageRepository extends JpaRepository<AiEstimateUsage, Long> {
    Optional<AiEstimateUsage> findByUserAndUsageDate(AppUser user, LocalDate usageDate);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ai_estimate_usage (user_id, usage_date, used_count)
            VALUES (:userId, :usageDate, 1)
            ON CONFLICT (user_id, usage_date) DO UPDATE
               SET used_count = ai_estimate_usage.used_count + 1
             WHERE (ai_estimate_usage.blocked_until IS NULL OR ai_estimate_usage.blocked_until <= :now)
               AND (:dailyLimit <= 0 OR ai_estimate_usage.used_count < :dailyLimit)
            """, nativeQuery = true)
    int reserve(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
            @Param("now") java.time.OffsetDateTime now, @Param("dailyLimit") int dailyLimit);

    @Modifying
    @Transactional
    @Query(value = "UPDATE ai_estimate_usage SET used_count = GREATEST(0, used_count - 1) WHERE user_id = :userId AND usage_date = :usageDate", nativeQuery = true)
    int release(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Modifying
    @Transactional
    @Query(value = "UPDATE ai_estimate_usage SET blocked_until = :blockedUntil, provider_status = :providerStatus WHERE user_id = :userId AND usage_date = :usageDate", nativeQuery = true)
    int updateProviderState(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
            @Param("blockedUntil") java.time.OffsetDateTime blockedUntil,
            @Param("providerStatus") String providerStatus);
}
