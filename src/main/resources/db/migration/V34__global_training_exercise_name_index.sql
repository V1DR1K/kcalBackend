CREATE UNIQUE INDEX IF NOT EXISTS ux_training_exercise_system_module_name_active
    ON training_exercise(module, lower(name))
    WHERE deleted_at IS NULL AND system_exercise = TRUE;
