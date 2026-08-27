CREATE TABLE training_category (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT REFERENCES users(id),
    module VARCHAR(30) NOT NULL,
    name VARCHAR(80) NOT NULL,
    normalized_name VARCHAR(80) NOT NULL,
    system_category BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_training_category_module CHECK (module IN ('GYM', 'CALISTHENICS')),
    CONSTRAINT chk_training_category_visibility CHECK (
        (system_category AND owner_id IS NULL) OR (NOT system_category AND owner_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_training_category_system_name
    ON training_category(module, normalized_name)
    WHERE system_category AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_training_category_owner_name
    ON training_category(owner_id, module, normalized_name)
    WHERE NOT system_category AND deleted_at IS NULL;
CREATE INDEX ix_training_category_module_active_name
    ON training_category(module, active, name, id)
    WHERE deleted_at IS NULL;

INSERT INTO training_category (owner_id, module, name, normalized_name, system_category)
SELECT NULL, source.module, source.name, lower(source.name), TRUE
FROM (VALUES
    ('CALISTHENICS', 'EMPUJE'), ('CALISTHENICS', 'TRACCION'),
    ('CALISTHENICS', 'PIERNAS'), ('CALISTHENICS', 'ABDOMEN'),
    ('CALISTHENICS', 'HABILIDAD'), ('CALISTHENICS', 'ACONDICIONAMIENTO'),
    ('CALISTHENICS', 'MOVILIDAD'),
    ('GYM', 'PECHO'), ('GYM', 'ESPALDA'), ('GYM', 'HOMBROS'),
    ('GYM', 'BICEPS'), ('GYM', 'TRICEPS'), ('GYM', 'CUADRICEPS'),
    ('GYM', 'ISQUIOTIBIALES'), ('GYM', 'GLUTEOS'), ('GYM', 'PANTORRILLAS'),
    ('GYM', 'ABDOMEN'), ('GYM', 'CUERPO_COMPLETO'), ('GYM', 'ACONDICIONAMIENTO'),
    ('GYM', 'ANTEBRAZOS')
) AS source(module, name)
WHERE NOT EXISTS (
    SELECT 1 FROM training_category category
    WHERE category.system_category AND category.module = source.module
      AND category.normalized_name = lower(source.name)
);

ALTER TABLE training_exercise
    ADD COLUMN code VARCHAR(80),
    ADD COLUMN normalized_name VARCHAR(120),
    ADD COLUMN category_id BIGINT,
    ADD COLUMN equipment VARCHAR(40),
    ADD COLUMN difficulty VARCHAR(20),
    ADD COLUMN registration_type VARCHAR(35),
    ADD COLUMN unilateral BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN external_load BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN system_exercise BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE training_exercise
SET system_exercise = global_exercise,
    normalized_name = lower(trim(name)),
    code = 'SG-LEGACY-' || id,
    difficulty = 'BEGINNER',
    registration_type = CASE WHEN module = 'GYM' THEN 'WEIGHT_AND_REPETITIONS' ELSE 'REPETITIONS' END,
    equipment = CASE WHEN module = 'GYM' THEN 'NONE' ELSE 'BODYWEIGHT' END,
    external_load = (module = 'GYM');

-- Free legacy labels remain reusable personal categories. System labels are shared.
INSERT INTO training_category (owner_id, module, name, normalized_name, system_category)
SELECT DISTINCT exercise.owner_id, exercise.module, trim(exercise.category), lower(trim(exercise.category)), FALSE
FROM training_exercise exercise
WHERE exercise.owner_id IS NOT NULL
  AND exercise.category IS NOT NULL AND trim(exercise.category) <> ''
   AND NOT EXISTS (
       SELECT 1 FROM training_category category
       WHERE category.owner_id = exercise.owner_id
         AND category.module = exercise.module
         AND category.normalized_name = lower(trim(exercise.category))
         AND category.deleted_at IS NULL
   );

UPDATE training_exercise exercise
SET category_id = COALESCE(
    (SELECT category.id FROM training_category category
      WHERE NOT category.system_category AND category.owner_id = exercise.owner_id
        AND category.module = exercise.module AND category.normalized_name = lower(trim(exercise.category))
        AND category.deleted_at IS NULL),
    (SELECT category.id FROM training_category category
     WHERE category.system_category AND category.module = exercise.module
       AND category.normalized_name = CASE
           WHEN exercise.module = 'GYM' AND lower(trim(exercise.category)) = 'piernas' THEN 'cuadriceps'
           WHEN exercise.module = 'CALISTHENICS' AND lower(trim(exercise.category)) = 'espalda' THEN 'traccion'
           ELSE lower(trim(exercise.category)) END),
    (SELECT category.id FROM training_category category
     WHERE category.system_category AND category.module = exercise.module
       AND category.normalized_name = 'acondicionamiento')
 );

UPDATE training_exercise
SET equipment = CASE WHEN module = 'GYM' THEN 'NONE' ELSE 'BODYWEIGHT' END
WHERE equipment IS NULL;

ALTER TABLE training_exercise
    ALTER COLUMN code SET NOT NULL,
    ALTER COLUMN normalized_name SET NOT NULL,
    ALTER COLUMN category_id SET NOT NULL,
    ALTER COLUMN equipment SET NOT NULL,
    ALTER COLUMN difficulty SET NOT NULL,
    ALTER COLUMN registration_type SET NOT NULL,
    ADD CONSTRAINT fk_training_exercise_category FOREIGN KEY (category_id) REFERENCES training_category(id),
    ADD CONSTRAINT chk_training_exercise_difficulty CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    ADD CONSTRAINT chk_training_exercise_registration_type CHECK (
        registration_type IN ('REPETITIONS', 'WEIGHT_AND_REPETITIONS', 'TIME', 'DISTANCE', 'REPETITIONS_AND_TIME')
    ),
    ADD CONSTRAINT chk_training_exercise_equipment CHECK (
        equipment IN ('NONE', 'BODYWEIGHT', 'BARBELL', 'DUMBBELL', 'KETTLEBELL', 'CABLE', 'MACHINE',
                      'BENCH', 'PULL_UP_BAR', 'PARALLEL_BARS', 'RINGS', 'RESISTANCE_BAND', 'BOX',
                      'MEDICINE_BALL', 'JUMP_ROPE', 'TREADMILL', 'STATIONARY_BIKE', 'ROWING_MACHINE',
                      'PLATES', 'TRX', 'FOAM_ROLLER', 'OTHER')
    );

ALTER TABLE training_exercise DROP COLUMN category;
ALTER TABLE training_exercise DROP COLUMN global_exercise;
CREATE UNIQUE INDEX ux_training_exercise_code_live
    ON training_exercise(code) WHERE deleted_at IS NULL;
CREATE INDEX ix_training_exercise_filters
    ON training_exercise(module, category_id, equipment, difficulty, registration_type, active, name)
    WHERE deleted_at IS NULL;

CREATE TABLE training_exercise_primary_muscle (
    exercise_id BIGINT NOT NULL REFERENCES training_exercise(id) ON DELETE CASCADE,
    muscle VARCHAR(80) NOT NULL,
    PRIMARY KEY (exercise_id, muscle)
);
CREATE TABLE training_exercise_secondary_muscle (
    exercise_id BIGINT NOT NULL REFERENCES training_exercise(id) ON DELETE CASCADE,
    muscle VARCHAR(80) NOT NULL,
    PRIMARY KEY (exercise_id, muscle)
);

ALTER TABLE training_preset_exercise
    ADD COLUMN registration_type VARCHAR(35),
    ADD COLUMN target_seconds INTEGER,
    ADD COLUMN target_distance_meters NUMERIC(12, 3);
UPDATE training_preset_exercise preset_exercise
SET registration_type = exercise.registration_type
FROM training_exercise exercise
WHERE exercise.id = preset_exercise.exercise_id;
UPDATE training_preset_exercise SET registration_type = 'REPETITIONS' WHERE registration_type IS NULL;
ALTER TABLE training_preset_exercise
    ALTER COLUMN registration_type SET NOT NULL,
    ADD CONSTRAINT chk_training_preset_exercise_registration_type CHECK (
        registration_type IN ('REPETITIONS', 'WEIGHT_AND_REPETITIONS', 'TIME', 'DISTANCE', 'REPETITIONS_AND_TIME')
    ),
    ADD CONSTRAINT chk_training_preset_exercise_time CHECK (target_seconds IS NULL OR target_seconds >= 0),
    ADD CONSTRAINT chk_training_preset_exercise_distance CHECK (target_distance_meters IS NULL OR target_distance_meters >= 0);

ALTER TABLE training_session_exercise
    ADD COLUMN registration_type VARCHAR(35),
    ADD COLUMN target_seconds INTEGER,
    ADD COLUMN target_distance_meters NUMERIC(12, 3);
UPDATE training_session_exercise session_exercise
SET registration_type = COALESCE(exercise.registration_type, 'REPETITIONS')
FROM training_exercise exercise
WHERE exercise.id = session_exercise.source_exercise_id;
UPDATE training_session_exercise SET registration_type = 'REPETITIONS' WHERE registration_type IS NULL;
ALTER TABLE training_session_exercise
    ALTER COLUMN registration_type SET NOT NULL,
    ADD CONSTRAINT chk_training_session_exercise_registration_type CHECK (
        registration_type IN ('REPETITIONS', 'WEIGHT_AND_REPETITIONS', 'TIME', 'DISTANCE', 'REPETITIONS_AND_TIME')
    ),
    ADD CONSTRAINT chk_training_session_exercise_time CHECK (target_seconds IS NULL OR target_seconds >= 0),
    ADD CONSTRAINT chk_training_session_exercise_distance CHECK (target_distance_meters IS NULL OR target_distance_meters >= 0);

ALTER TABLE training_set
    ADD COLUMN seconds INTEGER,
    ADD COLUMN distance_meters NUMERIC(12, 3),
    ADD CONSTRAINT chk_training_set_seconds CHECK (seconds IS NULL OR seconds >= 0),
    ADD CONSTRAINT chk_training_set_distance CHECK (distance_meters IS NULL OR distance_meters >= 0);
