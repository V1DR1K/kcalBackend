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
    Page<Recipe> findBySearchNameContaining(String name, Pageable pageable);

    Page<Recipe> findAll(Pageable pageable);

    Page<Recipe> findByCreatedById(Long createdById, Pageable pageable);

    Page<Recipe> findByCreatedByIdAndSearchNameContaining(Long createdById, String name, Pageable pageable);

    @Query("select r.createdBy from Recipe r where r.createdBy.id <> :userId order by r.createdBy.fullName asc, r.createdBy.id asc")
    List<AppUser> findAuthorsExcluding(@Param("userId") Long userId);

    @Query("""
            select r.createdBy.id as ownerId, r.createdBy.fullName as ownerName, count(r) as recipeCount
            from Recipe r where r.createdBy is not null and r.createdBy.id <> :userId
            group by r.createdBy.id, r.createdBy.fullName
            order by r.createdBy.fullName asc, r.createdBy.id asc
            """)
    List<RecipeAuthorCountProjection> findAuthorCountsExcluding(@Param("userId") Long userId);

    interface RecipeAuthorCountProjection {
        Long getOwnerId();
        String getOwnerName();
        Long getRecipeCount();
    }

    @Query("select r.id as recipeId, count(ingredient.id) as ingredientCount from Recipe r left join r.ingredients ingredient where r.id in :ids group by r.id")
    List<RecipeIngredientCountProjection> countIngredientsForPage(@Param("ids") List<Long> ids);
    interface RecipeIngredientCountProjection {
        Long getRecipeId();
        Long getIngredientCount();
    }

    long countByCreatedById(Long createdById);

    @Query("select count(r) > 0 from Recipe r join r.ingredients ingredient where ingredient.ingredientRecipe.id = :recipeId")
    boolean existsReferencingRecipe(@Param("recipeId") Long recipeId);

    @Override
    @EntityGraph(attributePaths = {"ingredients", "ingredients.food", "ingredients.ingredientRecipe"})
    Optional<Recipe> findById(Long id);
}
