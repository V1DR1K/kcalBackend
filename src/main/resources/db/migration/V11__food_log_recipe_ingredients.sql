CREATE TABLE food_log_recipe_ingredient (
    id BIGSERIAL PRIMARY KEY,
    food_log_id BIGINT NOT NULL REFERENCES food_log(id) ON DELETE CASCADE,
    food_id BIGINT NOT NULL REFERENCES food(id),
    quantity NUMERIC(38, 2) NOT NULL,
    unit VARCHAR(255) NOT NULL
);

CREATE INDEX idx_food_log_recipe_ingredient_log_id ON food_log_recipe_ingredient(food_log_id);
