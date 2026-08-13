package com.scalegrams.catalog;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, Long> {
    @EntityGraph(attributePaths = "tags")
    Optional<Food> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    @EntityGraph(attributePaths = "tags")
    java.util.List<Food> findByPreparationGroupOrderByPreparationAsc(String preparationGroup);

    @Override
    @EntityGraph(attributePaths = "tags")
    Optional<Food> findById(Long id);

    Page<Food> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Food> findByModerationStatus(ModerationStatus status, Pageable pageable);

    Page<Food> findByModerationStatusAndCategory(ModerationStatus status, FoodCategory category, Pageable pageable);

    java.util.List<Food> findByCreatedByIdOrderByCreatedAtDesc(Long createdById);

    Page<Food> findByNameContainingIgnoreCaseAndCategory(String name, FoodCategory category, Pageable pageable);

    @Query("select distinct f from Food f left join f.tags t where f.moderationStatus = :status and (" +
            "lower(f.name) like lower(concat('%', :q, '%')) or " +
            "lower(coalesce(f.brand, '')) like lower(concat('%', :q, '%')) or " +
            "lower(t) like lower(concat('%', :q, '%'))) ")
    Page<Food> search(@Param("q") String query, @Param("status") ModerationStatus status, Pageable pageable);

    @Query("select distinct f from Food f left join f.tags t where f.moderationStatus = :status and f.category = :category and (" +
            "lower(f.name) like lower(concat('%', :q, '%')) or " +
            "lower(coalesce(f.brand, '')) like lower(concat('%', :q, '%')) or " +
            "lower(t) like lower(concat('%', :q, '%')))")
    Page<Food> search(@Param("q") String query, @Param("category") FoodCategory category,
            @Param("status") ModerationStatus status, Pageable pageable);

    @Override
    Page<Food> findAll(Pageable pageable);
}
