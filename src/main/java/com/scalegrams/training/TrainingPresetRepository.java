package com.scalegrams.training;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingPresetRepository extends JpaRepository<TrainingPreset, Long> {
    @Query("""
            select preset from TrainingPreset preset
            where preset.owner = :owner
              and preset.deletedAt is null
              and (:module is null or preset.module = :module)
              and (:includeInactive = true or preset.active = true)
            """)
    Page<TrainingPreset> search(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("includeInactive") boolean includeInactive, Pageable pageable);

    Optional<TrainingPreset> findByIdAndOwnerAndDeletedAtIsNull(Long id, AppUser owner);

    Optional<TrainingPreset> findByIdAndOwner(Long id, AppUser owner);

    @Query("""
            select distinct preset from TrainingPreset preset
            left join fetch preset.days day
            left join fetch day.exercises presetExercise
            left join fetch presetExercise.exercise
            where preset.id = :id
              and preset.owner = :owner
              and preset.deletedAt is null
            """)
    Optional<TrainingPreset> findDetailByIdAndOwner(@Param("id") Long id, @Param("owner") AppUser owner);

    @Query("""
            select count(preset) > 0 from TrainingPreset preset
            where preset.owner = :owner
              and preset.module = :module
              and lower(preset.name) = lower(:name)
              and preset.deletedAt is null
              and (:excludedId is null or preset.id <> :excludedId)
            """)
    boolean existsLiveName(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("name") String name, @Param("excludedId") Long excludedId);
}
