-- Keep the V26 physical table names.  The application exposes the canonical
-- Plan/PlanDay names while this migration avoids destructive table renames.
ALTER TABLE training_preset
    ADD COLUMN frequency_mode VARCHAR(12) NOT NULL DEFAULT 'FIXED',
    ADD COLUMN target_sessions_per_week INTEGER;

ALTER TABLE training_preset
    ADD COLUMN start_date DATE,
    ADD COLUMN end_date DATE;

UPDATE training_preset
SET start_date = created_at::date
WHERE start_date IS NULL;

WITH ranked_active_plans AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY owner_id, module
               ORDER BY active DESC, updated_at DESC, id DESC
           ) AS plan_rank
    FROM training_preset
    WHERE deleted_at IS NULL
)
UPDATE training_preset plan
SET active = FALSE,
    end_date = CASE
        WHEN plan.created_at::date < CURRENT_DATE THEN CURRENT_DATE - 1
        ELSE plan.created_at::date
    END
FROM ranked_active_plans ranked
WHERE plan.id = ranked.id
  AND ranked.plan_rank > 1
  AND plan.active = TRUE;

UPDATE training_preset
SET target_sessions_per_week = COALESCE(
        (SELECT GREATEST(count(*), 1)::integer FROM training_day day
         WHERE day.preset_id = training_preset.id
           AND day.deleted_at IS NULL), 0)
WHERE target_sessions_per_week IS NULL;

ALTER TABLE training_preset
    ALTER COLUMN target_sessions_per_week SET NOT NULL;

ALTER TABLE training_preset
    ADD CONSTRAINT chk_training_preset_frequency_mode
        CHECK (frequency_mode IN ('FIXED', 'DYNAMIC')),
    ADD CONSTRAINT chk_training_preset_target_sessions
        CHECK (target_sessions_per_week > 0),
    ADD CONSTRAINT chk_training_preset_dates
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date);

ALTER TABLE training_day
    ADD CONSTRAINT chk_training_day_fixed_unique_guard
        CHECK (day_of_week IS NULL OR day_of_week IN
            ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'));

CREATE UNIQUE INDEX ux_training_day_plan_fixed_day_live
    ON training_day(preset_id, day_of_week)
    WHERE deleted_at IS NULL AND day_of_week IS NOT NULL;

ALTER TABLE training_session
    DROP CONSTRAINT chk_training_session_status,
    ADD CONSTRAINT chk_training_session_status
        CHECK (status IN ('STARTED', 'COMPLETED', 'CANCELLED', 'SKIPPED'));

ALTER TABLE training_exercise
    ALTER COLUMN owner_id DROP NOT NULL,
    ADD COLUMN global_exercise BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_training_exercise_visibility
        CHECK ((global_exercise AND owner_id IS NULL) OR (NOT global_exercise AND owner_id IS NOT NULL));

DROP INDEX IF EXISTS ux_training_exercise_owner_module_name_active;
CREATE UNIQUE INDEX ux_training_exercise_owner_module_name_active
    ON training_exercise(owner_id, module, lower(name))
    WHERE deleted_at IS NULL AND global_exercise = FALSE;
CREATE UNIQUE INDEX ux_training_exercise_global_module_name_active
    ON training_exercise(module, lower(name))
    WHERE deleted_at IS NULL AND global_exercise = TRUE;

-- A small immutable starter catalog. User-created exercises remain owned rows.
INSERT INTO training_exercise (owner_id, name, description, category, module, global_exercise)
SELECT NULL, source.name, source.description, source.category, source.module, TRUE
FROM (VALUES
    ('Press banca', 'Empuje horizontal con barra.', 'PECHO', 'GYM'),
    ('Sentadilla', 'Sentadilla libre.', 'PIERNAS', 'GYM'),
    ('Dominadas', 'Tracción vertical con el peso corporal.', 'ESPALDA', 'CALISTHENICS'),
    ('Fondos', 'Empuje en paralelas.', 'EMPUJE', 'CALISTHENICS')
) AS source(name, description, category, module)
WHERE NOT EXISTS (
    SELECT 1 FROM training_exercise exercise
    WHERE exercise.global_exercise = TRUE
      AND exercise.module = source.module
      AND lower(exercise.name) = lower(source.name)
);
