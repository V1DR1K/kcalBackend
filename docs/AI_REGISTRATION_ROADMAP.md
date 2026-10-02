# Roadmap: Registro y Estimación IA

## Objetivo

Unificar la captura asistida por IA en un flujo con intención explícita:

- **Alimento**: reconocer un único producto o alimento, materializarlo en el catálogo y, opcionalmente, registrar la porción en el diario.
- **Comida**: reconocer los componentes de un plato, materializarlos como alimentos, crear una receta y registrar una porción de esa receta.

La imagen y la respuesta del proveedor son un borrador. La fuente de verdad sigue siendo el modelo de dominio `Food`, `Recipe` y `FoodLog` después de la revisión humana.

## Arquitectura objetivo

```text
Foto + intención
      │
      ▼
Gemini: extracción nutricional
      │
      ▼
AiCapture DRAFT (vence en 24 h)
      │ revisión humana
      ▼
Confirmación idempotente
      ├── FOOD   → Food → (opcional) FoodLog
      └── RECIPE → Foods → Recipe → FoodLog (1 porción)
```

## Estado de implementación

### Fase 1 — Contrato y persistencia: completada

- `AiCapture` persiste intención, estado, borrador y registro confirmado.
- Estados: `DRAFT`, `CONFIRMED`, `DISCARDED`.
- La confirmación bloquea la captura, valida propietario/vencimiento y es idempotente.
- Migraciones Flyway `V40__ai_capture_workflow.sql` y `V41__support_ai_registration_lifecycle.sql`.
- Capturas confirmadas conservan referencias independientes a `Food`, `Recipe` y `FoodLog`; si se borra el registro del diario, la referencia se limpia y la captura sigue siendo idempotente.

### Fase 2 — Materialización del dominio: completada

- `FOOD` exige exactamente un ítem; reutiliza una coincidencia explícita del catálogo o crea un alimento por 100 g. La decisión revisada de macros no se sustituye silenciosamente por otra ficha.
- `RECIPE` resuelve o crea los alimentos componentes, crea la receta con pesos y nutrientes y registra una porción.
- Se mantiene temporalmente el endpoint legado para no romper clientes anteriores.

### Fase 3 — Experiencia “Registrar”: primera versión completada

- Preselector explícito **Registrar alimento con foto** / **Registrar comida con foto**.
- Revisión editable de nombre, gramos y macronutrientes antes de confirmar.
- Registro FOOD guarda el alimento en el catálogo; agregarlo al diario es opcional y permite elegir comida y fecha.
- El flujo existente de foto en una comida utiliza `RECIPE`.
- La corrección de una estimación conserva su intención original.

### Fase 4 — JEV: retirado

- Se retiraron cliente, llamada, configuración y etiquetas porque la integración agregaba fallos sin mejorar el flujo de registro.
- La columna histórica `jev_decision_json` se conserva en la base de datos y no se consulta ni se modifica.

### Correcciones cerradas antes de promoción

- Se elimina la dependencia obligatoria de `FoodLog` para conservar una captura FOOD/RECIPE confirmada; `ON DELETE SET NULL` permite borrar el registro del diario.
- Se distingue “guardar en catálogo” de “agregar también a mi día”; la ruta de escáner permanece abierta cuando solo se guarda el alimento.
- Los macros son editables y la edición invalida una coincidencia de catálogo para que los valores revisados sean los que se materialicen.
- El destino se resuelve por cantidad: un alimento se guarda como alimento; dos o más forman una receta.

## Próximos incrementos recomendados

### P1 — Captura guiada múltiple para alimentos

1. Aceptar varias imágenes por captura (`frente`, `tabla nutricional`, `ingredientes/código`).
2. Comprimir en cliente y fijar límites por archivo y por captura.
3. Permitir “Analizar ahora” desde una foto y recomendar otra cuando falte evidencia.
4. Mostrar qué dato provino de etiqueta, catálogo o inferencia.
5. Añadir pruebas de archivos repetidos, orden, tamaño y formatos inválidos.

Criterio de salida: un usuario puede registrar un paquete aun cuando marca y tabla estén en caras distintas, sin duplicar entidades.

### P2 — Diagnóstico y resiliencia del proveedor

1. Correlacionar análisis, correcciones y confirmaciones con un identificador de solicitud.
2. Medir latencia y disponibilidad sin registrar fotos, contexto ni datos personales.
3. Mantener un único reintento ante errores de transporte y respetar los límites de cuota del proveedor.

### P3 — Limpieza del legado

1. Migrar consumidores restantes de `AI_ESTIMATE` a `Food`/`Recipe`.
2. Medir durante una versión que no existan llamadas al endpoint legado.
3. Retirar confirmación y edición legacy.
4. Conservar lectura histórica o ejecutar una migración auditable de registros antiguos.

### P4 — Calidad y operación

1. Dashboard de embudo: captura → análisis → revisión → confirmación → descarte.
2. Alertas de disponibilidad y latencia de Gemini.
3. Job de purga de capturas vencidas y política de retención de imágenes (actualmente no se persisten).
4. Pruebas E2E móviles con cámara/galería y accesibilidad del diálogo.
5. Feature flags por usuario para despliegue gradual.

## Reglas que una IA implementadora debe respetar

- Nunca guardar claves en Git, logs, respuestas HTTP ni frontend.
- No registrar fotos, contexto, claves ni datos personales en logs.
- No crear entidades antes de la confirmación humana.
- Toda confirmación debe ser idempotente y pertenecer al usuario autenticado.
- `FOOD` produce un solo alimento; `RECIPE` produce una receta compuesta.
- Mantener compatibilidad hasta verificar telemetría antes de eliminar el legado.
- Ejecutar migraciones, tests backend, build/tests frontend y una prueba de ambos caminos antes de cada promoción.

## Decisión sobre JEV

JEV quedó retirado. La columna histórica `jev_decision_json` permanece para conservar datos previos; una limpieza futura requeriría una migración auditable y explícita.
