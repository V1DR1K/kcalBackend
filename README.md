# kcalBackend

Backend Spring Boot para KazaFitness, producto de KazaDesarrollos.

Por defecto usa PostgreSQL persistente y Ceph RGW compatible con S3 para imagenes de productos. Los datos demo se cargan con un initializer sin pisar datos existentes.

## Stack

- Java 21
- Spring Boot 3.5
- Spring Security + JWT token
- Spring Data JPA / Hibernate Jakarta
- PostgreSQL en Docker
- Flyway para migraciones
- Ceph RGW/S3 local en Docker para imagenes
- H2 en memoria para tests
- Bean Validation
- OpenAPI Swagger UI

## Correr local

1. Levantar infraestructura:

```powershell
docker compose up -d
```

2. Correr backend:

```powershell
.\mvnw.cmd spring-boot:run
```

API: `http://localhost:8081/api`

Swagger: `http://localhost:8081/swagger-ui.html`

PostgreSQL: `localhost:5433`, DB `kcal_db`, user `kcal_user`, password `kcal_password`

Ceph/S3 local: `http://localhost:7480`, bucket `kcal-products`

## Fuentes externas de alimentos

El endpoint `GET /api/foods/barcode/{barcode}` busca primero en PostgreSQL. Si no existe y `app.food-lookup.enabled=true`, consulta Open Food Facts, normaliza kcal/macros por 100g, guarda el alimento localmente y lo devuelve. La búsqueda `GET /api/foods?q=texto` también consulta productos comercializados en Argentina cuando los resultados locales son escasos y conserva únicamente fichas con kcal y macros completos. Las búsquedas locales incluyen nombre, marca y etiquetas/sinónimos. USDA FoodData Central queda configurado para enriquecimiento genérico.

Variables utiles:

- `FOOD_LOOKUP_ENABLED=true`
- `FOOD_LOOKUP_TIMEOUT=3s`
- `OPEN_FOOD_FACTS_BASE_URL=https://world.openfoodfacts.org`
- `OPEN_FOOD_FACTS_USER_AGENT=KazaFitness/0.1 (development; contact@kazadesarrollos.com)`
- `USDA_FOOD_DATA_API_KEY=`

Fuentes: Open Food Facts y USDA FoodData Central.

Si `8081` esta ocupado:

```powershell
$env:SERVER_PORT='8082'; .\mvnw.cmd spring-boot:run
```

## Tests

Los tests usan H2 en memoria y no necesitan Docker:

```powershell
.\mvnw.cmd test
```

## Producción

El perfil `prod` carga el catálogo base pero desactiva usuarios/datos demo y Swagger, valida el esquema y aplica las migraciones Flyway antes de iniciar. El HTTPS debe terminarse en el proxy o balanceador de la plataforma.

Variables obligatorias:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:5432/DB
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
JWT_SECRET=una-clave-aleatoria-de-al-menos-32-bytes
CORS_ALLOWED_ORIGINS=https://app.ejemplo.com
STORAGE_ENDPOINT=https://s3.ejemplo.com
STORAGE_PUBLIC_BASE_URL=https://cdn.ejemplo.com
STORAGE_ACCESS_KEY=...
STORAGE_SECRET_KEY=...
```

Inicio:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
.\mvnw.cmd spring-boot:run
```

Para generar el artefacto desplegable:

```powershell
.\mvnw.cmd clean package
java -jar target/kcalBackend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

No se versionan claves TLS ni credenciales de producción. Las credenciales de `docker-compose.yml` son únicamente para desarrollo local.

## Usuario demo

- Email: `alex@kazadesarrollos.com`
- Password: `password123`

## Flujo base

1. `POST /api/auth/register` o `POST /api/auth/login`
2. Usar `Authorization: Bearer <token>`
3. Consultar dashboard, alimentos, historial y perfil.
