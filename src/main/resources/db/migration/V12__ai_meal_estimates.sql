ALTER TABLE food_log
    ADD COLUMN ai_estimate_name VARCHAR(120),
    ADD COLUMN ai_estimate_confidence INTEGER,
    ADD COLUMN ai_estimate_details TEXT;

CREATE TABLE ai_estimate_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE (user_id, usage_date)
);
