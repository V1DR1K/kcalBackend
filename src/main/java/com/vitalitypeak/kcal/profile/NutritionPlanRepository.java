package com.vitalitypeak.kcal.profile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vitalitypeak.kcal.user.AppUser;

public interface NutritionPlanRepository extends JpaRepository<NutritionPlan, Long> {
    List<NutritionPlan> findByUserOrderByStartDateDesc(AppUser user);

    Optional<NutritionPlan> findByIdAndUser(Long id, AppUser user);

    default Optional<NutritionPlan> findActiveForUserAndDate(AppUser user, LocalDate date) {
        return findActiveForUserAndDate(user, date, Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("""
            select plan from NutritionPlan plan
            where plan.user = :user
              and plan.startDate <= :date
              and (plan.endDate is null or plan.endDate >= :date)
            order by plan.startDate desc
            """)
    List<NutritionPlan> findActiveForUserAndDate(@Param("user") AppUser user, @Param("date") LocalDate date, Pageable pageable);

    @Query("""
            select count(plan) > 0 from NutritionPlan plan
            where plan.user = :user
              and (:ignoreId is null or plan.id <> :ignoreId)
              and plan.startDate <= :endDate
              and (plan.endDate is null or plan.endDate >= :startDate)
            """)
    boolean existsOverlappingPlanForUser(@Param("user") AppUser user, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("ignoreId") Long ignoreId);
}
