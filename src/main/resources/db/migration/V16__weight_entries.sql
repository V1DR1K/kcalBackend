-- Weight history. One row per day per user; upserted when the profile weight changes.
CREATE TABLE weight_entry (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    entry_date DATE NOT NULL,
    weight_kg NUMERIC(38, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_weight_entry_user_date UNIQUE (user_id, entry_date),
    CONSTRAINT fk_weight_entry_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_weight_entry_user_date ON weight_entry (user_id, entry_date DESC);