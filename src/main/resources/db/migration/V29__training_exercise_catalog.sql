-- The catalog is keyed by code so this migration is safe to replay on restored databases.
UPDATE training_exercise
SET code = 'GYM_BARBELL_ROW'
WHERE code = 'GYM_BARbell_ROW' AND system_exercise;

UPDATE training_exercise SET code = 'GYM_PRESS_BENCH', equipment = 'BARBELL',
    difficulty = 'BEGINNER', registration_type = 'WEIGHT_AND_REPETITIONS', external_load = TRUE
WHERE module = 'GYM' AND lower(name) = lower('Press banca') AND system_exercise;
UPDATE training_exercise SET code = 'GYM_BACK_SQUAT', equipment = 'BARBELL',
    difficulty = 'INTERMEDIATE', registration_type = 'WEIGHT_AND_REPETITIONS', external_load = TRUE
WHERE module = 'GYM' AND lower(name) = lower('Sentadilla') AND system_exercise;
UPDATE training_exercise SET code = 'CAL_PULL_UP', equipment = 'PULL_UP_BAR',
    difficulty = 'INTERMEDIATE', registration_type = 'REPETITIONS', external_load = FALSE
WHERE module = 'CALISTHENICS' AND lower(name) = lower('Dominadas') AND system_exercise;
UPDATE training_exercise SET code = 'CAL_DIP', equipment = 'PARALLEL_BARS',
    difficulty = 'INTERMEDIATE', registration_type = 'REPETITIONS', external_load = FALSE
WHERE module = 'CALISTHENICS' AND lower(name) = lower('Fondos') AND system_exercise;

INSERT INTO training_exercise
    (owner_id, name, description, module, active, code, normalized_name, category_id,
     equipment, difficulty, registration_type, unilateral, external_load, system_exercise)
SELECT NULL, source.name, source.description, source.module, TRUE, source.code, lower(source.name), category.id,
       source.equipment, source.difficulty, source.registration_type, source.unilateral, source.external_load, TRUE
