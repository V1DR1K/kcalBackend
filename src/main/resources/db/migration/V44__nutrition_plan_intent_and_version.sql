ALTER TABLE nutrition_plan ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED';
ALTER TABLE nutrition_plan ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE nutrition_plan SET status = 'ARCHIVED' WHERE active = false;
ALTER TABLE nutrition_plan ADD CONSTRAINT chk_nutrition_plan_status CHECK (status IN ('ALTERNATIVE', 'SCHEDULED', 'ARCHIVED'));
CREATE INDEX idx_nutrition_plan_user_status_start ON nutrition_plan(user_id, status, start_date, id);
