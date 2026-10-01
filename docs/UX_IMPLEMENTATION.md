# Implementación de auditoría UX/UI

| Commit | Hallazgos | Cambio | Verificación |
|---|---|---|---|
| 03 | Base | Regresiones HTTP de entrenamiento y migraciones con PostgreSQL 17/Flyway/validate obligatorios; CI fix/**. | Compilación de pruebas; ejecución PostgreSQL requerida antes del release. |
| 04 | SG042 | Actualización de series por número preservando IDs y restricciones; finalización repetida idempotente. | Regresión HTTP/PostgreSQL: 3 guardados, identidad persistente y doble finalización; compilación. Ejecución PG pendiente antes del release. |
| 06 | SG043–044 | Disponibilidad PATCH independiente, IDs estables y copia inactiva; índices preservados en edición/reordenamiento. | Prueba HTTP/PG de identidad, disponibilidad y copia; compilación requerida. |
| 09 | SG039 | Alternativas, programación/cancelación con vista previa y versiones; vigencias derivadas sin cambiar fechas declaradas. V44 conserva planes y fechas. | 4 pruebas de vigencia aprobadas; regresiones HTTP PostgreSQL compiladas, ejecución PG pendiente antes del release. |
