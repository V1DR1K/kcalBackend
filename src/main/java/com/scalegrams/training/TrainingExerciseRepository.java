package com.scalegrams.training;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingExerciseRepository extends JpaRepository<TrainingExercise, Long> {
    @Query("""
            select exercise from TrainingExercise exercise
            where (exercise.owner = :owner or exercise.systemExercise = true)
              and exercise.deletedAt is null
              and exercise.module = coalesce(:module, exercise.module)
              and (:includeInactive = true or exercise.active = true)
              and (:categoryId is null or exercise.category.id = :categoryId)
              and (:categoryName is null or exercise.category.normalizedName = :categoryName)
              and (:equipment is null or exercise.equipment = :equipment)
              and (:difficulty is null or exercise.difficulty = :difficulty)
              and (:registrationType is null or exercise.registrationType = :registrationType)
              and (lower(exercise.name) like lower(concat('%', :query, '%'))
                   or lower(exercise.code) like lower(concat('%', :query, '%'))
                   or lower(exercise.category.name) like lower(concat('%', :query, '%'))
                   or exists (select muscle from TrainingExercise item join item.primaryMuscles muscle
                              where item = exercise and lower(muscle) like lower(concat('%', :query, '%')))
                   or exists (select muscle from TrainingExercise item join item.secondaryMuscles muscle
                              where item = exercise and lower(muscle) like lower(concat('%', :query, '%'))))
            """)
    Page<TrainingExercise> search(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("query") String query, @Param("categoryId") Long categoryId,
            @Param("categoryName") String categoryName, @Param("equipment") TrainingEquipment equipment,
            @Param("difficulty") TrainingDifficulty difficulty,
            @Param("registrationType") TrainingRegistrationType registrationType,
            @Param("includeInactive") boolean includeInactive, Pageable pageable);

    Page<TrainingExercise> findByOwnerAndActiveTrueAndDeletedAtIsNull(AppUser owner, Pageable pageable);

    Optional<TrainingExercise> findByIdAndOwnerAndDeletedAtIsNull(Long id, AppUser owner);

    @Query("""
            select exercise from TrainingExercise exercise
            where exercise.id = :id
              and exercise.deletedAt is null
              and (exercise.owner = :owner or exercise.systemExercise = true)
            """)
    Optional<TrainingExercise> findSelectable(@Param("id") Long id, @Param("owner") AppUser owner);

    Optional<TrainingExercise> findByOwnerAndModuleAndNameIgnoreCaseAndDeletedAtIsNull(AppUser owner,
            TrainingModule module, String name);

    @Query("""
            select count(exercise) > 0 from TrainingExercise exercise
            where exercise.owner = :owner
              and exercise.module = :module
              and lower(exercise.name) = lower(:name)
              and exercise.deletedAt is null
              and exercise.id <> coalesce(:excludedId, 0)
            """)
    boolean existsLiveName(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("name") String name, @Param("excludedId") Long excludedId);

    @Query("""
            select count(exercise) > 0 from TrainingExercise exercise
            where exercise.code = :code
              and exercise.deletedAt is null
              and exercise.id <> coalesce(:excludedId, 0)
            """)
    boolean existsLiveCode(@Param("code") String code, @Param("excludedId") Long excludedId);
}
