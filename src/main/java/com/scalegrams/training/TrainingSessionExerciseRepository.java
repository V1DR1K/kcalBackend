package com.scalegrams.training;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionExerciseRepository extends JpaRepository<TrainingSessionExercise, Long> {
    @Query("""
            select distinct sessionExercise from TrainingSessionExercise sessionExercise
            left join fetch sessionExercise.sets
            left join fetch sessionExercise.sourceExercise
            where sessionExercise.session.id = :sessionId
            """)
    List<TrainingSessionExercise> findAllWithSetsBySessionId(@Param("sessionId") Long sessionId);

    @Query("""
            select count(trainingSet) from TrainingSet trainingSet
            join trainingSet.sessionExercise sessionExercise
            join sessionExercise.session session
            where session.user = :user
              and session.sessionDate >= :fromDate
              and session.sessionDate <= :toDate
            """)
    long countSetsForUserAndDateRange(@Param("user") com.scalegrams.user.AppUser user,
            @Param("fromDate") java.time.LocalDate fromDate, @Param("toDate") java.time.LocalDate toDate);
}
