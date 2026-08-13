CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_food_name_trgm
    ON food USING gin (lower(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_food_brand_trgm
    ON food USING gin (lower(brand) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_food_tags_trgm
    ON food_tags USING gin (lower(tag) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_food_moderation_category_name
    ON food(moderation_status, category, lower(name), id);

CREATE INDEX IF NOT EXISTS idx_food_log_user_date_meal
    ON food_log(user_id, log_date, meal_type);
