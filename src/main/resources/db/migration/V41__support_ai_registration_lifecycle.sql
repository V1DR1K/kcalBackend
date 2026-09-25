ALTER TABLE ai_capture
    ADD COLUMN confirmed_food_id BIGINT REFERENCES food(id) ON DELETE SET NULL,
    ADD COLUMN confirmed_recipe_id BIGINT REFERENCES recipe(id) ON DELETE SET NULL;

ALTER TABLE ai_capture
    DROP CONSTRAINT IF EXISTS ai_capture_confirmed_log_id_fkey;

ALTER TABLE ai_capture
    ADD CONSTRAINT fk_ai_capture_confirmed_log
    FOREIGN KEY (confirmed_log_id) REFERENCES food_log(id) ON DELETE SET NULL;
