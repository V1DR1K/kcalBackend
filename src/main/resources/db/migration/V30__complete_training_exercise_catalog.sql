-- Complete the canonical order catalog without replacing rows created by V27-V29.
-- V29 leaves 89 system rows (180 GYM + 115 CALISTHENICS are required in total),
-- so this migration contributes 133 GYM rows and 73 CALISTHENICS rows.

-- V29 used broader temporary labels. Keep its rows, but put them in the
-- canonical blocks used by the order catalog.
UPDATE training_exercise exercise
SET category_id = category.id
FROM training_category category
WHERE category.system_category
  AND category.module = exercise.module
  AND exercise.system_exercise
  AND (
      (exercise.module = 'GYM' AND exercise.category_id IN (
          SELECT id FROM training_category
          WHERE module = 'GYM' AND normalized_name = 'acondicionamiento' AND system_category
      ) AND category.normalized_name = 'cuerpo_completo')
      OR (exercise.code IN ('CAL_PLANCHE', 'CAL_TUCK_PLANCHE')
          AND category.normalized_name = 'habilidad')
      OR (exercise.code IN ('CAL_FRONT_LEVER', 'CAL_BACK_LEVER')
          AND category.normalized_name = 'habilidad')
      OR (exercise.code IN ('CAL_MUSCLE_UP', 'CAL_HANDSTAND', 'CAL_L_SIT', 'CAL_SKIN_THE_CAT',
                            'CAL_HANDSTAND_PUSH_UP') AND category.normalized_name = 'habilidad')
      OR (exercise.code IN ('CAL_COSSACK_SQUAT', 'CAL_DEEP_SQUAT_HOLD')
          AND category.normalized_name = 'piernas')
      OR (exercise.code = 'CAL_SHOULDER_DISLOCATES' AND category.normalized_name = 'empuje')
      OR (exercise.code = 'CAL_CAT_COW' AND category.normalized_name = 'abdomen')
  );

-- Registration follows the order rules, including correcting V29's cardio
-- and calisthenics distance/time values.
UPDATE training_exercise
SET registration_type = CASE
    WHEN module = 'GYM' AND code = 'GYM_FARMERS_WALK' THEN 'DISTANCE'
    WHEN module = 'GYM' AND code = 'GYM_FARMERS_HOLD' THEN 'TIME'
    WHEN module = 'GYM' THEN 'WEIGHT_AND_REPETITIONS'
    WHEN code IN ('CAL_PLANCHE', 'CAL_FRONT_LEVER', 'CAL_BACK_LEVER', 'CAL_HANDSTAND', 'CAL_L_SIT',
                  'CAL_DEAD_HANG', 'CAL_PLANK', 'CAL_HOLLOW_HOLD', 'CAL_SKIN_THE_CAT',
                  'CAL_SIDE_PLANK', 'CAL_REVERSE_PLANK', 'CAL_TUCK_PLANCHE', 'CAL_DEEP_SQUAT_HOLD')
        THEN 'TIME'
    ELSE 'REPETITIONS'
END
WHERE system_exercise;

INSERT INTO training_exercise
    (owner_id, name, description, module, active, code, normalized_name, category_id,
     equipment, difficulty, registration_type, unilateral, external_load, system_exercise)
SELECT NULL, source.name, source.description, source.module, TRUE, source.code, lower(source.name), category.id,
       source.equipment, source.difficulty, source.registration_type, source.unilateral, source.external_load, TRUE
