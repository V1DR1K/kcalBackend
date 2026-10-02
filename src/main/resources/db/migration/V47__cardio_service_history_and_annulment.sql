ALTER TABLE training_cardio_service_event ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE training_cardio_service_event ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
UPDATE training_cardio_service_event SET updated_at = created_at;
ALTER TABLE training_cardio_service_event ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE training_cardio_service_event ADD COLUMN annulled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE training_cardio_service_event ADD COLUMN annulled_by_user_id BIGINT REFERENCES users(id);
ALTER TABLE training_cardio_service_event ADD COLUMN annulment_reason VARCHAR(2000);
CREATE INDEX ix_training_cardio_service_valid ON training_cardio_service_event(user_id, equipment, serviced_at DESC, id DESC) WHERE annulled_at IS NULL;
