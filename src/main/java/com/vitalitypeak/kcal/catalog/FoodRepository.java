package com.vitalitypeak.kcal.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Long> {
    @EntityGraph(attributePaths = "tags")
    List<Food> findByNameContainingIgnoreCase(String name);

    @EntityGraph(attributePaths = "tags")
    List<Food> findByCategory(FoodCategory category);

    @EntityGraph(attributePaths = "tags")
    List<Food> findByNameContainingIgnoreCaseAndCategory(String name, FoodCategory category);

    @EntityGraph(attributePaths = "tags")
    Optional<Food> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    @Override
    @EntityGraph(attributePaths = "tags")
    Optional<Food> findById(Long id);

    @EntityGraph(attributePaths = "tags")
    Page<Food> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = "tags")
    Page<Food> findByCategory(FoodCategory category, Pageable pageable);

    @EntityGraph(attributePaths = "tags")
    Page<Food> findByNameContainingIgnoreCaseAndCategory(String name, FoodCategory category, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "tags")
    Page<Food> findAll(Pageable pageable);
}
