package com.scalegrams.training;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingCardioRecordRepository extends JpaRepository<TrainingCardioRecord, Long> {
    Page<TrainingCardioRecord> findByUser(AppUser user, Pageable pageable);

    Optional<TrainingCardioRecord> findByIdAndUser(Long id, AppUser user);

    @Query("""
            select coalesce(sum(record.durationMinutes), 0)
            from TrainingCardioRecord record
            where record.user = :user
              and record.equipment = :equipment
              and record.recordedAt > :recordedAt
            """)
    long sumDurationAfter(@Param("user") AppUser user, @Param("equipment") TrainingEquipment equipment,
            @Param("recordedAt") OffsetDateTime recordedAt);

    @Query("""
            select coalesce(sum(record.durationMinutes), 0)
            from TrainingCardioRecord record
            where record.user = :user
              and record.equipment = :equipment
            """)
    long sumDuration(@Param("user") AppUser user, @Param("equipment") TrainingEquipment equipment);
}
