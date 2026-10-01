# Implementación de auditoría UX/UI

| Commit | Hallazgos | Cambio | Verificación |
|---|---|---|---|
| 03 | Base | Regresiones HTTP de entrenamiento y migraciones con PostgreSQL 17/Flyway/validate obligatorios; CI fix/**. | Compilación de pruebas; ejecución PostgreSQL requerida antes del release. |
| 04 | SG042 | Actualización de series por número preservando IDs y restricciones; finalización repetida idempotente. | Regresión HTTP/PostgreSQL: 3 guardados, identidad persistente y doble finalización; compilación. Ejecución PG pendiente antes del release. |
| 06 | SG043–044 | Disponibilidad PATCH independiente, IDs estables y copia inactiva; índices preservados en edición/reordenamiento. | Prueba HTTP/PG de identidad, disponibilidad y copia; compilación requerida. |
| 09 | SG039 | Alternativas, programación/cancelación con vista previa y versiones; vigencias derivadas sin cambiar fechas declaradas. V44 conserva planes y fechas. | 4 pruebas de vigencia aprobadas; regresiones HTTP PostgreSQL compiladas, ejecución PG pendiente antes del release. |
| 11 | SG040 | Medidas conservan metas manuales; perfil y dashboard resuelven la misma meta efectiva y su origen. | Regresión HTTP existente de perfil/dashboard aprobada; nueva regresión PostgreSQL de medidas compilada, ejecución pendiente. |
| 13 | SG005, SG011–014 | Valores ausentes se conservan; snapshots retienen subtotales conocidos y completitud (V45). Historial utiliza energía guardada. Ranking por palabra completa y advertencia para composición sospechosa, sin sustituir datos. | 2 pruebas matemáticas y 2 regresiones HTTP aprobadas (ausentes/histórico y ranking); migración PostgreSQL pendiente antes del release. |
| 14 | SG006 | Conteo real de ingredientes por consulta agrupada de página; resúmenes sin listas ni cálculo nutricional por receta. | Regresión HTTP crear→listar→detalle aprobada: conteo 2 y lista vacía en resumen, 2 ingredientes en detalle. |
| 15 | SG038 | Reutilización de archivados requiere acknowledgedArchivedFoodIds en registros, recetas/copia y plantillas; validación previa y transaccional, historial/archivo conservados. Error compatible informa IDs/nombres y respuestas señalan archived. | 3 regresiones HTTP aprobadas: rechazo sin escritura, aceptación explícita en receta/recientes/plantilla/copia y archivo conservado. |
