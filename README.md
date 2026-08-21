# ScaleGrams Backend

Backend Spring Boot para ScaleGrams.

Por defecto usa PostgreSQL persistente. Los productos usan arte genérico local por categoría; no se almacenan fotos ni URLs de imágenes.

## Stack

- Java 21
- Spring Boot 3.5
- Spring Security + JWT token
- Spring Data JPA / Hibernate Jakarta
- PostgreSQL en Docker
- Flyway para migraciones
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

PostgreSQL: `localhost:5433`, DB `scalegrams_db`, user `scalegrams_user`, password `scalegrams_password`

## Fuentes externas de alimentos y nutrientes

El endpoint `GET /api/foods/barcode/{barcode}` busca primero en PostgreSQL. Si no existe y `app.food-lookup.enabled=true`, consulta Open Food Facts, normaliza kcal/macros por 100g, guarda el alimento localmente y lo devuelve. La búsqueda `GET /api/foods?q=texto` también consulta productos comercializados en Argentina cuando los resultados locales son escasos y conserva únicamente fichas con kcal y macros completos. Las búsquedas locales incluyen nombre, marca y etiquetas/sinónimos. USDA FoodData Central queda configurado para enriquecimiento genérico.

Variables utiles:

- `FOOD_LOOKUP_ENABLED=true`
- `FOOD_LOOKUP_TIMEOUT=3s`
- `OPEN_FOOD_FACTS_BASE_URL=https://world.openfoodfacts.org`
- `OPEN_FOOD_FACTS_USER_AGENT=ScaleGrams/0.1`
- `USDA_FOOD_DATA_API_KEY=`
- `APP_CATALOG_IMPORT_ENABLED=false`
- `APP_CATALOG_IMPORT_BRANDS=Coca-Cola,Mogul,Milka,Fantoche,Ciudad del Lago,Arcor`
- `APP_CATALOG_IMPORT_PAGES_PER_BRAND=3`
- `APP_CATALOG_IMPORT_PAGE_SIZE=100`

La importación masiva está desactivada por defecto. Al activarla, procesa marcas por páginas, espera al menos 6 segundos entre búsquedas y actualiza por código de barras sin duplicar productos.

Fuentes: Open Food Facts y USDA FoodData Central. La migración `V20__normalized_nutrients.sql` crea el catálogo extensible de nutrientes, conserva snapshots por registro y deja los alimentos legacy como perfiles parciales.

Endpoints nutricionales adicionales:

- `POST /api/foods/{id}/enrich`: completa una ficha desde USDA/Open Food Facts.
- `PUT /api/foods/{id}/nutrients`: permite editar valores manuales del propietario o administrador.
- `POST /api/foods/enrich-existing?limit=50`: enriquece progresivamente el catálogo; requiere administrador.
- `GET /api/foods/nutrient-definitions`: devuelve los nutrientes editables y sus unidades.
- `GET /api/nutrition/food-logs/{id}/nutrients`: devuelve el snapshot nutricional de un registro.

Después de desplegar la migración, ejecutar el endpoint de enriquecimiento progresivo por lotes para poblar los alimentos existentes. Los valores no disponibles se mantienen como `MISSING` y no se convierten en cero.

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

El perfil `prod` carga el catálogo base y Swagger queda desactivado; valida el esquema y aplica las migraciones Flyway antes de iniciar. El HTTPS debe terminarse en el proxy o balanceador de la plataforma.

Variables obligatorias:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:5432/DB
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
AUTH_SERVICE_URL=https://auth.ejemplo.com
AUTH_PUBLIC_KEY_PEM="-----BEGIN PUBLIC KEY-----...-----END PUBLIC KEY-----"
AUTH_DEFAULT_ROLE=USER
CORS_ALLOWED_ORIGINS=https://app.ejemplo.com
```

Inicio:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
.\mvnw.cmd spring-boot:run
```

Para generar el artefacto desplegable:

```powershell
.\mvnw.cmd clean package
java -jar target/scalegrams-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

No se versionan claves TLS ni credenciales de producción. Las credenciales de `docker-compose.yml` son únicamente para desarrollo local.

## Flujo base

1. `POST /api/auth/login` con `username` y `password`
2. Usar `Authorization: Bearer <token>`
3. Consultar dashboard, alimentos, historial y perfil.
