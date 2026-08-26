package com.scalegrams.training;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {
    @Query("""
            select preset from TrainingPlan preset
            where preset.owner = :owner
              and preset.deletedAt is null
              and preset.module = coalesce(:module, preset.module)
              and (:includeInactive = true or preset.active = true)
            """)
    Page<TrainingPlan> search(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("includeInactive") boolean includeInactive, Pageable pageable);

    Optional<TrainingPlan> findByIdAndOwnerAndDeletedAtIsNull(Long id, AppUser owner);

    Optional<TrainingPlan> findByIdAndOwner(Long id, AppUser owner);

    @Query("""
            select distinct preset from TrainingPlan preset
            left join fetch preset.days day
            where preset.id = :id
              and preset.owner = :owner
              and preset.deletedAt is null
            """)
    Optional<TrainingPlan> findDetailByIdAndOwner(@Param("id") Long id, @Param("owner") AppUser owner);

    @Query("""
            select count(preset) > 0 from TrainingPlan preset
            where preset.owner = :owner
              and preset.module = :module
              and lower(preset.name) = lower(:name)
              and preset.deletedAt is null
              and preset.id <> coalesce(:excludedId, 0)
            """)
    boolean existsLiveName(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("name") String name, @Param("excludedId") Long excludedId);

    List<TrainingPlan> findByOwnerAndModuleAndActiveTrueAndDeletedAtIsNull(AppUser owner, TrainingModule module);
}
