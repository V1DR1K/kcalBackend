CREATE TABLE meal_share (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    source_date DATE NOT NULL,
    source_meal_type VARCHAR(40) NOT NULL,
    snapshot_json TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_meal_share_meal_type CHECK (source_meal_type IN ('BREAKFAST', 'LUNCH', 'AFTERNOON_SNACK', 'DINNER'))
);

CREATE INDEX ix_meal_share_owner_created_at ON meal_share(owner_id, created_at DESC);
CREATE INDEX ix_meal_share_expires_at ON meal_share(expires_at);

CREATE TABLE meal_share_acceptance (
    id BIGSERIAL PRIMARY KEY,
    share_id BIGINT NOT NULL REFERENCES meal_share(id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_date DATE NOT NULL,
    target_meal_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_meal_share_acceptance_recipient UNIQUE (share_id, recipient_id),
    CONSTRAINT chk_meal_share_acceptance_meal_type CHECK (target_meal_type IN ('BREAKFAST', 'LUNCH', 'AFTERNOON_SNACK', 'DINNER'))
);

CREATE INDEX ix_meal_share_acceptance_recipient_created ON meal_share_acceptance(recipient_id, created_at DESC);