FROM (VALUES
    -- GYM / PECHO (10)
    ('Press declinado con barra','Empuje declinado para el pectoral inferior.','GYM','GYM_DECLINE_BENCH_PRESS','PECHO','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press declinado con mancuernas','Empuje declinado unilateralmente controlable.','GYM','GYM_DECLINE_DB_PRESS','PECHO','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press de pecho en máquina','Empuje horizontal guiado para pectorales.','GYM','GYM_MACHINE_CHEST_PRESS','PECHO','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Pec deck','Aducción horizontal guiada para pectorales.','GYM','GYM_PEC_DECK','PECHO','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Aperturas en polea baja','Apertura de pectoral desde un ángulo bajo.','GYM','GYM_LOW_CABLE_FLY','PECHO','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Squeeze press','Press con mancuernas juntas para el pectoral.','GYM','GYM_SQUEEZE_PRESS','PECHO','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press unilateral en polea','Empuje horizontal unilateral en polea.','GYM','GYM_SINGLE_ARM_CABLE_PRESS','PECHO','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Press de suelo con mancuernas','Press horizontal con recorrido limitado.','GYM','GYM_DUMBBELL_FLOOR_PRESS','PECHO','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press convergente','Press de pecho guiado con convergencia.','GYM','GYM_CONVERGING_CHEST_PRESS','PECHO','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press con mancuernas agarre neutro','Press de pecho con agarre neutro.','GYM','GYM_NEUTRAL_GRIP_DB_PRESS','PECHO','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / ESPALDA (9)
    ('Remo en T','Tracción horizontal con apoyo y barra en T.','GYM','GYM_T_BAR_ROW','ESPALDA','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Remo con barra landmine','Remo horizontal con un extremo de la barra fijado.','GYM','GYM_LANDMINE_ROW','ESPALDA','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Remo pecho apoyado','Remo con el torso estabilizado en banco.','GYM','GYM_CHEST_SUPPORTED_ROW','ESPALDA','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Remo unilateral en polea','Tracción horizontal unilateral en polea.','GYM','GYM_SINGLE_ARM_CABLE_ROW','ESPALDA','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Jalón cerrado al pecho','Jalón vertical con agarre cerrado.','GYM','GYM_CLOSE_GRIP_LAT_PULLDOWN','ESPALDA','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Jalón ancho al pecho','Jalón vertical con agarre amplio.','GYM','GYM_WIDE_GRIP_LAT_PULLDOWN','ESPALDA','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Jalón con brazos rectos','Extensión de hombro para dorsales en polea.','GYM','GYM_STRAIGHT_ARM_PUSHDOWN','ESPALDA','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Remo en máquina con apoyo','Tracción horizontal guiada con apoyo frontal.','GYM','GYM_SUPPORTED_MACHINE_ROW','ESPALDA','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Hiperextensiones','Extensión de cadera y columna en banco.','GYM','GYM_BACK_EXTENSION','ESPALDA','BENCH','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,FALSE),

    -- GYM / HOMBROS (11)
    ('Press Arnold','Press de hombros con rotación de mancuernas.','GYM','GYM_ARNOLD_PRESS','HOMBROS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press sentado con mancuernas','Empuje vertical sentado con mancuernas.','GYM','GYM_SEATED_DB_SHOULDER_PRESS','HOMBROS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press de hombros en máquina','Empuje vertical guiado para deltoides.','GYM','GYM_MACHINE_SHOULDER_PRESS','HOMBROS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación frontal con mancuernas','Flexión de hombro con mancuernas.','GYM','GYM_FRONT_RAISE_DB','HOMBROS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación frontal en polea','Flexión de hombro con resistencia constante.','GYM','GYM_CABLE_FRONT_RAISE','HOMBROS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Remo al mentón','Elevación de la barra con codos altos.','GYM','GYM_UPRIGHT_ROW','HOMBROS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación lateral en polea','Abducción de hombro unilateral en polea.','GYM','GYM_CABLE_LATERAL_RAISE','HOMBROS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Pájaros en máquina','Abducción horizontal para deltoides posteriores.','GYM','GYM_REVERSE_PEC_DECK','HOMBROS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación Y inclinada','Elevación diagonal para hombros y espalda alta.','GYM','GYM_INCLINE_Y_RAISE','HOMBROS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Encogimientos con barra','Elevación de hombros para trapecios.','GYM','GYM_BARBELL_SHRUG','HOMBROS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Remo posterior en polea','Tracción alta para deltoides posteriores.','GYM','GYM_CABLE_REAR_DELT_ROW','HOMBROS','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / BICEPS (12)
    ('Curl predicador','Flexión de codo con apoyo del brazo.','GYM','GYM_PREACHER_CURL','BICEPS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl spider','Curl con el pecho apoyado y brazos verticales.','GYM','GYM_SPIDER_CURL','BICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl en polea baja','Flexión de codo con tensión continua.','GYM','GYM_CABLE_CURL','BICEPS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl de concentración','Curl unilateral sentado con apoyo del codo.','GYM','GYM_CONCENTRATION_CURL','BICEPS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Curl inverso','Flexión de codo con agarre prono.','GYM','GYM_REVERSE_CURL','BICEPS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl Zottman','Curl con subida supina y bajada prona.','GYM','GYM_ZOTTMAN_CURL','BICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl martillo inclinado','Curl neutro con el brazo extendido atrás.','GYM','GYM_INCLINE_HAMMER_CURL','BICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl alterno con mancuernas','Flexión alterna de codos con mancuernas.','GYM','GYM_ALTERNATING_DB_CURL','BICEPS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl alto en polea','Curl con los brazos elevados en polea.','GYM','GYM_HIGH_CABLE_CURL','BICEPS','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl de arrastre','Curl llevando los codos hacia atrás.','GYM','GYM_DRAG_CURL','BICEPS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl con barra EZ','Curl de bíceps con barra curva.','GYM','GYM_EZ_BAR_CURL','BICEPS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl de bíceps en máquina','Flexión de codo guiada en máquina.','GYM','GYM_MACHINE_BICEPS_CURL','BICEPS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / TRICEPS (12)
    ('Press banca agarre cerrado','Press con agarre cerrado para tríceps.','GYM','GYM_CLOSE_GRIP_BENCH_PRESS','TRICEPS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Fondos asistidos en máquina','Extensión de codo asistida en máquina.','GYM','GYM_ASSISTED_DIP_MACHINE','TRICEPS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press francés con mancuernas','Extensión de codos tumbado con mancuernas.','GYM','GYM_DB_SKULL_CRUSHER','TRICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Extensión sobre cabeza con mancuerna','Extensión de tríceps por encima de la cabeza.','GYM','GYM_DB_OVERHEAD_TRICEPS_EXTENSION','TRICEPS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Patada de tríceps en polea','Extensión de codo hacia atrás en polea.','GYM','GYM_CABLE_TRICEPS_KICKBACK','TRICEPS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Extensión con cuerda','Extensión de tríceps con cuerda.','GYM','GYM_ROPE_TRICEPS_PUSHDOWN','TRICEPS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Extensión inversa en polea','Extensión de codo con agarre supino.','GYM','GYM_REVERSE_GRIP_PUSHDOWN','TRICEPS','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Extensión unilateral en polea','Extensión de codo unilateral en polea.','GYM','GYM_SINGLE_ARM_TRICEPS_PUSHDOWN','TRICEPS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Extensión cruzada de tríceps','Extensión unilateral cruzando el cuerpo.','GYM','GYM_CROSS_BODY_TRICEPS_EXTENSION','TRICEPS','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Press JM','Press corto con énfasis en tríceps.','GYM','GYM_JM_PRESS','TRICEPS','BARBELL','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press Tate','Extensión de tríceps con mancuernas sobre el pecho.','GYM','GYM_TATE_PRESS','TRICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Extensión de tríceps en máquina','Extensión de codo guiada.','GYM','GYM_MACHINE_TRICEPS_EXTENSION','TRICEPS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / CUADRICEPS (10)
    ('Sentadilla frontal','Sentadilla con la carga delante del torso.','GYM','GYM_FRONT_SQUAT','CUADRICEPS','BARBELL','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Sentadilla hack','Sentadilla guiada con énfasis en cuádriceps.','GYM','GYM_HACK_SQUAT','CUADRICEPS','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Sentadilla en máquina Smith','Sentadilla guiada con barra.','GYM','GYM_SMITH_SQUAT','CUADRICEPS','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Sentadilla goblet','Sentadilla frontal con mancuerna.','GYM','GYM_GOBLET_SQUAT','CUADRICEPS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Sentadilla sissy','Flexión profunda de rodilla con peso corporal.','GYM','GYM_SISSY_SQUAT','CUADRICEPS','BODYWEIGHT','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Subida al banco con peso','Subida unilateral a un banco con carga.','GYM','GYM_WEIGHTED_STEP_UP','CUADRICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Extensión unilateral de cuádriceps','Extensión de rodilla unilateral en máquina.','GYM','GYM_SINGLE_LEG_EXTENSION','CUADRICEPS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Zancada inversa con barra','Zancada hacia atrás con barra.','GYM','GYM_BAR_REVERSE_LUNGE','CUADRICEPS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Zancada caminando con mancuernas','Zancada alterna con mancuernas.','GYM','GYM_DB_WALKING_LUNGE','CUADRICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Sentadilla belt squat','Sentadilla con carga suspendida en la cadera.','GYM','GYM_BELT_SQUAT','CUADRICEPS','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / ISQUIOTIBIALES (12)
    ('Buenos días','Bisagra de cadera con barra sobre los hombros.','GYM','GYM_GOOD_MORNING','ISQUIOTIBIALES','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl femoral tumbado','Flexión de rodilla tumbado en máquina.','GYM','GYM_LYING_LEG_CURL','ISQUIOTIBIALES','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl femoral sentado','Flexión de rodilla sentado en máquina.','GYM','GYM_SEATED_LEG_CURL','ISQUIOTIBIALES','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl femoral de pie','Flexión unilateral de rodilla de pie.','GYM','GYM_STANDING_LEG_CURL','ISQUIOTIBIALES','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Elevación de glúteos e isquios','Extensión de cadera y flexión de rodilla.','GYM','GYM_GLUTE_HAM_RAISE','ISQUIOTIBIALES','MACHINE','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Pull through en polea','Extensión de cadera desde una bisagra.','GYM','GYM_CABLE_PULL_THROUGH','ISQUIOTIBIALES','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Peso muerto rumano unilateral','Bisagra unilateral con mancuerna.','GYM','GYM_SINGLE_LEG_RDL','ISQUIOTIBIALES','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Peso muerto rumano con kettlebell','Bisagra de cadera con kettlebell.','GYM','GYM_KETTLEBELL_RDL','ISQUIOTIBIALES','KETTLEBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Extensión de cadera en máquina','Extensión de cadera guiada.','GYM','GYM_MACHINE_HIP_EXTENSION','ISQUIOTIBIALES','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Hiperextensión inversa','Extensión de cadera en máquina de hiperextensión.','GYM','GYM_REVERSE_HYPEREXTENSION','ISQUIOTIBIALES','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Deslizamiento de isquiotibiales','Flexión de rodilla con pies deslizantes.','GYM','GYM_HAMSTRING_SLIDER','ISQUIOTIBIALES','FOAM_ROLLER','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,FALSE),

    -- GYM / GLUTEOS (12)
    ('Puente de glúteos con barra','Extensión de cadera con barra sobre la pelvis.','GYM','GYM_BARBELL_GLUTE_BRIDGE','GLUTEOS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Peso muerto sumo','Peso muerto con postura amplia y énfasis en cadera.','GYM','GYM_SUMO_DEADLIFT','GLUTEOS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Hip thrust en Smith','Extensión de cadera guiada con barra.','GYM','GYM_SMITH_HIP_THRUST','GLUTEOS','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Hip thrust unilateral','Extensión de cadera unilateral.','GYM','GYM_SINGLE_LEG_HIP_THRUST','GLUTEOS','BENCH','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,FALSE),
    ('Subida al banco para glúteos','Subida controlada con énfasis en extensión de cadera.','GYM','GYM_GLUTE_STEP_UP','GLUTEOS','BOX','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,FALSE),
    ('Zancada inversa para glúteos','Zancada hacia atrás con énfasis en cadera.','GYM','GYM_GLUTE_REVERSE_LUNGE','GLUTEOS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Patada de glúteo en máquina','Extensión de cadera unilateral guiada.','GYM','GYM_MACHINE_GLUTE_KICKBACK','GLUTEOS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Glute drive','Extensión de cadera en máquina específica.','GYM','GYM_GLUTE_DRIVE','GLUTEOS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Frog pumps con peso','Extensión de cadera con plantas juntas.','GYM','GYM_WEIGHTED_FROG_PUMPS','GLUTEOS','PLATES','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Abducción de cadera en polea','Abducción unilateral de cadera en polea.','GYM','GYM_CABLE_HIP_ABDUCTION','GLUTEOS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Extensión de cadera en banco','Extensión de cadera con apoyo en banco.','GYM','GYM_BENCH_HIP_EXTENSION','GLUTEOS','BENCH','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Buenos días para glúteos','Bisagra de cadera con postura enfocada en glúteos.','GYM','GYM_GLUTE_GOOD_MORNING','GLUTEOS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / PANTORRILLAS (13)
    ('Elevación de talones de pie en máquina','Flexión plantar de pie en máquina.','GYM','GYM_STANDING_MACHINE_CALF_RAISE','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación de talones tipo burro','Flexión plantar inclinada hacia delante.','GYM','GYM_DONKEY_CALF_RAISE','PANTORRILLAS','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación de talones en prensa','Flexión plantar usando la plataforma de prensa.','GYM','GYM_LEG_PRESS_CALF_RAISE','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación de talones en Smith','Flexión plantar con barra guiada.','GYM','GYM_SMITH_CALF_RAISE','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación unilateral de talón en máquina','Flexión plantar unilateral guiada.','GYM','GYM_SINGLE_LEG_MACHINE_CALF_RAISE','PANTORRILLAS','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Elevación de tibial en máquina','Dorsiflexión de tobillo en máquina.','GYM','GYM_MACHINE_TIBIAL_RAISE','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación de talones sentado con mancuerna','Flexión plantar sentado con mancuerna.','GYM','GYM_DB_SEATED_CALF_RAISE','PANTORRILLAS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación de talones en hack','Flexión plantar en máquina hack.','GYM','GYM_HACK_CALF_RAISE','PANTORRILLAS','MACHINE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación de talones horizontal','Flexión plantar en prensa horizontal.','GYM','GYM_HORIZONTAL_CALF_PRESS','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Press de sóleo','Flexión plantar sentado con énfasis en sóleo.','GYM','GYM_SOLEUS_PRESS','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación de talones con barra','Flexión plantar con barra libre.','GYM','GYM_BARBELL_CALF_RAISE','PANTORRILLAS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Elevación unilateral sentado','Flexión plantar unilateral sentado.','GYM','GYM_SINGLE_LEG_SEATED_CALF_RAISE','PANTORRILLAS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Elevación de talones en multipower','Flexión plantar guiada en multipower.','GYM','GYM_GUIDED_CALF_RAISE','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / ABDOMEN (11)
    ('Elevación de rodillas colgado','Flexión de cadera colgado de una barra.','GYM','GYM_HANGING_KNEE_RAISE','ABDOMEN','PULL_UP_BAR','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Sit-up declinado','Flexión de tronco en banco declinado.','GYM','GYM_DECLINE_SIT_UP','ABDOMEN','BENCH','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Sit-up con peso','Flexión de tronco con carga adicional.','GYM','GYM_WEIGHTED_SIT_UP','ABDOMEN','PLATES','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Leñador en polea','Rotación diagonal del tronco en polea.','GYM','GYM_CABLE_WOODCHOP','ABDOMEN','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Crunch en máquina','Flexión de tronco guiada en máquina.','GYM','GYM_MACHINE_CRUNCH','ABDOMEN','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Crunch abdominal en máquina','Flexión resistida de la columna.','GYM','GYM_AB_CRUNCH_MACHINE','ABDOMEN','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Crunch inverso','Retroversión pélvica tumbado.','GYM','GYM_REVERSE_CRUNCH','ABDOMEN','BENCH','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Toes to bar','Elevación de pies hasta la barra.','GYM','GYM_TOES_TO_BAR','ABDOMEN','PULL_UP_BAR','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Limpiaparabrisas','Rotación de piernas colgado.','GYM','GYM_WINDSHIELD_WIPERS','ABDOMEN','PULL_UP_BAR','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Inclinación lateral en polea','Inclinación lateral del tronco con carga.','GYM','GYM_CABLE_SIDE_BEND','ABDOMEN','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Crunch oblicuo en polea','Flexión y rotación oblicua en polea.','GYM','GYM_CABLE_OBLIQUE_CRUNCH','ABDOMEN','CABLE','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),

    -- GYM / ANTEBRAZOS (13)
    ('Curl de muñeca inverso','Extensión de muñeca con barra.','GYM','GYM_REVERSE_WRIST_CURL','ANTEBRAZOS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Rodillo de muñeca','Flexión y extensión de muñeca con rodillo.','GYM','GYM_WRIST_ROLLER','ANTEBRAZOS','PLATES','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl de muñeca detrás de la espalda','Flexión de muñeca con barra posterior.','GYM','GYM_BEHIND_BACK_WRIST_CURL','ANTEBRAZOS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl de muñeca en polea','Flexión de muñeca con polea baja.','GYM','GYM_CABLE_WRIST_CURL','ANTEBRAZOS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl de dedos con barra','Flexión de dedos para el agarre.','GYM','GYM_BAR_FINGER_CURL','ANTEBRAZOS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Apretón de pelota','Cierre repetido de la mano contra una pelota.','GYM','GYM_GRIP_BALL_SQUEEZE','ANTEBRAZOS','OTHER','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,FALSE),
    ('Caminata granjero con barra','Desplazamiento cargado con barra.','GYM','GYM_BARBELL_FARMERS_WALK','ANTEBRAZOS','BARBELL','BEGINNER','DISTANCE',FALSE,TRUE),
    ('Pinza de discos','Sujeción y manipulación repetida de discos.','GYM','GYM_PLATE_PINCH_REPETITIONS','ANTEBRAZOS','PLATES','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl radial de muñeca','Desviación radial resistida de muñeca.','GYM','GYM_RADIAL_WRIST_CURL','ANTEBRAZOS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Curl cubital de muñeca','Desviación cubital resistida de muñeca.','GYM','GYM_ULNAR_WRIST_CURL','ANTEBRAZOS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Pronación de antebrazo','Rotación del antebrazo contra resistencia.','GYM','GYM_FOREARM_PRONATION','ANTEBRAZOS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Supinación de antebrazo','Rotación supina del antebrazo contra resistencia.','GYM','GYM_FOREARM_SUPINATION','ANTEBRAZOS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Agarre con toalla','Cierre repetido de la mano sobre una toalla lastrada.','GYM','GYM_TOWEL_GRIP_REPETITIONS','ANTEBRAZOS','OTHER','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- GYM / CUERPO_COMPLETO (9)
    ('Cargada y press','Cargada desde el suelo seguida de un press.','GYM','GYM_CLEAN_AND_PRESS','CUERPO_COMPLETO','BARBELL','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Complejo con barra','Secuencia continua de movimientos con barra.','GYM','GYM_BARBELL_COMPLEX','CUERPO_COMPLETO','BARBELL','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Arrancada con mancuerna','Levantamiento explosivo unilateral desde el suelo.','GYM','GYM_DUMBBELL_SNATCH','CUERPO_COMPLETO','DUMBBELL','ADVANCED','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Arrancada con barra','Levantamiento olímpico desde el suelo.','GYM','GYM_BARBELL_SNATCH','CUERPO_COMPLETO','BARBELL','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Tirón alto','Tirón explosivo de barra hasta el pecho.','GYM','GYM_CLEAN_PULL','CUERPO_COMPLETO','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Levantamiento turco','Transición desde el suelo hasta la posición de pie.','GYM','GYM_TURKISH_GET_UP','CUERPO_COMPLETO','KETTLEBELL','ADVANCED','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
    ('Empuje de trineo','Desplazamiento del trineo con impulso de piernas.','GYM','GYM_SLED_PUSH','CUERPO_COMPLETO','OTHER','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Saltos al cajón con peso','Salto al cajón sosteniendo mancuernas.','GYM','GYM_WEIGHTED_BOX_JUMP','CUERPO_COMPLETO','BOX','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
    ('Man maker','Secuencia de flexión, remo y cargada con mancuernas.','GYM','GYM_MAN_MAKER','CUERPO_COMPLETO','DUMBBELL','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),

    -- CALISTHENICS / EMPUJE (12)
    ('Flexiones de rodillas','Flexión horizontal asistida con las rodillas apoyadas.','CALISTHENICS','CAL_KNEE_PUSH_UP','EMPUJE','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Flexiones inclinadas','Flexión horizontal con las manos elevadas.','CALISTHENICS','CAL_INCLINE_PUSH_UP','EMPUJE','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Flexiones declinadas','Flexión horizontal con los pies elevados.','CALISTHENICS','CAL_DECLINE_PUSH_UP','EMPUJE','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Flexiones abiertas','Flexión horizontal con manos separadas.','CALISTHENICS','CAL_WIDE_PUSH_UP','EMPUJE','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Flexiones escalonadas','Flexión con una mano adelantada respecto a la otra.','CALISTHENICS','CAL_STAGGERED_PUSH_UP','EMPUJE','BODYWEIGHT','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
    ('Flexiones arqueras','Flexión con transferencia hacia un brazo.','CALISTHENICS','CAL_ARCHER_PUSH_UP','EMPUJE','BODYWEIGHT','ADVANCED','REPETITIONS',TRUE,FALSE),
    ('Flexiones explosivas','Flexión con despegue de las manos.','CALISTHENICS','CAL_EXPLOSIVE_PUSH_UP','EMPUJE','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Flexiones con palmada','Flexión explosiva con palmada.','CALISTHENICS','CAL_CLAP_PUSH_UP','EMPUJE','BODYWEIGHT','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Flexiones en pica elevadas','Progresión de empuje vertical con pies elevados.','CALISTHENICS','CAL_ELEVATED_PIKE_PUSH_UP','EMPUJE','BOX','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Flexiones verticales asistidas','Empuje vertical invertido con asistencia.','CALISTHENICS','CAL_ASSISTED_HANDSTAND_PUSH_UP','EMPUJE','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Fondos en banco','Extensión de codo usando un banco.','CALISTHENICS','CAL_BENCH_DIP','EMPUJE','BENCH','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Fondos en barra recta','Empuje sobre una barra horizontal.','CALISTHENICS','CAL_STRAIGHT_BAR_DIP','EMPUJE','PULL_UP_BAR','ADVANCED','REPETITIONS',FALSE,FALSE),

    -- CALISTHENICS / TRACCION (12)
    ('Dominadas negativas','Descenso controlado desde la posición superior.','CALISTHENICS','CAL_NEGATIVE_PULL_UP','TRACCION','PULL_UP_BAR','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Dominadas escapulares','Depresión y elevación escapular colgado.','CALISTHENICS','CAL_SCAPULAR_PULL_UP','TRACCION','PULL_UP_BAR','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Dominadas comando','Dominada con las manos enfrentadas sobre la barra.','CALISTHENICS','CAL_COMMANDO_PULL_UP','TRACCION','PULL_UP_BAR','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
    ('Dominadas agarre ancho','Tracción vertical con agarre amplio.','CALISTHENICS','CAL_WIDE_PULL_UP','TRACCION','PULL_UP_BAR','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Dominadas agarre cerrado','Tracción vertical con manos juntas.','CALISTHENICS','CAL_CLOSE_GRIP_PULL_UP','TRACCION','PULL_UP_BAR','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Dominadas typewriter','Desplazamiento lateral en la posición superior.','CALISTHENICS','CAL_TYPEWRITER_PULL_UP','TRACCION','PULL_UP_BAR','ADVANCED','REPETITIONS',TRUE,FALSE),
    ('Dominadas asistidas','Tracción vertical con asistencia elástica.','CALISTHENICS','CAL_ASSISTED_PULL_UP','TRACCION','RESISTANCE_BAND','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Dominadas con toalla','Tracción vertical sujetando una toalla.','CALISTHENICS','CAL_TOWEL_PULL_UP','TRACCION','PULL_UP_BAR','ADVANCED','REPETITIONS',TRUE,FALSE),
    ('Dominada unilateral asistida','Tracción con un brazo y apoyo del otro.','CALISTHENICS','CAL_ASSISTED_ONE_ARM_PULL_UP','TRACCION','PULL_UP_BAR','ADVANCED','REPETITIONS',TRUE,FALSE),
    ('Dominadas explosivas','Tracción explosiva por encima de la barra.','CALISTHENICS','CAL_EXPLOSIVE_PULL_UP','TRACCION','PULL_UP_BAR','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Remo en anillas con pies elevados','Remo horizontal con mayor inclinación corporal.','CALISTHENICS','CAL_FEET_ELEVATED_RING_ROW','TRACCION','RINGS','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Remo australiano unilateral','Remo horizontal con apoyo en un brazo.','CALISTHENICS','CAL_SINGLE_ARM_AUSTRALIAN_ROW','TRACCION','PULL_UP_BAR','ADVANCED','REPETITIONS',TRUE,FALSE),

    -- CALISTHENICS / HABILIDAD (9)
    ('Inclinación de planche','Inclinación hacia delante con brazos extendidos.','CALISTHENICS','CAL_PLANCHE_LEAN','HABILIDAD','PARALLEL_BARS','BEGINNER','TIME',FALSE,FALSE),
    ('Frog stand','Equilibrio en cuclillas sobre las manos.','CALISTHENICS','CAL_FROG_STAND','HABILIDAD','PARALLEL_BARS','BEGINNER','TIME',FALSE,FALSE),
    ('Flexión tuck planche','Flexión manteniendo las rodillas recogidas.','CALISTHENICS','CAL_TUCK_PLANCHE_PUSH_UP','HABILIDAD','PARALLEL_BARS','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Planche tuck avanzada','Sostén con cadera extendida y rodillas recogidas.','CALISTHENICS','CAL_ADVANCED_TUCK_PLANCHE','HABILIDAD','PARALLEL_BARS','ADVANCED','TIME',FALSE,FALSE),
    ('Planche straddle','Sostén de planche con piernas separadas.','CALISTHENICS','CAL_STRADDLE_PLANCHE','HABILIDAD','PARALLEL_BARS','ADVANCED','TIME',FALSE,FALSE),
    ('Flexión de planche','Flexión completa desde la posición de planche.','CALISTHENICS','CAL_PLANCHE_PUSH_UP','HABILIDAD','PARALLEL_BARS','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Inclinación de planche con flexión','Flexión dinámica desde una inclinación de planche.','CALISTHENICS','CAL_PLANCHE_LEAN_PUSH_UP','HABILIDAD','PARALLEL_BARS','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Planche con brazos flexionados','Sostén de planche con codos flexionados.','CALISTHENICS','CAL_BENT_ARM_PLANCHE','HABILIDAD','PARALLEL_BARS','ADVANCED','TIME',FALSE,FALSE),
    ('Press a planche','Transición dinámica desde apoyo a planche.','CALISTHENICS','CAL_PRESS_TO_PLANCHE','HABILIDAD','PARALLEL_BARS','ADVANCED','REPETITIONS',FALSE,FALSE),

    -- CALISTHENICS / HABILIDAD (8)
    ('Front lever tuck','Sostén de palanca frontal con rodillas recogidas.','CALISTHENICS','CAL_TUCK_FRONT_LEVER','HABILIDAD','PULL_UP_BAR','INTERMEDIATE','TIME',FALSE,FALSE),
    ('Front lever tuck avanzada','Sostén de palanca frontal con cadera abierta.','CALISTHENICS','CAL_ADVANCED_TUCK_FRONT_LEVER','HABILIDAD','PULL_UP_BAR','ADVANCED','TIME',FALSE,FALSE),
    ('Front lever straddle','Sostén de palanca frontal con piernas separadas.','CALISTHENICS','CAL_STRADDLE_FRONT_LEVER','HABILIDAD','PULL_UP_BAR','ADVANCED','TIME',FALSE,FALSE),
    ('Elevación a front lever','Elevación dinámica desde suspensión a palanca frontal.','CALISTHENICS','CAL_FRONT_LEVER_RAISE','HABILIDAD','PULL_UP_BAR','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Remo en front lever','Remo manteniendo el cuerpo en palanca frontal.','CALISTHENICS','CAL_FRONT_LEVER_PULL','HABILIDAD','PULL_UP_BAR','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Back lever tuck','Sostén de palanca posterior con rodillas recogidas.','CALISTHENICS','CAL_TUCK_BACK_LEVER','HABILIDAD','RINGS','INTERMEDIATE','TIME',FALSE,FALSE),
    ('Elevación a back lever','Transición dinámica hacia la palanca posterior.','CALISTHENICS','CAL_BACK_LEVER_RAISE','HABILIDAD','RINGS','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Ice cream maker','Transición dinámica entre suspensión y palanca.','CALISTHENICS','CAL_ICE_CREAM_MAKER','HABILIDAD','PULL_UP_BAR','ADVANCED','REPETITIONS',FALSE,FALSE),

    -- CALISTHENICS / ABDOMEN (10)
    ('Crunch inverso','Retroversión pélvica tumbado.','CALISTHENICS','CAL_REVERSE_CRUNCH','ABDOMEN','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Abdominales','Flexión de tronco desde el suelo.','CALISTHENICS','CAL_SIT_UP','ABDOMEN','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('V-up','Flexión simultánea de tronco y cadera.','CALISTHENICS','CAL_V_UP','ABDOMEN','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Limpiaparabrisas colgado','Rotación controlada de piernas colgado.','CALISTHENICS','CAL_HANGING_WINDSHIELD_WIPER','ABDOMEN','PULL_UP_BAR','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Bicicleta abdominal','Flexión y rotación alterna del tronco.','CALISTHENICS','CAL_BICYCLE_CRUNCH','ABDOMEN','BODYWEIGHT','BEGINNER','REPETITIONS',TRUE,FALSE),
    ('Toques de hombro en plancha','Toque alterno de hombros desde plancha.','CALISTHENICS','CAL_PLANK_SHOULDER_TAP','ABDOMEN','BODYWEIGHT','BEGINNER','REPETITIONS',TRUE,FALSE),
    ('Hollow rocks','Balanceo manteniendo la forma hollow.','CALISTHENICS','CAL_HOLLOW_ROCKS','ABDOMEN','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Arch hold','Sostén de extensión corporal para la cadena posterior.','CALISTHENICS','CAL_ARCH_HOLD','ABDOMEN','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
    ('Elevación de cadera lateral','Elevación de cadera desde plancha lateral.','CALISTHENICS','CAL_SIDE_PLANK_HIP_LIFT','ABDOMEN','BODYWEIGHT','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
    ('Crunch cruzado','Flexión alterna hacia la rodilla contraria.','CALISTHENICS','CAL_CROSS_BODY_CRUNCH','ABDOMEN','BODYWEIGHT','BEGINNER','REPETITIONS',TRUE,FALSE),

    -- CALISTHENICS / PIERNAS (10)
    ('Sentadilla dividida','Sentadilla con una pierna adelantada.','CALISTHENICS','CAL_SPLIT_SQUAT','PIERNAS','BODYWEIGHT','BEGINNER','REPETITIONS',TRUE,FALSE),
    ('Subida al banco','Subida unilateral a un banco.','CALISTHENICS','CAL_STEP_UP','PIERNAS','BOX','BEGINNER','REPETITIONS',TRUE,FALSE),
    ('Puente de glúteos unilateral','Extensión unilateral de cadera en el suelo.','CALISTHENICS','CAL_SINGLE_LEG_GLUTE_BRIDGE','PIERNAS','BODYWEIGHT','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
    ('Sentadilla isométrica en pared','Sostén de sentadilla contra una pared.','CALISTHENICS','CAL_WALL_SIT','PIERNAS','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
    ('Pistol squat asistida','Sentadilla a una pierna con apoyo.','CALISTHENICS','CAL_ASSISTED_PISTOL_SQUAT','PIERNAS','BODYWEIGHT','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
    ('Sentadilla sissy','Flexión profunda de rodilla con peso corporal.','CALISTHENICS','CAL_SISSY_SQUAT','PIERNAS','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Sentadilla con salto','Sentadilla seguida de un salto vertical.','CALISTHENICS','CAL_JUMP_SQUAT','PIERNAS','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Salto horizontal','Salto explosivo hacia delante.','CALISTHENICS','CAL_BROAD_JUMP','PIERNAS','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Elevación de talones unilateral','Flexión plantar a una pierna.','CALISTHENICS','CAL_SINGLE_LEG_CALF_RAISE','PIERNAS','BODYWEIGHT','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
    ('Puente de isquiotibiales','Extensión de cadera con talones apoyados.','CALISTHENICS','CAL_HAMSTRING_BRIDGE','PIERNAS','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),

    -- CALISTHENICS / HABILIDAD (7)
    ('Caminata haciendo el pino','Desplazamiento controlado invertido sobre las manos.','CALISTHENICS','CAL_HANDSTAND_WALK','HABILIDAD','BODYWEIGHT','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Handstand libre','Sostén de equilibrio invertido sin apoyo.','CALISTHENICS','CAL_FREESTANDING_HANDSTAND','HABILIDAD','BODYWEIGHT','ADVANCED','TIME',FALSE,FALSE),
    ('Crow pose','Equilibrio de brazos con las rodillas sobre los brazos.','CALISTHENICS','CAL_CROW_POSE','HABILIDAD','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
    ('Headstand','Equilibrio invertido sobre la cabeza y las manos.','CALISTHENICS','CAL_HEADSTAND','HABILIDAD','BODYWEIGHT','INTERMEDIATE','TIME',FALSE,FALSE),
    ('Press a handstand','Transición desde pica hasta handstand.','CALISTHENICS','CAL_PRESS_HANDSTAND','HABILIDAD','BODYWEIGHT','ADVANCED','REPETITIONS',FALSE,FALSE),
    ('Elbow lever','Equilibrio horizontal apoyado en los codos.','CALISTHENICS','CAL_ELBOW_LEVER','HABILIDAD','BODYWEIGHT','INTERMEDIATE','TIME',FALSE,FALSE),
    ('Sostén en anillas','Sostén de apoyo con brazos extendidos en anillas.','CALISTHENICS','CAL_RING_SUPPORT_HOLD','HABILIDAD','RINGS','INTERMEDIATE','TIME',FALSE,FALSE),

    -- CALISTHENICS / ACONDICIONAMIENTO (5)
    ('Rodillas altas','Carrera en el sitio elevando las rodillas.','CALISTHENICS','CAL_HIGH_KNEES','ACONDICIONAMIENTO','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Talones al glúteo','Carrera en el sitio llevando talones atrás.','CALISTHENICS','CAL_BUTT_KICKS','ACONDICIONAMIENTO','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
    ('Saltos laterales','Saltos alternos de un lado al otro.','CALISTHENICS','CAL_SKATER_JUMPS','ACONDICIONAMIENTO','BODYWEIGHT','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
    ('Saltos con rodillas al pecho','Salto explosivo llevando las rodillas al pecho.','CALISTHENICS','CAL_TUCK_JUMPS','ACONDICIONAMIENTO','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
    ('Burpee con salto horizontal','Burpee terminado con salto amplio.','CALISTHENICS','CAL_BURPEE_BROAD_JUMP','ACONDICIONAMIENTO','BODYWEIGHT','ADVANCED','REPETITIONS',FALSE,FALSE)
) AS source(name, description, module, code, category, equipment, difficulty, registration_type, unilateral, external_load)
JOIN training_category category
  ON category.module = source.module
 AND category.normalized_name = lower(source.category)
 AND category.system_category
WHERE NOT EXISTS (
    SELECT 1
    FROM training_exercise exercise
    WHERE exercise.system_exercise
      AND (exercise.code = source.code
           OR (exercise.module = source.module AND lower(exercise.name) = lower(source.name)))
);

-- Give every newly inserted catalog row a primary and a secondary muscle. The
-- block-to-muscle mapping keeps this list compact while remaining deterministic.
INSERT INTO training_exercise_primary_muscle (exercise_id, muscle)
SELECT exercise.id,
       CASE category.normalized_name
           WHEN 'pecho' THEN 'Pectorales'
           WHEN 'espalda' THEN 'Dorsales'
           WHEN 'hombros' THEN 'Hombros'
           WHEN 'biceps' THEN 'Biceps'
           WHEN 'triceps' THEN 'Triceps'
           WHEN 'cuadriceps' THEN 'Cuadriceps'
           WHEN 'isquiotibiales' THEN 'Isquiotibiales'
           WHEN 'gluteos' THEN 'Gluteos'
           WHEN 'pantorrillas' THEN 'Pantorrillas'
           WHEN 'antebrazos' THEN 'Antebrazos'
           WHEN 'cuerpo_completo' THEN 'Cuerpo completo'
           WHEN 'empuje' THEN 'Pectorales'
           WHEN 'traccion' THEN 'Dorsales'
           WHEN 'progresiones_de_planche' THEN 'Hombros'
           WHEN 'palanca_frontal_posterior' THEN 'Dorsales'
           WHEN 'abdomen' THEN 'Abdomen'
           WHEN 'piernas' THEN 'Cuadriceps'
           WHEN 'habilidades' THEN 'Hombros'
           ELSE 'Cuerpo completo'
       END
FROM training_exercise exercise
JOIN training_category category ON category.id = exercise.category_id
WHERE exercise.system_exercise
  AND NOT EXISTS (
      SELECT 1 FROM training_exercise_primary_muscle muscle
      WHERE muscle.exercise_id = exercise.id
  );

INSERT INTO training_exercise_secondary_muscle (exercise_id, muscle)
SELECT exercise.id,
       CASE category.normalized_name
           WHEN 'pecho' THEN 'Triceps'
           WHEN 'espalda' THEN 'Biceps'
           WHEN 'hombros' THEN 'Trapecios'
           WHEN 'biceps' THEN 'Antebrazos'
           WHEN 'triceps' THEN 'Hombros'
           WHEN 'cuadriceps' THEN 'Gluteos'
           WHEN 'isquiotibiales' THEN 'Gluteos'
           WHEN 'gluteos' THEN 'Isquiotibiales'
           WHEN 'pantorrillas' THEN 'Soleo'
           WHEN 'antebrazos' THEN 'Biceps'
           WHEN 'cuerpo_completo' THEN 'Cuadriceps'
           WHEN 'empuje' THEN 'Triceps'
           WHEN 'traccion' THEN 'Biceps'
           WHEN 'progresiones_de_planche' THEN 'Pectorales'
           WHEN 'palanca_frontal_posterior' THEN 'Abdomen'
           WHEN 'abdomen' THEN 'Flexores de cadera'
           WHEN 'piernas' THEN 'Gluteos'
           WHEN 'habilidades' THEN 'Abdomen'
           ELSE 'Pantorrillas'
       END
FROM training_exercise exercise
JOIN training_category category ON category.id = exercise.category_id
WHERE exercise.system_exercise
  AND NOT EXISTS (
      SELECT 1 FROM training_exercise_secondary_muscle muscle
      WHERE muscle.exercise_id = exercise.id
  );

DO $$
DECLARE
    gym_count INTEGER;
    calisthenics_count INTEGER;
    missing_muscles INTEGER;
BEGIN
    SELECT count(*) FILTER (WHERE module = 'GYM'),
           count(*) FILTER (WHERE module = 'CALISTHENICS')
    INTO gym_count, calisthenics_count
    FROM training_exercise
    WHERE system_exercise;

    IF gym_count <> 180 OR calisthenics_count <> 115 THEN
        RAISE EXCEPTION 'Training catalog count mismatch: GYM %, CALISTHENICS %', gym_count, calisthenics_count;
    END IF;

    SELECT count(*)
    INTO missing_muscles
    FROM training_exercise exercise
    WHERE exercise.system_exercise
      AND (NOT EXISTS (SELECT 1 FROM training_exercise_primary_muscle muscle WHERE muscle.exercise_id = exercise.id)
           OR NOT EXISTS (SELECT 1 FROM training_exercise_secondary_muscle muscle WHERE muscle.exercise_id = exercise.id));

    IF missing_muscles <> 0 THEN
        RAISE EXCEPTION 'Training catalog rows without primary or secondary muscles: %', missing_muscles;
    END IF;
END $$;
