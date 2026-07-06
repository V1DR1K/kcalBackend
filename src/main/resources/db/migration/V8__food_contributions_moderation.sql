ALTER TABLE food ADD COLUMN created_by_id BIGINT REFERENCES users(id);
ALTER TABLE food ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE food ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
CREATE INDEX idx_food_moderation_status ON food(moderation_status);
CREATE INDEX idx_food_created_by ON food(created_by_id);
