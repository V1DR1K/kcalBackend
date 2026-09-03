package com.scalegrams.training;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.scalegrams.user.AppUser;

public interface TrainingCardioServiceEventRepository extends JpaRepository<TrainingCardioServiceEvent, Long> {
    Optional<TrainingCardioServiceEvent> findFirstByUserAndEquipmentOrderByServicedAtDescIdDesc(AppUser user,
            TrainingEquipment equipment);
}
