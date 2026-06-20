CREATE TABLE IF NOT EXISTS nutrition_plan (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    daily_calories INTEGER NOT NULL,
    protein_percent NUMERIC(38, 2) NOT NULL,
    carbs_percent NUMERIC(38, 2) NOT NULL,
    fat_percent NUMERIC(38, 2) NOT NULL,
    protein_goal_grams INTEGER NOT NULL,
    carbs_goal_grams INTEGER NOT NULL,
    fat_goal_grams INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_nutrition_plan_user_start ON nutrition_plan(user_id, start_date);
CREATE INDEX IF NOT EXISTS idx_nutrition_plan_user_end ON nutrition_plan(user_id, end_date);

CREATE TABLE IF NOT EXISTS recipe (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    total_weight_grams NUMERIC(38, 2) NOT NULL,
    calories INTEGER,
    protein_grams NUMERIC(38, 2),
    carbs_grams NUMERIC(38, 2),
    fat_grams NUMERIC(38, 2),
    created_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS recipe_ingredient (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
    food_id BIGINT NOT NULL REFERENCES food(id),
    unit VARCHAR(255) NOT NULL,
    quantity NUMERIC(38, 2) NOT NULL
);

ALTER TABLE food_log ADD COLUMN IF NOT EXISTS recipe_id BIGINT REFERENCES recipe(id);
ALTER TABLE food_log ADD COLUMN IF NOT EXISTS item_type VARCHAR(255);
UPDATE food_log SET item_type = 'FOOD' WHERE item_type IS NULL;
