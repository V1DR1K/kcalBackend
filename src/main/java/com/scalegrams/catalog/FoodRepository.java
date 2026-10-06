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

    @EntityGraph(attributePaths = "nutrients.definition")
    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status and f.searchName = :query")
    java.util.List<Food> findActiveBySearchName(@Param("query") String query,
            @Param("status") ModerationStatus status);

    @EntityGraph(attributePaths = "nutrients.definition")
    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status " +
            "and (f.searchName = :query or concat(concat(f.searchName, ' '), f.searchBrand) = :query)")
    java.util.List<Food> findActiveBySearchNameOrBrand(@Param("query") String query,
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
            "when concat(' ', f.searchName, ' ') like concat('% ', :q, ' %') then 2 " +
            "when f.searchName like concat('%', :q, '%') then 3 " +
            "when f.searchBrand like concat('%', :q, '%') then 4 else 5 end, " +
            "lower(f.name), f.id")
    Page<Food> search(@Param("q") String query, @Param("status") ModerationStatus status, Pageable pageable);

    @Query("select f from Food f where f.deletedAt is null and f.moderationStatus = :status and f.category = :category and (" +
            "f.searchName like concat('%', :q, '%') or " +
            "f.searchBrand like concat('%', :q, '%') or " +
            "f.searchTags like concat('%', :q, '%')) " +
            "order by case when f.searchName = :q then 0 " +
            "when f.searchName like concat(:q, '%') then 1 " +
            "when concat(' ', f.searchName, ' ') like concat('% ', :q, ' %') then 2 " +
            "when f.searchName like concat('%', :q, '%') then 3 " +
            "when f.searchBrand like concat('%', :q, '%') then 4 else 5 end, " +
            "lower(f.name), f.id")
    Page<Food> search(@Param("q") String query, @Param("category") FoodCategory category,
            @Param("status") ModerationStatus status, Pageable pageable);

    @Query(value = """
            select f.*
            from food f
            where f.deleted_at is null
              and f.moderation_status = :status
              and (
                  f.search_name like concat('%', :q, '%')
                  or f.search_brand like concat('%', :q, '%')
                  or f.search_tags like concat('%', :q, '%')
                  or (length(:q) >= 4 and (f.search_name % :q or f.search_brand % :q or f.search_tags % :q))
                  or to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags)
                      @@ plainto_tsquery('simple', :q)
              )
            order by
              case
                  when f.search_name = :q then 0
                  when f.search_name like concat(:q, '%') then 1
                  when f.search_brand = :q then 2
                  when f.search_brand like concat(:q, '%') then 3
                  when to_tsvector('simple', f.search_name) @@ plainto_tsquery('simple', :q) then 4
                  when to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags)
                      @@ plainto_tsquery('simple', :q) then 5
                  when f.search_name like concat('%', :q, '%') then 6
                  when f.search_brand like concat('%', :q, '%') then 7
                  else 8
              end,
              ts_rank_cd(
                  to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags),
                  plainto_tsquery('simple', :q)
              ) desc,
              (
                  greatest(similarity(f.search_name, :q), word_similarity(:q, f.search_name)) * 0.6
                  + greatest(similarity(f.search_brand, :q), word_similarity(:q, f.search_brand)) * 0.28
                  + greatest(similarity(f.search_tags, :q), word_similarity(:q, f.search_tags)) * 0.12
              ) desc,
              lower(f.name), f.id
            """, countQuery = """
            select count(*)
            from food f
            where f.deleted_at is null
              and f.moderation_status = :status
              and (
                  f.search_name like concat('%', :q, '%')
                  or f.search_brand like concat('%', :q, '%')
                  or f.search_tags like concat('%', :q, '%')
                  or (length(:q) >= 4 and (f.search_name % :q or f.search_brand % :q or f.search_tags % :q))
                  or to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags)
                      @@ plainto_tsquery('simple', :q)
              )
            """, nativeQuery = true)
    Page<Food> semanticSearch(@Param("q") String query,
            @Param("status") String status, Pageable pageable);

    @Query(value = """
            select f.*
            from food f
            where f.deleted_at is null
              and f.moderation_status = :status
              and f.category = :category
              and (
                  f.search_name like concat('%', :q, '%')
                  or f.search_brand like concat('%', :q, '%')
                  or f.search_tags like concat('%', :q, '%')
                  or (length(:q) >= 4 and (f.search_name % :q or f.search_brand % :q or f.search_tags % :q))
                  or to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags)
                      @@ plainto_tsquery('simple', :q)
              )
            order by
              case
                  when f.search_name = :q then 0
                  when f.search_name like concat(:q, '%') then 1
                  when f.search_brand = :q then 2
                  when f.search_brand like concat(:q, '%') then 3
                  when to_tsvector('simple', f.search_name) @@ plainto_tsquery('simple', :q) then 4
                  when to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags)
                      @@ plainto_tsquery('simple', :q) then 5
                  when f.search_name like concat('%', :q, '%') then 6
                  when f.search_brand like concat('%', :q, '%') then 7
                  else 8
              end,
              ts_rank_cd(
                  to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags),
                  plainto_tsquery('simple', :q)
              ) desc,
              (
                  greatest(similarity(f.search_name, :q), word_similarity(:q, f.search_name)) * 0.6
                  + greatest(similarity(f.search_brand, :q), word_similarity(:q, f.search_brand)) * 0.28
                  + greatest(similarity(f.search_tags, :q), word_similarity(:q, f.search_tags)) * 0.12
              ) desc,
              lower(f.name), f.id
            """, countQuery = """
            select count(*)
            from food f
            where f.deleted_at is null
              and f.moderation_status = :status
              and f.category = :category
              and (
                  f.search_name like concat('%', :q, '%')
                  or f.search_brand like concat('%', :q, '%')
                  or f.search_tags like concat('%', :q, '%')
                  or (length(:q) >= 4 and (f.search_name % :q or f.search_brand % :q or f.search_tags % :q))
                  or to_tsvector('simple', f.search_name || ' ' || f.search_brand || ' ' || f.search_tags)
                      @@ plainto_tsquery('simple', :q)
              )
            """, nativeQuery = true)
    Page<Food> semanticSearch(@Param("q") String query, @Param("category") String category,
            @Param("status") String status, Pageable pageable);

    @Override
    Page<Food> findAll(Pageable pageable);
}
