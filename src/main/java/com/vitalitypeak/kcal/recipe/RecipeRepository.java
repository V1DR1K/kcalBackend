package com.vitalitypeak.kcal.recipe;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    @EntityGraph(attributePaths = {"ingredients", "ingredients.food", "ingredients.food.tags"})
    Page<Recipe> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = {"ingredients", "ingredients.food", "ingredients.food.tags"})
    Page<Recipe> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"ingredients", "ingredients.food", "ingredients.food.tags"})
    Optional<Recipe> findById(Long id);
}
