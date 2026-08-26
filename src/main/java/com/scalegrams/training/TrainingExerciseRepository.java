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
            where (exercise.owner = :owner or exercise.globalExercise = true)
              and exercise.deletedAt is null
              and (:module is null or exercise.module = :module)
              and (:query is null or lower(exercise.name) like lower(concat('%', :query, '%')))
            """)
    Page<TrainingExercise> search(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("query") String query, Pageable pageable);

    Page<TrainingExercise> findByOwnerAndActiveTrueAndDeletedAtIsNull(AppUser owner, Pageable pageable);

    Optional<TrainingExercise> findByIdAndOwnerAndDeletedAtIsNull(Long id, AppUser owner);

    @Query("""
            select exercise from TrainingExercise exercise
            where exercise.id = :id
              and exercise.deletedAt is null
              and (exercise.owner = :owner or exercise.globalExercise = true)
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
              and (:excludedId is null or exercise.id <> :excludedId)
            """)
    boolean existsLiveName(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("name") String name, @Param("excludedId") Long excludedId);
}
