ALTER TABLE ai_estimate_usage
    ADD COLUMN blocked_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN provider_status VARCHAR(240);
