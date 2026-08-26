package com.scalegrams.training;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingPlanDayRepository extends JpaRepository<TrainingPlanDay, Long> {
    @Query("""
            select day from TrainingPlanDay day
            join day.plan plan
            where day.id = :id
              and plan.owner = :owner
            """)
    Optional<TrainingPlanDay> findByIdAndOwner(@Param("id") Long id, @Param("owner") AppUser owner);

    @Query("""
            select distinct day from TrainingPlanDay day
            join fetch day.plan plan
            left join fetch day.exercises planExercise
            left join fetch planExercise.exercise
            where day.id = :id
              and plan.owner = :owner
              and plan.deletedAt is null
              and day.deletedAt is null
            """)
    Optional<TrainingPlanDay> findDetailByIdAndOwner(@Param("id") Long id, @Param("owner") AppUser owner);
}
