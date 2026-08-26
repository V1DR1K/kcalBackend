ALTER TABLE food
    ADD COLUMN cooked_yield_factor NUMERIC(12, 4),
    ADD COLUMN cooked_yield_source VARCHAR(40),
    ADD COLUMN cooked_yield_assumption VARCHAR(240);

ALTER TABLE food
    ADD CONSTRAINT chk_food_cooked_yield_factor_positive
        CHECK (cooked_yield_factor IS NULL OR cooked_yield_factor > 0),
    ADD CONSTRAINT chk_food_cooked_yield_source
        CHECK (cooked_yield_source IS NULL OR cooked_yield_source IN ('MANUAL', 'IDENTITY', 'GEMINI'));

ALTER TABLE recipe
    ADD COLUMN raw_total_weight_grams NUMERIC(38, 2),
    ADD COLUMN cooked_total_weight_grams NUMERIC(38, 2);

UPDATE recipe
SET raw_total_weight_grams = total_weight_grams
WHERE raw_total_weight_grams IS NULL;

ALTER TABLE recipe
    ALTER COLUMN raw_total_weight_grams SET NOT NULL,
    ADD CONSTRAINT chk_recipe_raw_total_weight_positive
        CHECK (raw_total_weight_grams > 0),
    ADD CONSTRAINT chk_recipe_cooked_total_weight_positive
        CHECK (cooked_total_weight_grams IS NULL OR cooked_total_weight_grams > 0);

ALTER TABLE food_log
    ADD COLUMN recipe_raw_total_weight_grams NUMERIC(38, 2),
    ADD COLUMN recipe_cooked_total_weight_grams NUMERIC(38, 2);

ALTER TABLE food_log
    ADD CONSTRAINT chk_food_log_recipe_raw_total_weight_positive
        CHECK (recipe_raw_total_weight_grams IS NULL OR recipe_raw_total_weight_grams > 0),
    ADD CONSTRAINT chk_food_log_recipe_cooked_total_weight_positive
        CHECK (recipe_cooked_total_weight_grams IS NULL OR recipe_cooked_total_weight_grams > 0);

CREATE INDEX idx_food_active_missing_cooked_yield
    ON food(id)
    WHERE deleted_at IS NULL AND cooked_yield_factor IS NULL;
