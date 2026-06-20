# Informe de implementacion - Vitality Peak backend

## Escaneo del frontend

El repositorio `kcalFrontend` contiene prototipos HTML estaticos generados desde Google Stitch. No habia integracion real con API (`fetch`, `axios` o storage persistente), por lo que se infirieron contratos desde campos, botones, textos y datos visibles de cada vista.

## Decisiones tecnicas

- Spring Boot 3.5 + Java 21 para mantener el backend moderno y compatible con Jakarta.
- Spring Security stateless con JWT Bearer para login, registro y consumo desde Vite.
- Hibernate/JPA con PostgreSQL persistente por defecto; H2 queda solo para tests automatizados.
- Enums para estados de dominio: `Gender`, `ActivityLevel`, `FitnessGoal`, `Role`, `FoodCategory`, `FoodUnit`, `MealType`.
- OpenAPI en `/swagger-ui.html` para validar request/response desde navegador.
- `docker-compose.yml` queda disponible para PostgreSQL real, pero no hace falta para probar el frontend.

## Vistas y endpoints cubiertos

### Iniciar sesion

Necesita email y password.

- `POST /api/auth/login`
- Request: `{ "email": "alex@vitality.com", "password": "password123" }`
- Response: JWT Bearer y resumen del usuario.

### Registro de usuario

La pantalla tiene tres pasos: cuenta, datos fisicos y objetivo.

- `POST /api/auth/register`
- Request: `fullName`, `email`, `password`, `weightKg`, `heightCm`, `birthDate`, `gender`, `goal`, `activityLevel`.
- Response: JWT Bearer y resumen del usuario.
- El backend calcula metas iniciales de calorias y macros con Mifflin-St Jeor ajustado por actividad y objetivo.

### Dashboard diario

Muestra fecha, calorias restantes, meta, ingeridas, macros por objetivo, comidas e hidratacion.

- `GET /api/nutrition/dashboard?date=2026-06-17`
- Response: `calorieGoal`, `caloriesConsumed`, `caloriesRemaining`, `macros`, `meals`, `waterConsumedLiters`, `waterGoalLiters`.

### Buscador de alimentos

Incluye buscador textual, categorias, cards y acceso a escaneo.

- `GET /api/foods?q=pollo`
- `GET /api/foods?category=PROTEIN`
- Response: alimentos con kcal/macros por 100g, unidad base, categoria, tags, marca e imagen opcional.

### Configurar alimento

La pantalla permite elegir comida, cantidad y unidad, y recalcula kcal/macros.

- `POST /api/foods/preview`
- Request: `{ "foodId": 1, "quantity": 150, "unit": "GRAM" }`
- Response: macros estimados para esa cantidad.
- `POST /api/nutrition/food-logs`
- Request: `{ "foodId": 1, "mealType": "LUNCH", "quantity": 150, "unit": "GRAM", "logDate": "2026-06-17" }`
- Response: registro guardado con alimento y totales.

### Escaner de codigo

La pantalla simula codigo de barras y muestra alimento encontrado.

- `GET /api/foods/barcode/{barcode}`
- Ejemplo seed: `7790000000059` devuelve `Atun en lata`.

### Historial

Muestra calendario mensual, resumen diario, macros y racha/adherencia.

- `GET /api/nutrition/history?year=2026&month=6`
- Response: dias del mes con calorias, metas, macros y `goalReached`.

### Mi perfil

Muestra datos personales, peso, altura, edad, actividad, objetivo, estilo nutricional y metas.

- `GET /api/profile`
- `PATCH /api/profile`
- Request parcial: `fullName`, `weightKg`, `heightCm`, `birthDate`, `gender`, `activityLevel`, `goal`, `targetWeightKg`, `nutritionStyle`, `waterGoalLiters`.
- Al actualizar datos fisicos o objetivo, recalcula metas nutricionales.

### Hidratacion

El dashboard muestra litros consumidos contra meta.

- `POST /api/nutrition/water-logs`
- Request: `{ "liters": 0.5, "logDate": "2026-06-17" }`

## Datos semilla

Se crean alimentos usados por las pantallas en cada arranque del modo mock: pechuga de pollo, arroz blanco, palta, yogur griego, atun en lata y banana.

Tambien se crea usuario demo:

- Email: `alex@vitality.com`
- Password: `password123`

## Pendientes recomendados

- Conectar el frontend HTML a estos endpoints o migrarlo a componentes reales.
- Agregar refresh token si la app requiere sesiones largas.
- Agregar tests de controllers con Spring Security.
- Mejorar unidades no basadas en gramos cuando el frontend defina equivalencias reales para porciones/unidades.
