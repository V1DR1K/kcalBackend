CREATE TABLE ai_capture (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    draft_json TEXT NOT NULL,
    jev_decision_json TEXT,
    confirmed_log_id BIGINT REFERENCES food_log(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ai_capture_target CHECK (target_type IN ('FOOD', 'RECIPE')),
    CONSTRAINT chk_ai_capture_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'DISCARDED'))
);

CREATE INDEX idx_ai_capture_user_created ON ai_capture(user_id, created_at DESC);
CREATE INDEX idx_ai_capture_status_expires ON ai_capture(status, expires_at);
