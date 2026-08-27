-- Keep the database constraint aligned with the equipment enum exposed by the API.
ALTER TABLE training_exercise DROP CONSTRAINT chk_training_exercise_equipment;
ALTER TABLE training_exercise
    ADD CONSTRAINT chk_training_exercise_equipment CHECK (
        equipment IN ('NONE', 'BODYWEIGHT', 'BARBELL', 'DUMBBELL', 'KETTLEBELL', 'CABLE', 'MACHINE',
                      'SMITH_MACHINE', 'BENCH', 'PULL_UP_BAR', 'PARALLEL_BARS', 'RINGS', 'PARALLETTES',
                      'RESISTANCE_BAND', 'BOX', 'AB_WHEEL', 'HEX_BAR', 'SLED', 'MEDICINE_BALL',
                      'JUMP_ROPE', 'TREADMILL', 'STATIONARY_BIKE', 'ROWING_MACHINE', 'PLATES', 'TRX',
                      'FOAM_ROLLER', 'OTHER')
    );

UPDATE training_exercise
SET equipment = CASE code
    WHEN 'GYM_AB_WHEEL' THEN 'AB_WHEEL'
    WHEN 'GYM_SMITH_SQUAT' THEN 'SMITH_MACHINE'
    WHEN 'GYM_SMITH_HIP_THRUST' THEN 'SMITH_MACHINE'
    WHEN 'GYM_SMITH_CALF_RAISE' THEN 'SMITH_MACHINE'
    WHEN 'GYM_SLED_PUSH' THEN 'SLED'
    ELSE equipment
END
WHERE code IN ('GYM_AB_WHEEL', 'GYM_SMITH_SQUAT', 'GYM_SMITH_HIP_THRUST', 'GYM_SMITH_CALF_RAISE', 'GYM_SLED_PUSH');
