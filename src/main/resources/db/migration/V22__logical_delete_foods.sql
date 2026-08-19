ALTER TABLE food ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_food_deleted_at ON food(deleted_at);
CREATE INDEX idx_food_created_by_deleted_at ON food(created_by_id, deleted_at, created_at);
