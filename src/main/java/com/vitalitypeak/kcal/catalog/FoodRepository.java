package com.vitalitypeak.kcal.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Long> {
    List<Food> findByNameContainingIgnoreCase(String name);

    List<Food> findByCategory(FoodCategory category);

    List<Food> findByNameContainingIgnoreCaseAndCategory(String name, FoodCategory category);

    Optional<Food> findByBarcode(String barcode);
}
