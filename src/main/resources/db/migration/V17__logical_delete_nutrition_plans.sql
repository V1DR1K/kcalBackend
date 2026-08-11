ALTER TABLE nutrition_plan
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_nutrition_plan_user_active_start
    ON nutrition_plan(user_id, active, start_date);
