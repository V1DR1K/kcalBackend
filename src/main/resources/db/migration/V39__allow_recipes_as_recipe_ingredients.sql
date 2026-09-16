ALTER TABLE recipe_ingredient
    ALTER COLUMN food_id DROP NOT NULL,
    ADD COLUMN ingredient_recipe_id BIGINT REFERENCES recipe(id);

ALTER TABLE recipe_ingredient
    ADD CONSTRAINT chk_recipe_ingredient_single_source
        CHECK ((food_id IS NOT NULL AND ingredient_recipe_id IS NULL)
            OR (food_id IS NULL AND ingredient_recipe_id IS NOT NULL));

CREATE INDEX idx_recipe_ingredient_ingredient_recipe_id
    ON recipe_ingredient(ingredient_recipe_id);
