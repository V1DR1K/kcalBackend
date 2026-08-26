package com.scalegrams.training;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    interface WeeklySessionAggregate {
        long getSessionCount();

        long getTotalMinutes();
    }

    @EntityGraph(attributePaths = {"sourcePreset", "sourceTrainingDay"})
    @EntityGraph(attributePaths = {"sourcePreset", "sourceTrainingDay"})
    @Query("""
            select session from TrainingSession session
            where session.user = :user
              and (:fromDate is null or session.sessionDate >= :fromDate)
              and (:toDate is null or session.sessionDate <= :toDate)
              and (:date is null or session.sessionDate = :date)
              and (:module is null or session.module = :module)
              and (:status is null or session.status = :status)
              and (:presetId is null or session.sourcePreset.id = :presetId)
              and (:trainingDayId is null or session.sourceTrainingDay.id = :trainingDayId)
            """)
    Page<TrainingSession> search(@Param("user") AppUser user, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate, @Param("date") LocalDate date,
            @Param("module") TrainingModule module, @Param("status") TrainingSessionStatus status,
            @Param("presetId") Long presetId, @Param("trainingDayId") Long trainingDayId, Pageable pageable);

    @Query("""
            select distinct session from TrainingSession session
            left join fetch session.sourcePreset
            left join fetch session.sourceTrainingDay
            left join fetch session.exercises sessionExercise
            left join fetch sessionExercise.sourceExercise
            where session.id = :id
              and session.user = :user
            """)
    Optional<TrainingSession> findDetailByIdAndUser(@Param("id") Long id, @Param("user") AppUser user);

    @Query("""
            select session from TrainingSession session
            where session.user = :user
              and session.sessionDate >= :fromDate
              and session.sessionDate <= :toDate
            order by session.sessionDate asc, session.id asc
            """)
    List<TrainingSession> findForCalendar(@Param("user") AppUser user, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @EntityGraph(attributePaths = {"sourcePreset", "sourceTrainingDay"})
    Optional<TrainingSession> findFirstByUserOrderBySessionDateDescIdDesc(AppUser user);

    @Query("""
            select count(session) as sessionCount, coalesce(sum(session.durationMinutes), 0) as totalMinutes
            from TrainingSession session
            where session.user = :user
              and session.sessionDate >= :fromDate
              and session.sessionDate <= :toDate
            """)
    WeeklySessionAggregate weeklyAggregate(@Param("user") AppUser user, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
