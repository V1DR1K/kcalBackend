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

    @EntityGraph(attributePaths = {"sourcePlan", "sourcePlanDay"})
    @Query("""
            select session from TrainingSession session
            left join session.sourcePlan sourcePlan
            left join session.sourcePlanDay sourcePlanDay
            where session.user = :user
              and session.sessionDate >= coalesce(:fromDate, session.sessionDate)
              and session.sessionDate <= coalesce(:toDate, session.sessionDate)
              and session.sessionDate = coalesce(:date, session.sessionDate)
              and session.module = coalesce(:module, session.module)
              and session.status = coalesce(:status, session.status)
              and coalesce(sourcePlan.id, 0) = coalesce(:presetId, coalesce(sourcePlan.id, 0))
              and coalesce(sourcePlanDay.id, 0) = coalesce(:trainingDayId, coalesce(sourcePlanDay.id, 0))
            """)
    Page<TrainingSession> search(@Param("user") AppUser user, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate, @Param("date") LocalDate date,
            @Param("module") TrainingModule module, @Param("status") TrainingSessionStatus status,
            @Param("presetId") Long presetId, @Param("trainingDayId") Long trainingDayId, Pageable pageable);

    @Query("""
            select distinct session from TrainingSession session
            left join fetch session.sourcePlan
            left join fetch session.sourcePlanDay
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

    @EntityGraph(attributePaths = {"sourcePlan", "sourcePlanDay"})
    Optional<TrainingSession> findFirstByUserOrderBySessionDateDescIdDesc(AppUser user);

    @Query("""
            select session from TrainingSession session
            left join fetch session.sourcePlan
            left join fetch session.sourcePlanDay
            where session.user = :user
              and session.sessionDate = :date
              and session.sourcePlan.id = :planId
              and session.sourcePlanDay.id = :planDayId
              and session.status in (com.scalegrams.training.TrainingSessionStatus.IN_PROGRESS,
                                     com.scalegrams.training.TrainingSessionStatus.COMPLETED,
                                     com.scalegrams.training.TrainingSessionStatus.SKIPPED)
            order by case session.status
                when com.scalegrams.training.TrainingSessionStatus.IN_PROGRESS then 0
                when com.scalegrams.training.TrainingSessionStatus.COMPLETED then 1
                else 2
            end, session.id desc
            """)
    List<TrainingSession> findBlockingForSchedule(@Param("user") AppUser user, @Param("date") LocalDate date,
            @Param("planId") Long planId, @Param("planDayId") Long planDayId);

    @Query("""
            select session from TrainingSession session
            left join fetch session.sourcePlan
            left join fetch session.sourcePlanDay
            where session.user = :user
              and session.sessionDate = :date
              and session.sourcePlan is not null
              and session.sourcePlanDay is not null
              and session.status in (com.scalegrams.training.TrainingSessionStatus.IN_PROGRESS,
                                     com.scalegrams.training.TrainingSessionStatus.COMPLETED,
                                     com.scalegrams.training.TrainingSessionStatus.SKIPPED)
            order by case session.status
                when com.scalegrams.training.TrainingSessionStatus.IN_PROGRESS then 0
                when com.scalegrams.training.TrainingSessionStatus.COMPLETED then 1
                else 2
            end, session.id desc
            """)
    List<TrainingSession> findBlockingForDate(@Param("user") AppUser user, @Param("date") LocalDate date);

    @Query("""
            select count(session) from TrainingSession session
            where session.user = :user
              and session.sourcePlan.id = :planId
              and session.sessionDate < :date
              and session.status in (com.scalegrams.training.TrainingSessionStatus.COMPLETED,
                                     com.scalegrams.training.TrainingSessionStatus.SKIPPED)
            """)
    long countAdvancingSessions(@Param("user") AppUser user, @Param("planId") Long planId,
            @Param("date") LocalDate date);

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
