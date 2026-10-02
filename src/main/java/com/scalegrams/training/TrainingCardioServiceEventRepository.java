package com.scalegrams.training;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.scalegrams.user.AppUser;

public interface TrainingCardioServiceEventRepository extends JpaRepository<TrainingCardioServiceEvent, Long> {
    Optional<TrainingCardioServiceEvent> findFirstByUserAndEquipmentOrderByServicedAtDescIdDesc(AppUser user,
            TrainingEquipment equipment);

    Optional<TrainingCardioServiceEvent> findFirstByUserAndEquipmentAndAnnulledAtIsNullOrderByServicedAtDescIdDesc(AppUser user, TrainingEquipment equipment);
    Optional<TrainingCardioServiceEvent> findByIdAndUser(Long id, AppUser user);
    org.springframework.data.domain.Page<TrainingCardioServiceEvent> findByUser(AppUser user, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<TrainingCardioServiceEvent> findByUserAndEquipment(AppUser user, TrainingEquipment equipment, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        select count(event) > 0 from TrainingCardioServiceEvent event
        where event.user = :user and event.equipment = :equipment and event.servicedAt = :date
        and event.annulledAt is null and (:excludedId is null or event.id <> :excludedId)
        and ((:notes is null and event.notes is null) or event.notes = :notes)
        """)
    boolean existsDuplicate(@org.springframework.data.repository.query.Param("user") AppUser user,
        @org.springframework.data.repository.query.Param("equipment") TrainingEquipment equipment,
        @org.springframework.data.repository.query.Param("date") java.time.OffsetDateTime date,
        @org.springframework.data.repository.query.Param("notes") String notes,
        @org.springframework.data.repository.query.Param("excludedId") Long excludedId);
}
