ALTER TABLE food ADD COLUMN IF NOT EXISTS preparation VARCHAR(255);
ALTER TABLE food ADD COLUMN IF NOT EXISTS preparation_source VARCHAR(255);
ALTER TABLE food ADD COLUMN IF NOT EXISTS preparation_group VARCHAR(255);
ALTER TABLE food ADD COLUMN IF NOT EXISTS serving_name VARCHAR(255);
ALTER TABLE food ADD COLUMN IF NOT EXISTS serving_weight_grams NUMERIC(38, 2);

UPDATE food SET preparation = 'UNSPECIFIED' WHERE preparation IS NULL;

CREATE INDEX IF NOT EXISTS idx_food_category ON food(category);
CREATE INDEX IF NOT EXISTS idx_food_lower_name ON food(LOWER(name));
CREATE INDEX IF NOT EXISTS idx_food_preparation_group ON food(preparation_group);
CREATE INDEX IF NOT EXISTS idx_food_log_user_date ON food_log(user_id, log_date);
CREATE INDEX IF NOT EXISTS idx_water_log_user_date ON water_log(user_id, log_date);
