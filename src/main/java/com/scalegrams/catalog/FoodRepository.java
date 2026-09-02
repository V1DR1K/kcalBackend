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

    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status and f.searchName = :query")
    java.util.List<Food> findActiveBySearchName(@Param("query") String query,
            @Param("status") ModerationStatus status);

    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status " +
            "and f.searchName like concat('%', :token, '%') order by f.id")
    java.util.List<Food> findActiveBySearchToken(@Param("token") String token,
            @Param("status") ModerationStatus status, org.springframework.data.domain.Pageable pageable);

    java.util.Optional<Food> findBySourceAndSourceId(String source, String sourceId);

    @EntityGraph(attributePaths = "tags")
    java.util.List<Food> findByPreparationGroupAndDeletedAtIsNullOrderByPreparationAsc(String preparationGroup);

    @Override
    @EntityGraph(attributePaths = "tags")
    Optional<Food> findById(Long id);

    Page<Food> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Food> findByModerationStatusAndDeletedAtIsNull(ModerationStatus status, Pageable pageable);

    Page<Food> findByModerationStatusAndCategoryAndDeletedAtIsNull(ModerationStatus status, FoodCategory category, Pageable pageable);

    Page<Food> findByDeletedAtIsNullAndCookedYieldFactorIsNull(Pageable pageable);

    long countByDeletedAtIsNullAndCookedYieldFactorIsNull();

    java.util.List<Food> findByCreatedByIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long createdById);

    java.util.List<Food> findByCreatedByIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long createdById);

    Page<Food> findByNameContainingIgnoreCaseAndCategory(String name, FoodCategory category, Pageable pageable);

    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status and (" +
            "f.searchName like concat('%', :q, '%') or " +
            "f.searchBrand like concat('%', :q, '%') or " +
            "f.searchTags like concat('%', :q, '%')) " +
            "order by case when f.searchName = :q then 0 " +
            "when f.searchName like concat(:q, '%') then 1 " +
            "when f.searchName like concat('%', :q, '%') then 2 " +
            "when f.searchBrand like concat('%', :q, '%') then 3 else 4 end, " +
            "lower(f.name), f.id")
    Page<Food> search(@Param("q") String query, @Param("status") ModerationStatus status, Pageable pageable);

    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status and f.category = :category and (" +
            "f.searchName like concat('%', :q, '%') or " +
            "f.searchBrand like concat('%', :q, '%') or " +
            "f.searchTags like concat('%', :q, '%')) " +
            "order by case when f.searchName = :q then 0 " +
            "when f.searchName like concat(:q, '%') then 1 " +
            "when f.searchName like concat('%', :q, '%') then 2 " +
            "when f.searchBrand like concat('%', :q, '%') then 3 else 4 end, " +
            "lower(f.name), f.id")
    Page<Food> search(@Param("q") String query, @Param("category") FoodCategory category,
            @Param("status") ModerationStatus status, Pageable pageable);

    @Override
    Page<Food> findAll(Pageable pageable);
}
