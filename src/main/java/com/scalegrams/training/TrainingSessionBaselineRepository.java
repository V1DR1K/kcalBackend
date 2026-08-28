package com.scalegrams.training;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionBaselineRepository extends JpaRepository<TrainingSessionBaseline, Long> {
    List<TrainingSessionBaseline> findBySessionIdOrderByPositionAscIdAsc(Long sessionId);
}
