package com.vitalitypeak.kcal.catalog;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Page<Food> findByCategory(FoodCategory category, Pageable pageable);

    Page<Food> findByNameContainingIgnoreCaseAndCategory(String name, FoodCategory category, Pageable pageable);

    @Override
    Page<Food> findAll(Pageable pageable);
}
