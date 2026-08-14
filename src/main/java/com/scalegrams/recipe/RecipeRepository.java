package com.scalegrams.recipe;

import java.util.List;
import java.util.Optional;

import com.scalegrams.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    Page<Recipe> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Recipe> findAll(Pageable pageable);

    Page<Recipe> findByCreatedById(Long createdById, Pageable pageable);

    Page<Recipe> findByCreatedByIdAndNameContainingIgnoreCase(Long createdById, String name, Pageable pageable);

    @Query("select r.createdBy from Recipe r where r.createdBy.id <> :userId order by r.createdBy.fullName asc, r.createdBy.id asc")
    List<AppUser> findAuthorsExcluding(@Param("userId") Long userId);

    long countByCreatedById(Long createdById);

    @Override
    @EntityGraph(attributePaths = {"ingredients", "ingredients.food"})
    Optional<Recipe> findById(Long id);
}