FROM (VALUES
 ('Press banca','Empuje horizontal con barra.','GYM','GYM_PRESS_BENCH','PECHO','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Press inclinado con mancuernas','Empuje inclinado para pectoral superior.','GYM','GYM_PRESS_INCLINE_DB','PECHO','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Aperturas con mancuernas','Aislamiento de pectoral.','GYM','GYM_CHEST_FLY_DB','PECHO','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Remo con barra','Tracción horizontal con barra.','GYM','GYM_BARBELL_ROW','ESPALDA','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Jalón al pecho','Tracción vertical en polea.','GYM','GYM_LAT_PULLDOWN','ESPALDA','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Remo sentado en polea','Tracción horizontal en polea.','GYM','GYM_CABLE_ROW','ESPALDA','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Peso muerto','Levantamiento global con barra.','GYM','GYM_DEADLIFT','ESPALDA','BARBELL','ADVANCED','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Press militar','Empuje vertical con barra.','GYM','GYM_OVERHEAD_PRESS','HOMBROS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Elevaciones laterales','Aislamiento de deltoides.','GYM','GYM_LATERAL_RAISE','HOMBROS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Face pull','Rotación externa y deltoides posterior.','GYM','GYM_FACE_PULL','HOMBROS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Curl de bíceps con barra','Flexión de codo con barra.','GYM','GYM_BARBELL_CURL','BICEPS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Curl martillo','Flexión neutra de codo.','GYM','GYM_HAMMER_CURL','BICEPS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Press francés','Extensión de tríceps.','GYM','GYM_FRENCH_PRESS','TRICEPS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Extensión de tríceps en polea','Extensión de codo en polea.','GYM','GYM_TRICEPS_PUSHDOWN','TRICEPS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Sentadilla con barra','Sentadilla bilateral.','GYM','GYM_BAR_SQUAT','CUADRICEPS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Prensa de piernas','Empuje de piernas en máquina.','GYM','GYM_LEG_PRESS','CUADRICEPS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Extensión de cuádriceps','Extensión de rodilla.','GYM','GYM_LEG_EXTENSION','CUADRICEPS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Peso muerto rumano','Bisagra de cadera.','GYM','GYM_RDL','ISQUIOTIBIALES','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Curl femoral','Flexión de rodilla en máquina.','GYM','GYM_LEG_CURL','ISQUIOTIBIALES','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Hip thrust','Extensión de cadera.','GYM','GYM_HIP_THRUST','GLUTEOS','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Abducción de cadera','Abducción en máquina.','GYM','GYM_HIP_ABDUCTION','GLUTEOS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Elevación de talones','Flexión plantar.','GYM','GYM_CALF_RAISE','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Crunch en polea','Flexión de tronco.','GYM','GYM_CABLE_CRUNCH','ABDOMEN','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
 ('Rueda abdominal','Anti-extensión del tronco.','GYM','GYM_AB_WHEEL','ABDOMEN','NONE','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
 ('Caminata granjero','Desplazamiento cargado.','GYM','GYM_FARMERS_WALK','ACONDICIONAMIENTO','DUMBBELL','BEGINNER','DISTANCE',FALSE,TRUE),
 ('Sostén granjero','Sostén isométrico cargado.','GYM','GYM_FARMERS_HOLD','ANTEBRAZOS','DUMBBELL','BEGINNER','TIME',FALSE,TRUE),
 ('Burpees','Acondicionamiento de cuerpo completo.','GYM','GYM_BURPEE','ACONDICIONAMIENTO','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
 ('Carrera en cinta','Carrera continua.','GYM','GYM_TREADMILL_RUN','ACONDICIONAMIENTO','TREADMILL','BEGINNER','DISTANCE',FALSE,FALSE),
 ('Bicicleta fija','Cardio en bicicleta.','GYM','GYM_STATIONARY_BIKE','ACONDICIONAMIENTO','STATIONARY_BIKE','BEGINNER','TIME',FALSE,FALSE),
 ('Flexiones','Empuje horizontal con peso corporal.','CALISTHENICS','CAL_PUSH_UP','EMPUJE','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
 ('Fondos','Empuje en paralelas.','CALISTHENICS','CAL_DIP','EMPUJE','PARALLEL_BARS','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
 ('Flexiones pica','Empuje vertical progresivo.','CALISTHENICS','CAL_PIKE_PUSH_UP','EMPUJE','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
 ('Dominadas','Tracción vertical con peso corporal.','CALISTHENICS','CAL_PULL_UP','TRACCION','PULL_UP_BAR','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
 ('Dominadas supinas','Tracción vertical supina.','CALISTHENICS','CAL_CHIN_UP','TRACCION','PULL_UP_BAR','BEGINNER','REPETITIONS',FALSE,FALSE),
 ('Remo australiano','Tracción horizontal.','CALISTHENICS','CAL_AUSTRALIAN_ROW','TRACCION','PULL_UP_BAR','BEGINNER','REPETITIONS',FALSE,FALSE),
 ('Muscle up','Transición de tracción a empuje.','CALISTHENICS','CAL_MUSCLE_UP','HABILIDAD','PULL_UP_BAR','ADVANCED','REPETITIONS',FALSE,FALSE),
 ('Planche','Isométrico de palanca frontal.','CALISTHENICS','CAL_PLANCHE','HABILIDAD','PARALLEL_BARS','ADVANCED','TIME',FALSE,FALSE),
 ('Front lever','Isométrico de tracción horizontal.','CALISTHENICS','CAL_FRONT_LEVER','HABILIDAD','PULL_UP_BAR','ADVANCED','TIME',FALSE,FALSE),
 ('Back lever','Isométrico de palanca posterior.','CALISTHENICS','CAL_BACK_LEVER','HABILIDAD','RINGS','ADVANCED','TIME',FALSE,FALSE),
 ('Handstand','Equilibrio invertido.','CALISTHENICS','CAL_HANDSTAND','HABILIDAD','BODYWEIGHT','INTERMEDIATE','TIME',FALSE,FALSE),
 ('L-sit','Sostén isométrico en L.','CALISTHENICS','CAL_L_SIT','HABILIDAD','PARALLEL_BARS','INTERMEDIATE','TIME',FALSE,FALSE),
 ('Suspensión en barra','Sostén colgado de la barra.','CALISTHENICS','CAL_DEAD_HANG','TRACCION','PULL_UP_BAR','BEGINNER','TIME',FALSE,FALSE),
 ('Plancha abdominal','Anti-extensión del tronco.','CALISTHENICS','CAL_PLANK','ABDOMEN','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
 ('Elevación de piernas colgado','Flexión de cadera colgado.','CALISTHENICS','CAL_HANGING_LEG_RAISE','ABDOMEN','PULL_UP_BAR','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
 ('Hollow body hold','Isométrico abdominal.','CALISTHENICS','CAL_HOLLOW_HOLD','ABDOMEN','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
 ('Pistol squat','Sentadilla unilateral.','CALISTHENICS','CAL_PISTOL_SQUAT','PIERNAS','BODYWEIGHT','ADVANCED','REPETITIONS',TRUE,FALSE),
 ('Zancadas','Patrón unilateral de piernas.','CALISTHENICS','CAL_LUNGE','PIERNAS','BODYWEIGHT','BEGINNER','REPETITIONS',TRUE,FALSE),
 ('Sentadilla','Sentadilla con peso corporal.','CALISTHENICS','CAL_SQUAT','PIERNAS','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
 ('Nordic curl','Flexión nórdica de rodilla.','CALISTHENICS','CAL_NORDIC_CURL','PIERNAS','BODYWEIGHT','ADVANCED','REPETITIONS',FALSE,FALSE),
 ('Puente de glúteos','Extensión de cadera.','CALISTHENICS','CAL_GLUTE_BRIDGE','PIERNAS','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
 ('Jumping jacks','Acondicionamiento calisténico.','CALISTHENICS','CAL_JUMPING_JACKS','ACONDICIONAMIENTO','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
 ('Mountain climbers','Acondicionamiento dinámico.','CALISTHENICS','CAL_MOUNTAIN_CLIMBERS','ACONDICIONAMIENTO','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
  ('Salto de cuerda','Cardio con cuerda.','CALISTHENICS','CAL_JUMP_ROPE','ACONDICIONAMIENTO','JUMP_ROPE','BEGINNER','TIME',FALSE,FALSE),
  ('Press inclinado con barra','Empuje inclinado para pectoral superior.','GYM','GYM_INCLINE_BENCH_PRESS','PECHO','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Cruce de poleas','Aislamiento de pectoral en polea.','GYM','GYM_CABLE_CROSSOVER','PECHO','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Remo con mancuerna','Tracción horizontal unilateral.','GYM','GYM_DUMBBELL_ROW','ESPALDA','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
  ('Pullover en polea','Extensión de hombro para dorsales.','GYM','GYM_CABLE_PULLOVER','ESPALDA','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Pájaros con mancuernas','Trabajo del deltoides posterior.','GYM','GYM_REAR_DELT_FLY','HOMBROS','DUMBBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Curl inclinado con mancuernas','Flexión de codo en banco inclinado.','GYM','GYM_INCLINE_DB_CURL','BICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Extensión de tríceps sobre cabeza','Extensión de codo por encima de la cabeza.','GYM','GYM_OVERHEAD_TRICEPS_EXTENSION','TRICEPS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Sentadilla búlgara','Sentadilla unilateral con apoyo posterior.','GYM','GYM_BULGARIAN_SPLIT_SQUAT','CUADRICEPS','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
  ('Peso muerto piernas rígidas','Bisagra de cadera con rodillas extendidas.','GYM','GYM_STIFF_LEG_DEADLIFT','ISQUIOTIBIALES','BARBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Patada de glúteo en polea','Extensión de cadera unilateral.','GYM','GYM_CABLE_KICKBACK','GLUTEOS','CABLE','BEGINNER','WEIGHT_AND_REPETITIONS',TRUE,TRUE),
  ('Elevación de talones sentado','Flexión plantar sentado.','GYM','GYM_SEATED_CALF_RAISE','PANTORRILLAS','MACHINE','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Giro ruso con balón','Rotación controlada del tronco.','GYM','GYM_RUSSIAN_TWIST','ABDOMEN','MEDICINE_BALL','BEGINNER','REPETITIONS',FALSE,TRUE),
  ('Press Pallof','Anti-rotación con polea.','GYM','GYM_PALLOF_PRESS','ABDOMEN','CABLE','BEGINNER','REPETITIONS',FALSE,TRUE),
  ('Balanceo con kettlebell','Potencia de cadera con kettlebell.','GYM','GYM_KETTLEBELL_SWING','CUERPO_COMPLETO','KETTLEBELL','INTERMEDIATE','REPETITIONS',FALSE,TRUE),
  ('Thruster con mancuernas','Sentadilla y empuje combinados.','GYM','GYM_DUMBBELL_THRUSTER','CUERPO_COMPLETO','DUMBBELL','INTERMEDIATE','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Remo en máquina','Cardio y tracción en máquina de remo.','GYM','GYM_ROWING_MACHINE','ACONDICIONAMIENTO','ROWING_MACHINE','BEGINNER','DISTANCE',FALSE,FALSE),
  ('Curl de muñeca','Flexión de muñeca para antebrazos.','GYM','GYM_WRIST_CURL','ANTEBRAZOS','BARBELL','BEGINNER','WEIGHT_AND_REPETITIONS',FALSE,TRUE),
  ('Flexiones diamante','Empuje cerrado con peso corporal.','CALISTHENICS','CAL_DIAMOND_PUSH_UP','EMPUJE','BODYWEIGHT','INTERMEDIATE','REPETITIONS',FALSE,FALSE),
  ('Flexiones pseudo planche','Empuje inclinado hacia delante.','CALISTHENICS','CAL_PSEUDO_PLANCHE_PUSH_UP','EMPUJE','BODYWEIGHT','ADVANCED','REPETITIONS',FALSE,FALSE),
  ('Flexiones haciendo el pino','Empuje vertical invertido.','CALISTHENICS','CAL_HANDSTAND_PUSH_UP','HABILIDAD','BODYWEIGHT','ADVANCED','REPETITIONS',FALSE,FALSE),
  ('Dominadas arqueras','Tracción unilateral asistida por el otro brazo.','CALISTHENICS','CAL_ARCHER_PULL_UP','TRACCION','PULL_UP_BAR','ADVANCED','REPETITIONS',TRUE,FALSE),
  ('Skin the cat','Rotación controlada en anillas.','CALISTHENICS','CAL_SKIN_THE_CAT','HABILIDAD','RINGS','ADVANCED','TIME',FALSE,FALSE),
  ('Dragon flag','Anti-extensión avanzada del tronco.','CALISTHENICS','CAL_DRAGON_FLAG','ABDOMEN','PARALLEL_BARS','ADVANCED','REPETITIONS',FALSE,FALSE),
  ('Plancha lateral','Anti-flexión lateral del tronco.','CALISTHENICS','CAL_SIDE_PLANK','ABDOMEN','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
  ('Plancha invertida','Sostén de cadena posterior.','CALISTHENICS','CAL_REVERSE_PLANK','ABDOMEN','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
  ('Elevación de talones de pie','Flexión plantar con peso corporal.','CALISTHENICS','CAL_CALF_RAISE','PIERNAS','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
  ('Sentadilla shrimp','Sentadilla unilateral avanzada.','CALISTHENICS','CAL_SHRIMP_SQUAT','PIERNAS','BODYWEIGHT','ADVANCED','REPETITIONS',TRUE,FALSE),
  ('Sentadilla cosaca','Movilidad lateral de cadera y piernas.','CALISTHENICS','CAL_COSSACK_SQUAT','MOVILIDAD','BODYWEIGHT','INTERMEDIATE','REPETITIONS',TRUE,FALSE),
  ('Dislocaciones de hombros','Movilidad de hombros con banda.','CALISTHENICS','CAL_SHOULDER_DISLOCATES','MOVILIDAD','RESISTANCE_BAND','BEGINNER','REPETITIONS',FALSE,FALSE),
  ('Sostén de sentadilla profunda','Movilidad de tobillo y cadera.','CALISTHENICS','CAL_DEEP_SQUAT_HOLD','MOVILIDAD','BODYWEIGHT','BEGINNER','TIME',FALSE,FALSE),
  ('Gato-vaca','Movilidad de columna.','CALISTHENICS','CAL_CAT_COW','MOVILIDAD','BODYWEIGHT','BEGINNER','REPETITIONS',FALSE,FALSE),
  ('Gateo de oso','Desplazamiento cuadrúpedo de acondicionamiento.','CALISTHENICS','CAL_BEAR_CRAWL','ACONDICIONAMIENTO','BODYWEIGHT','INTERMEDIATE','DISTANCE',FALSE,FALSE),
  ('Sprint','Carrera corta de alta intensidad.','CALISTHENICS','CAL_SPRINT','ACONDICIONAMIENTO','BODYWEIGHT','INTERMEDIATE','DISTANCE',FALSE,FALSE),
  ('Remo en anillas','Tracción horizontal en anillas.','CALISTHENICS','CAL_RING_ROW','TRACCION','RINGS','BEGINNER','REPETITIONS',FALSE,FALSE),
  ('Tuck planche','Progresión isométrica de planche.','CALISTHENICS','CAL_TUCK_PLANCHE','HABILIDAD','PARALLEL_BARS','ADVANCED','TIME',FALSE,FALSE)
) AS source(name, description, module, code, category, equipment, difficulty, registration_type, unilateral, external_load)
JOIN training_category category ON category.module = source.module
    AND category.normalized_name = lower(source.category) AND category.system_category
WHERE NOT EXISTS (SELECT 1 FROM training_exercise exercise
                   WHERE exercise.system_exercise
                     AND (exercise.code = source.code
                          OR (exercise.module = source.module AND lower(exercise.name) = lower(source.name))));

INSERT INTO training_exercise_primary_muscle (exercise_id, muscle)
SELECT exercise.id, source.muscle
FROM (VALUES
 ('GYM_PRESS_BENCH','Pectorales'), ('GYM_PRESS_INCLINE_DB','Pectorales'), ('GYM_CHEST_FLY_DB','Pectorales'),
  ('GYM_BARBELL_ROW','Espalda'), ('GYM_LAT_PULLDOWN','Dorsales'), ('GYM_CABLE_ROW','Espalda'), ('GYM_DEADLIFT','Espalda'),
 ('GYM_OVERHEAD_PRESS','Hombros'), ('GYM_LATERAL_RAISE','Hombros'), ('GYM_FACE_PULL','Hombros'),
 ('GYM_BARBELL_CURL','Biceps'), ('GYM_HAMMER_CURL','Biceps'), ('GYM_FRENCH_PRESS','Triceps'), ('GYM_TRICEPS_PUSHDOWN','Triceps'),
  ('GYM_BACK_SQUAT','Cuadriceps'), ('GYM_BAR_SQUAT','Cuadriceps'), ('GYM_LEG_PRESS','Cuadriceps'), ('GYM_LEG_EXTENSION','Cuadriceps'),
 ('GYM_RDL','Isquiotibiales'), ('GYM_LEG_CURL','Isquiotibiales'), ('GYM_HIP_THRUST','Gluteos'), ('GYM_HIP_ABDUCTION','Gluteos'),
 ('GYM_CALF_RAISE','Pantorrillas'), ('GYM_CABLE_CRUNCH','Abdomen'), ('GYM_AB_WHEEL','Abdomen'), ('GYM_FARMERS_WALK','Antebrazos'),
 ('GYM_FARMERS_HOLD','Antebrazos'), ('GYM_BURPEE','Cuerpo completo'), ('GYM_TREADMILL_RUN','Cuerpo completo'), ('GYM_STATIONARY_BIKE','Piernas'),
 ('CAL_PUSH_UP','Pectorales'), ('CAL_DIP','Triceps'), ('CAL_PIKE_PUSH_UP','Hombros'), ('CAL_PULL_UP','Dorsales'), ('CAL_CHIN_UP','Dorsales'),
 ('CAL_AUSTRALIAN_ROW','Espalda'), ('CAL_MUSCLE_UP','Dorsales'), ('CAL_PLANCHE','Hombros'), ('CAL_FRONT_LEVER','Dorsales'), ('CAL_BACK_LEVER','Espalda'),
 ('CAL_HANDSTAND','Hombros'), ('CAL_L_SIT','Abdomen'), ('CAL_DEAD_HANG','Antebrazos'), ('CAL_PLANK','Abdomen'), ('CAL_HANGING_LEG_RAISE','Abdomen'),
 ('CAL_HOLLOW_HOLD','Abdomen'), ('CAL_PISTOL_SQUAT','Cuadriceps'), ('CAL_LUNGE','Gluteos'), ('CAL_SQUAT','Cuadriceps'), ('CAL_NORDIC_CURL','Isquiotibiales'),
  ('CAL_GLUTE_BRIDGE','Gluteos'), ('CAL_JUMPING_JACKS','Cuerpo completo'), ('CAL_MOUNTAIN_CLIMBERS','Abdomen'), ('CAL_JUMP_ROPE','Pantorrillas'),
  ('GYM_INCLINE_BENCH_PRESS','Pectorales'), ('GYM_CABLE_CROSSOVER','Pectorales'), ('GYM_DUMBBELL_ROW','Espalda'),
  ('GYM_CABLE_PULLOVER','Dorsales'), ('GYM_REAR_DELT_FLY','Hombros'), ('GYM_INCLINE_DB_CURL','Biceps'),
  ('GYM_OVERHEAD_TRICEPS_EXTENSION','Triceps'), ('GYM_BULGARIAN_SPLIT_SQUAT','Cuadriceps'),
  ('GYM_STIFF_LEG_DEADLIFT','Isquiotibiales'), ('GYM_CABLE_KICKBACK','Gluteos'), ('GYM_SEATED_CALF_RAISE','Pantorrillas'),
  ('GYM_RUSSIAN_TWIST','Abdomen'), ('GYM_PALLOF_PRESS','Abdomen'), ('GYM_KETTLEBELL_SWING','Gluteos'),
  ('GYM_DUMBBELL_THRUSTER','Cuadriceps'), ('GYM_ROWING_MACHINE','Cuerpo completo'), ('GYM_WRIST_CURL','Antebrazos'),
  ('CAL_DIAMOND_PUSH_UP','Pectorales'), ('CAL_PSEUDO_PLANCHE_PUSH_UP','Hombros'), ('CAL_HANDSTAND_PUSH_UP','Hombros'),
  ('CAL_ARCHER_PULL_UP','Dorsales'), ('CAL_SKIN_THE_CAT','Hombros'), ('CAL_DRAGON_FLAG','Abdomen'),
  ('CAL_SIDE_PLANK','Abdomen'), ('CAL_REVERSE_PLANK','Abdomen'), ('CAL_CALF_RAISE','Pantorrillas'),
  ('CAL_SHRIMP_SQUAT','Cuadriceps'), ('CAL_COSSACK_SQUAT','Gluteos'), ('CAL_SHOULDER_DISLOCATES','Hombros'),
  ('CAL_DEEP_SQUAT_HOLD','Gluteos'), ('CAL_CAT_COW','Espalda'), ('CAL_BEAR_CRAWL','Cuerpo completo'),
  ('CAL_SPRINT','Pantorrillas'), ('CAL_RING_ROW','Espalda'), ('CAL_TUCK_PLANCHE','Hombros')
) AS source(code, muscle)
JOIN training_exercise exercise ON exercise.code = source.code
WHERE NOT EXISTS (SELECT 1 FROM training_exercise_primary_muscle muscle WHERE muscle.exercise_id = exercise.id AND muscle.muscle = source.muscle);

INSERT INTO training_exercise_secondary_muscle (exercise_id, muscle)
SELECT exercise.id, source.muscle
FROM (VALUES
  ('GYM_PRESS_BENCH','Triceps'), ('GYM_PRESS_INCLINE_DB','Hombros'), ('GYM_CHEST_FLY_DB','Hombros'), ('GYM_BARBELL_ROW','Biceps'),
 ('GYM_LAT_PULLDOWN','Biceps'), ('GYM_CABLE_ROW','Biceps'), ('GYM_DEADLIFT','Gluteos'), ('GYM_OVERHEAD_PRESS','Triceps'),
 ('GYM_LATERAL_RAISE','Trapecios'), ('GYM_FACE_PULL','Trapecios'), ('GYM_BARBELL_CURL','Antebrazos'), ('GYM_HAMMER_CURL','Antebrazos'),
  ('GYM_FRENCH_PRESS','Hombros'), ('GYM_TRICEPS_PUSHDOWN','Hombros'), ('GYM_BACK_SQUAT','Gluteos'), ('GYM_BAR_SQUAT','Gluteos'), ('GYM_LEG_PRESS','Gluteos'),
 ('GYM_LEG_EXTENSION','Cuadriceps'), ('GYM_RDL','Gluteos'), ('GYM_LEG_CURL','Pantorrillas'), ('GYM_HIP_THRUST','Isquiotibiales'),
 ('GYM_HIP_ABDUCTION','Gluteos'), ('GYM_CALF_RAISE','Soleo'), ('GYM_CABLE_CRUNCH','Recto abdominal'), ('GYM_AB_WHEEL','Hombros'),
 ('GYM_FARMERS_WALK','Trapecios'), ('GYM_FARMERS_HOLD','Hombros'), ('GYM_BURPEE','Pectorales'), ('GYM_TREADMILL_RUN','Pantorrillas'),
 ('GYM_STATIONARY_BIKE','Cuadriceps'), ('CAL_PUSH_UP','Triceps'), ('CAL_DIP','Pectorales'), ('CAL_PIKE_PUSH_UP','Triceps'),
 ('CAL_PULL_UP','Biceps'), ('CAL_CHIN_UP','Biceps'), ('CAL_AUSTRALIAN_ROW','Biceps'), ('CAL_MUSCLE_UP','Triceps'),
 ('CAL_PLANCHE','Pectorales'), ('CAL_FRONT_LEVER','Abdomen'), ('CAL_BACK_LEVER','Abdomen'), ('CAL_HANDSTAND','Abdomen'),
 ('CAL_L_SIT','Cuadriceps'), ('CAL_DEAD_HANG','Dorsales'), ('CAL_PLANK','Gluteos'), ('CAL_HANGING_LEG_RAISE','Flexores de cadera'),
 ('CAL_HOLLOW_HOLD','Flexores de cadera'), ('CAL_PISTOL_SQUAT','Gluteos'), ('CAL_LUNGE','Cuadriceps'), ('CAL_SQUAT','Gluteos'),
 ('CAL_NORDIC_CURL','Gluteos'), ('CAL_GLUTE_BRIDGE','Isquiotibiales'), ('CAL_JUMPING_JACKS','Pantorrillas'), ('CAL_MOUNTAIN_CLIMBERS','Hombros'),
  ('CAL_JUMP_ROPE','Cuadriceps'), ('GYM_INCLINE_BENCH_PRESS','Triceps'), ('GYM_CABLE_CROSSOVER','Hombros'),
  ('GYM_DUMBBELL_ROW','Biceps'), ('GYM_CABLE_PULLOVER','Triceps'), ('GYM_REAR_DELT_FLY','Trapecios'),
  ('GYM_INCLINE_DB_CURL','Antebrazos'), ('GYM_OVERHEAD_TRICEPS_EXTENSION','Hombros'),
  ('GYM_BULGARIAN_SPLIT_SQUAT','Gluteos'), ('GYM_STIFF_LEG_DEADLIFT','Gluteos'), ('GYM_CABLE_KICKBACK','Gluteos'),
  ('GYM_SEATED_CALF_RAISE','Soleo'), ('GYM_RUSSIAN_TWIST','Oblicuos'), ('GYM_PALLOF_PRESS','Oblicuos'),
  ('GYM_KETTLEBELL_SWING','Isquiotibiales'), ('GYM_DUMBBELL_THRUSTER','Hombros'), ('GYM_ROWING_MACHINE','Espalda'),
  ('GYM_WRIST_CURL','Antebrazos'), ('CAL_DIAMOND_PUSH_UP','Triceps'), ('CAL_PSEUDO_PLANCHE_PUSH_UP','Pectorales'),
  ('CAL_HANDSTAND_PUSH_UP','Triceps'), ('CAL_ARCHER_PULL_UP','Biceps'), ('CAL_SKIN_THE_CAT','Dorsales'),
  ('CAL_DRAGON_FLAG','Flexores de cadera'), ('CAL_SIDE_PLANK','Oblicuos'), ('CAL_REVERSE_PLANK','Gluteos'),
  ('CAL_CALF_RAISE','Soleo'), ('CAL_SHRIMP_SQUAT','Gluteos'), ('CAL_COSSACK_SQUAT','Cuadriceps'),
  ('CAL_SHOULDER_DISLOCATES','Trapecios'), ('CAL_DEEP_SQUAT_HOLD','Pantorrillas'), ('CAL_CAT_COW','Abdomen'),
  ('CAL_BEAR_CRAWL','Hombros'), ('CAL_SPRINT','Cuadriceps'), ('CAL_RING_ROW','Biceps'), ('CAL_TUCK_PLANCHE','Pectorales')
) AS source(code, muscle)
JOIN training_exercise exercise ON exercise.code = source.code
WHERE NOT EXISTS (SELECT 1 FROM training_exercise_secondary_muscle muscle WHERE muscle.exercise_id = exercise.id AND muscle.muscle = source.muscle);
