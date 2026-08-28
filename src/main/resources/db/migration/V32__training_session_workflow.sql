ALTER TABLE training_session
    DROP CONSTRAINT chk_training_session_status,
    ADD CONSTRAINT chk_training_session_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'SKIPPED'));

-- Migrate the session state without deleting historical rows.
UPDATE training_session
SET status = 'IN_PROGRESS'
WHERE status = 'STARTED';

-- Preserve duplicate legacy starts while leaving one resumable session per slot.
WITH duplicate_starts AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY user_id, source_preset_id, source_training_day_id, session_date
               ORDER BY id DESC
           ) AS duplicate_rank
    FROM training_session
    WHERE status = 'IN_PROGRESS'
      AND source_preset_id IS NOT NULL
      AND source_training_day_id IS NOT NULL
)
UPDATE training_session session
SET status = 'CANCELLED',
    finished_at = COALESCE(finished_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
FROM duplicate_starts duplicate_start
WHERE session.id = duplicate_start.id
  AND duplicate_start.duplicate_rank > 1;

-- Canonical plans may describe structure only. Legacy values remain intact.
ALTER TABLE training_preset_exercise
    ALTER COLUMN target_sets DROP NOT NULL,
    ALTER COLUMN target_repetitions DROP NOT NULL;

ALTER TABLE training_set
    ALTER COLUMN repetitions DROP NOT NULL;

ALTER TABLE training_session
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN baseline_captured BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN baseline_plan_version BIGINT,
    ADD COLUMN baseline_plan_day_version BIGINT;

ALTER TABLE training_preset
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE training_day
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE training_preset_exercise
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE training_session_exercise
    ADD COLUMN source_plan_exercise_id BIGINT REFERENCES training_preset_exercise(id) ON DELETE SET NULL,
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'ADDED',
    ADD CONSTRAINT chk_training_session_exercise_origin CHECK (origin IN ('PLAN', 'ADDED'));

CREATE TABLE training_session_baseline (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES training_session(id) ON DELETE CASCADE,
    plan_exercise_id BIGINT REFERENCES training_preset_exercise(id) ON DELETE SET NULL,
    catalog_exercise_id BIGINT REFERENCES training_exercise(id) ON DELETE SET NULL,
    exercise_name VARCHAR(120) NOT NULL,
    position INTEGER NOT NULL,
    plan_version BIGINT NOT NULL,
    plan_day_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_training_session_baseline_position CHECK (position >= 0)
);

CREATE UNIQUE INDEX ux_training_session_baseline_plan_exercise
    ON training_session_baseline(session_id, plan_exercise_id)
    WHERE plan_exercise_id IS NOT NULL;
CREATE INDEX ix_training_session_baseline_session_position
    ON training_session_baseline(session_id, position, id);

-- A plan slot can have only one active or completed result while retaining free-session history.
CREATE UNIQUE INDEX ux_training_session_in_progress_plan_day_date
    ON training_session(user_id, source_preset_id, source_training_day_id, session_date)
    WHERE status IN ('IN_PROGRESS', 'COMPLETED', 'SKIPPED')
      AND source_preset_id IS NOT NULL
      AND source_training_day_id IS NOT NULL;
