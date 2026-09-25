# Roadmap: Registro y Estimación IA

## Objetivo

Unificar la captura asistida por IA en un flujo con intención explícita:

- **Alimento**: reconocer un único producto o alimento, materializarlo en el catálogo y registrar la porción.
- **Comida**: reconocer los componentes de un plato, materializarlos como alimentos, crear una receta y registrar una porción de esa receta.

La imagen y la respuesta del proveedor son un borrador. La fuente de verdad sigue siendo el modelo de dominio `Food`, `Recipe` y `FoodLog` después de la revisión humana.

## Arquitectura objetivo

```text
Foto + intención
      │
      ▼
Gemini: extracción nutricional
      │
      ├── JEV (shadow): tipo, calidad de evidencia y necesidad de revisión
      │
      ▼
AiCapture DRAFT (vence en 24 h)
      │ revisión humana
      ▼
Confirmación idempotente
      ├── FOOD   → Food → FoodLog
      └── RECIPE → Foods → Recipe → FoodLog (1 porción)
```

## Estado de implementación

### Fase 1 — Contrato y persistencia: completada

- `AiCapture` persiste intención, estado, borrador, decisión JEV y registro confirmado.
- Estados: `DRAFT`, `CONFIRMED`, `DISCARDED`.
- La confirmación bloquea la captura, valida propietario/vencimiento y es idempotente.
- Migración Flyway `V40__ai_capture_workflow.sql`.

### Fase 2 — Materialización del dominio: completada

- `FOOD` exige exactamente un ítem; reutiliza una coincidencia confiable o crea un alimento por 100 g.
- `RECIPE` resuelve o crea los alimentos componentes, crea la receta con pesos y nutrientes y registra una porción.
- Se mantiene temporalmente el endpoint legado para no romper clientes anteriores.

### Fase 3 — Experiencia “Registrar”: primera versión completada

- Preselector explícito **Registrar alimento con foto** / **Registrar comida con foto**.
- Revisión editable antes de confirmar.
- El flujo existente de foto en una comida utiliza `RECIPE`.
- La corrección de una estimación conserva su intención original.

### Fase 4 — JEV: piloto shadow implementado

- JEV recibe el tipo solicitado y la extracción estructurada, nunca la API key en el cliente.
- Devuelve: tipo detectado, confianza, calidad de evidencia y probabilidad de requerir revisión.
- Sus fallos no bloquean al usuario y no modifican macros ni crean entidades.
- Activación por variables de entorno; comenzar en `shadow`.

## Próximos incrementos recomendados

### P1 — Captura guiada múltiple para alimentos

1. Aceptar varias imágenes por captura (`frente`, `tabla nutricional`, `ingredientes/código`).
2. Comprimir en cliente y fijar límites por archivo y por captura.
3. Permitir “Analizar ahora” desde una foto y recomendar otra cuando falte evidencia.
4. Mostrar qué dato provino de etiqueta, catálogo o inferencia.
5. Añadir pruebas de archivos repetidos, orden, tamaño y formatos inválidos.

Criterio de salida: un usuario puede registrar un paquete aun cuando marca y tabla estén en caras distintas, sin duplicar entidades.

### P2 — Política JEV con datos reales

1. Registrar métricas sin PII: latencia, disponibilidad, costo, acuerdo con intención y edición humana posterior.
2. Comparar durante al menos 200 capturas revisadas.
3. Segmentar `FOOD` y `RECIPE`; medir falso bloqueo y correcciones evitadas.
4. Definir umbrales únicamente después del piloto.
5. Pasar gradualmente de `shadow` a `advisory`; no habilitar bloqueo automático sin un nuevo criterio de aceptación.

Criterio de viabilidad sugerido: disponibilidad ≥ 99 %, p95 compatible con el flujo, costo aceptable por confirmación y mejora medible de errores sin aumentar abandonos.

### P3 — Limpieza del legado

1. Migrar consumidores restantes de `AI_ESTIMATE` a `Food`/`Recipe`.
2. Medir durante una versión que no existan llamadas al endpoint legado.
3. Retirar confirmación y edición legacy.
4. Conservar lectura histórica o ejecutar una migración auditable de registros antiguos.

### P4 — Calidad y operación

1. Dashboard de embudo: captura → análisis → revisión → confirmación → descarte.
2. Alarmas separadas para Gemini y JEV.
3. Job de purga de capturas vencidas y política de retención de imágenes (actualmente no se persisten).
4. Pruebas E2E móviles con cámara/galería y accesibilidad del diálogo.
5. Feature flags por usuario para despliegue gradual y rollback independiente de JEV.

## Reglas que una IA implementadora debe respetar

- Nunca guardar claves en Git, logs, respuestas HTTP ni frontend.
- No permitir que JEV altere macros en modo `shadow`.
- No crear entidades antes de la confirmación humana.
- Toda confirmación debe ser idempotente y pertenecer al usuario autenticado.
- `FOOD` produce un solo alimento; `RECIPE` produce una receta compuesta.
- Mantener compatibilidad hasta verificar telemetría antes de eliminar el legado.
- Ejecutar migraciones, tests backend, build/tests frontend y una prueba real de ambos caminos antes de cada promoción.

## Decisión sobre JEV

**Viable como clasificador y control de calidad secundario.** No debe reemplazar a Gemini para extraer nutrientes ni ser fuente nutricional. El modo inicial correcto es `shadow`: permite medir precisión, latencia y costo con tráfico real sin perjudicar la experiencia. La decisión de promoverlo a `advisory` queda condicionada a las métricas de P2.
