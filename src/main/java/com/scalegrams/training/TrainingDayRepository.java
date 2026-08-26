package com.scalegrams.training;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingDayRepository extends JpaRepository<TrainingDay, Long> {
    @Query("""
            select day from TrainingDay day
            join day.preset preset
            where day.id = :id
              and preset.owner = :owner
            """)
    Optional<TrainingDay> findByIdAndOwner(@Param("id") Long id, @Param("owner") AppUser owner);

    @Query("""
            select distinct day from TrainingDay day
            join fetch day.preset preset
            left join fetch day.exercises presetExercise
            left join fetch presetExercise.exercise
            where day.id = :id
              and preset.owner = :owner
              and preset.deletedAt is null
              and day.deletedAt is null
            """)
    Optional<TrainingDay> findDetailByIdAndOwner(@Param("id") Long id, @Param("owner") AppUser owner);
}
