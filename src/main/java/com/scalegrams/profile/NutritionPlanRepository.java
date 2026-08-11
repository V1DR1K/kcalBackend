package com.scalegrams.profile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface NutritionPlanRepository extends JpaRepository<NutritionPlan, Long> {
    List<NutritionPlan> findByUserAndActiveTrueOrderByStartDateDescIdDesc(AppUser user);

    Optional<NutritionPlan> findByIdAndUserAndActiveTrue(Long id, AppUser user);

    default Optional<NutritionPlan> findActiveForUserAndDate(AppUser user, LocalDate date) {
        return findActiveForUserAndDate(user, date, Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("""
            select plan from NutritionPlan plan
            where plan.user = :user
              and plan.active = true
              and plan.startDate <= :date
              and (plan.endDate is null or plan.endDate >= :date)
            order by plan.startDate desc, plan.id desc
            """)
    List<NutritionPlan> findActiveForUserAndDate(@Param("user") AppUser user, @Param("date") LocalDate date, Pageable pageable);
}
