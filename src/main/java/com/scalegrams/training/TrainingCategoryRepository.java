package com.scalegrams.training;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scalegrams.user.AppUser;

public interface TrainingCategoryRepository extends JpaRepository<TrainingCategory, Long> {
    @Query("""
            select category from TrainingCategory category
            where category.deletedAt is null
              and (category.owner = :owner or category.systemCategory = true)
              and category.module = coalesce(:module, category.module)
              and (:includeInactive = true or category.active = true)
              and lower(category.name) like lower(concat('%', :query, '%'))
            """)
    Page<TrainingCategory> search(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("query") String query, @Param("includeInactive") boolean includeInactive, Pageable pageable);

    @Query("""
            select category from TrainingCategory category
            where category.id = :id and category.deletedAt is null
              and (category.owner = :owner or category.systemCategory = true)
            """)
    Optional<TrainingCategory> findSelectable(@Param("id") Long id, @Param("owner") AppUser owner);

    Optional<TrainingCategory> findByIdAndOwnerAndDeletedAtIsNull(Long id, AppUser owner);

    Optional<TrainingCategory> findByOwnerAndModuleAndNormalizedNameAndDeletedAtIsNull(AppUser owner,
            TrainingModule module, String normalizedName);

    @Query("""
            select category from TrainingCategory category
            where category.systemCategory = true and category.module = :module
              and category.normalizedName = :normalizedName and category.deletedAt is null
            """)
    Optional<TrainingCategory> findSystem(@Param("module") TrainingModule module,
            @Param("normalizedName") String normalizedName);

    @Query("""
            select count(category) > 0 from TrainingCategory category
            where category.owner = :owner and category.module = :module
              and category.normalizedName = :normalizedName and category.deletedAt is null
              and category.id <> coalesce(:excludedId, 0)
            """)
    boolean existsOwnedName(@Param("owner") AppUser owner, @Param("module") TrainingModule module,
            @Param("normalizedName") String normalizedName, @Param("excludedId") Long excludedId);
}
