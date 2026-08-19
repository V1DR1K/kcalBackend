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

    @EntityGraph(attributePaths = "tags")
    Optional<Food> findByBarcodeAndDeletedAtIsNull(String barcode);

    boolean existsByBarcode(String barcode);

    @EntityGraph(attributePaths = "tags")
    java.util.List<Food> findByPreparationGroupAndDeletedAtIsNullOrderByPreparationAsc(String preparationGroup);

    @Override
    @EntityGraph(attributePaths = "tags")
    Optional<Food> findById(Long id);

    Page<Food> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Food> findByModerationStatusAndDeletedAtIsNull(ModerationStatus status, Pageable pageable);

    Page<Food> findByModerationStatusAndCategoryAndDeletedAtIsNull(ModerationStatus status, FoodCategory category, Pageable pageable);

    java.util.List<Food> findByCreatedByIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long createdById);

    Page<Food> findByNameContainingIgnoreCaseAndCategory(String name, FoodCategory category, Pageable pageable);

    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status and (" +
            "lower(f.name) like lower(concat('%', :q, '%')) or " +
            "lower(coalesce(f.brand, '')) like lower(concat('%', :q, '%')) or " +
            "exists (select 1 from Food taggedFood join taggedFood.tags tag where taggedFood.id = f.id and lower(tag) like lower(concat('%', :q, '%')))) " +
            "order by case when lower(f.name) = lower(:q) then 0 " +
            "when lower(f.name) like lower(concat(:q, '%')) then 1 " +
            "when lower(f.name) like lower(concat('%', :q, '%')) then 2 " +
            "when lower(coalesce(f.brand, '')) like lower(concat('%', :q, '%')) then 3 else 4 end, " +
            "lower(f.name), f.id")
    Page<Food> search(@Param("q") String query, @Param("status") ModerationStatus status, Pageable pageable);

    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status and f.category = :category and (" +
            "lower(f.name) like lower(concat('%', :q, '%')) or " +
            "lower(coalesce(f.brand, '')) like lower(concat('%', :q, '%')) or " +
            "exists (select 1 from Food taggedFood join taggedFood.tags tag where taggedFood.id = f.id and lower(tag) like lower(concat('%', :q, '%')))) " +
            "order by case when lower(f.name) = lower(:q) then 0 " +
            "when lower(f.name) like lower(concat(:q, '%')) then 1 " +
            "when lower(f.name) like lower(concat('%', :q, '%')) then 2 " +
            "when lower(coalesce(f.brand, '')) like lower(concat('%', :q, '%')) then 3 else 4 end, " +
            "lower(f.name), f.id")
    Page<Food> search(@Param("q") String query, @Param("category") FoodCategory category,
            @Param("status") ModerationStatus status, Pageable pageable);

    @Override
    Page<Food> findAll(Pageable pageable);
}
