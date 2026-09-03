CREATE TABLE training_cardio_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    equipment VARCHAR(40) NOT NULL DEFAULT 'TREADMILL',
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    distance_km NUMERIC(12, 3) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    inclined BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_training_cardio_equipment CHECK (equipment IN (
        'NONE', 'BODYWEIGHT', 'BARBELL', 'DUMBBELL', 'KETTLEBELL', 'CABLE', 'MACHINE',
        'SMITH_MACHINE', 'BENCH', 'PULL_UP_BAR', 'PARALLEL_BARS', 'RINGS', 'PARALLETTES',
        'RESISTANCE_BAND', 'BOX', 'AB_WHEEL', 'HEX_BAR', 'SLED', 'MEDICINE_BALL',
        'JUMP_ROPE', 'TREADMILL', 'STATIONARY_BIKE', 'ROWING_MACHINE', 'PLATES', 'TRX',
        'FOAM_ROLLER', 'OTHER'
    )),
    CONSTRAINT chk_training_cardio_distance CHECK (distance_km >= 0),
    CONSTRAINT chk_training_cardio_duration CHECK (duration_minutes > 0)
);

CREATE INDEX ix_training_cardio_user_recorded_at
    ON training_cardio_record(user_id, recorded_at DESC, id DESC);

CREATE TABLE training_cardio_service_event (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    equipment VARCHAR(40) NOT NULL,
    serviced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notes VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_training_cardio_service_equipment CHECK (equipment IN (
        'NONE', 'BODYWEIGHT', 'BARBELL', 'DUMBBELL', 'KETTLEBELL', 'CABLE', 'MACHINE',
        'SMITH_MACHINE', 'BENCH', 'PULL_UP_BAR', 'PARALLEL_BARS', 'RINGS', 'PARALLETTES',
        'RESISTANCE_BAND', 'BOX', 'AB_WHEEL', 'HEX_BAR', 'SLED', 'MEDICINE_BALL',
        'JUMP_ROPE', 'TREADMILL', 'STATIONARY_BIKE', 'ROWING_MACHINE', 'PLATES', 'TRX',
        'FOAM_ROLLER', 'OTHER'
    ))
);

CREATE INDEX ix_training_cardio_service_user_equipment_serviced_at
    ON training_cardio_service_event(user_id, equipment, serviced_at DESC, id DESC);
