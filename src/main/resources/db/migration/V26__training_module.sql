CREATE TABLE training_exercise (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(80),
    module VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_training_exercise_module CHECK (module IN ('GYM', 'CALISTHENICS'))
);

CREATE UNIQUE INDEX ux_training_exercise_owner_module_name_active
    ON training_exercise(owner_id, module, lower(name))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_training_exercise_owner_active_module_name
    ON training_exercise(owner_id, module, active, name)
    WHERE deleted_at IS NULL;

CREATE TABLE training_preset (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    module VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_training_preset_module CHECK (module IN ('GYM', 'CALISTHENICS'))
);

CREATE UNIQUE INDEX ux_training_preset_owner_module_name_active
    ON training_preset(owner_id, module, lower(name))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_training_preset_owner_active_module_updated
    ON training_preset(owner_id, active, module, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE training_day (
    id BIGSERIAL PRIMARY KEY,
    preset_id BIGINT NOT NULL REFERENCES training_preset(id),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    day_of_week VARCHAR(12),
    position INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_training_day_position CHECK (position >= 0),
    CONSTRAINT chk_training_day_day_of_week CHECK (day_of_week IS NULL OR day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
    ))
);

CREATE INDEX ix_training_day_preset_active_position
    ON training_day(preset_id, active, position, id)
    WHERE deleted_at IS NULL;

CREATE TABLE training_preset_exercise (
    id BIGSERIAL PRIMARY KEY,
    training_day_id BIGINT NOT NULL REFERENCES training_day(id),
    exercise_id BIGINT NOT NULL REFERENCES training_exercise(id),
    target_sets INTEGER NOT NULL,
    target_repetitions INTEGER NOT NULL,
    target_weight_kg NUMERIC(12, 3),
    notes VARCHAR(1000),
    position INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_training_preset_exercise_targets CHECK (target_sets >= 0 AND target_repetitions >= 0),
    CONSTRAINT chk_training_preset_exercise_weight CHECK (target_weight_kg IS NULL OR target_weight_kg >= 0),
    CONSTRAINT chk_training_preset_exercise_position CHECK (position >= 0)
);

CREATE UNIQUE INDEX ux_training_preset_exercise_day_exercise_active
    ON training_preset_exercise(training_day_id, exercise_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_training_preset_exercise_day_active_position
    ON training_preset_exercise(training_day_id, active, position, id)
    WHERE deleted_at IS NULL;

CREATE TABLE training_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_date DATE NOT NULL,
    module VARCHAR(30) NOT NULL,
    source_preset_id BIGINT REFERENCES training_preset(id) ON DELETE SET NULL,
    source_training_day_id BIGINT REFERENCES training_day(id) ON DELETE SET NULL,
    title VARCHAR(160),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    duration_minutes INTEGER,
    notes VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_training_session_module CHECK (module IN ('GYM', 'CALISTHENICS')),
    CONSTRAINT chk_training_session_status CHECK (status IN ('STARTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_training_session_duration CHECK (duration_minutes IS NULL OR duration_minutes >= 0)
);

CREATE INDEX ix_training_session_user_date
    ON training_session(user_id, session_date DESC, id DESC);
CREATE INDEX ix_training_session_user_module_status_date
    ON training_session(user_id, module, status, session_date DESC, id DESC);
CREATE INDEX ix_training_session_source_preset
    ON training_session(user_id, source_preset_id, session_date DESC, id DESC);
CREATE INDEX ix_training_session_source_day
    ON training_session(user_id, source_training_day_id, session_date DESC, id DESC);

CREATE TABLE training_session_exercise (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES training_session(id) ON DELETE CASCADE,
    source_exercise_id BIGINT REFERENCES training_exercise(id),
    exercise_name VARCHAR(120) NOT NULL,
    target_sets INTEGER,
    target_repetitions INTEGER,
    target_weight_kg NUMERIC(12, 3),
    notes VARCHAR(1000),
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_training_session_exercise_targets CHECK (
        (target_sets IS NULL OR target_sets >= 0)
        AND (target_repetitions IS NULL OR target_repetitions >= 0)
    ),
    CONSTRAINT chk_training_session_exercise_weight CHECK (target_weight_kg IS NULL OR target_weight_kg >= 0),
    CONSTRAINT chk_training_session_exercise_position CHECK (position >= 0)
);

CREATE INDEX ix_training_session_exercise_session_position
    ON training_session_exercise(session_id, position, id);

CREATE TABLE training_set (
    id BIGSERIAL PRIMARY KEY,
    session_exercise_id BIGINT NOT NULL REFERENCES training_session_exercise(id) ON DELETE CASCADE,
    set_number INTEGER NOT NULL,
    repetitions INTEGER NOT NULL,
    weight_kg NUMERIC(12, 3),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_training_set_exercise_number UNIQUE(session_exercise_id, set_number),
    CONSTRAINT chk_training_set_number CHECK (set_number > 0),
    CONSTRAINT chk_training_set_repetitions CHECK (repetitions >= 0),
    CONSTRAINT chk_training_set_weight CHECK (weight_kg IS NULL OR weight_kg >= 0)
);

CREATE INDEX ix_training_set_session_exercise_number
    ON training_set(session_exercise_id, set_number);
