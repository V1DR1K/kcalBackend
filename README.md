# kcalBackend

Backend Spring Boot para Vitality Peak / NutriTrack Pro.

Por defecto corre en modo mock rapido con H2 en memoria y datos semilla. No necesita Docker ni PostgreSQL para probar el frontend.

## Stack

- Java 21
- Spring Boot 3.5
- Spring Security + JWT token
- Spring Data JPA / Hibernate Jakarta
- H2 en memoria para mocks locales
- PostgreSQL opcional en Docker
- Bean Validation
- OpenAPI Swagger UI

## Correr local

```powershell
.\mvnw.cmd spring-boot:run
```

API: `http://localhost:8080/api`

Swagger: `http://localhost:8080/swagger-ui.html`

H2 console: `http://localhost:8080/h2-console`

JDBC URL: `jdbc:h2:mem:kcal_mock`

Si `8080` esta ocupado:

```powershell
$env:SERVER_PORT='8081'; .\mvnw.cmd spring-boot:run
```

## PostgreSQL opcional

El `docker-compose.yml` queda disponible por si despues queres volver a DB real. PostgreSQL queda expuesto en `localhost:5433`.

## Usuario demo

- Email: `alex@vitality.com`
- Password: `password123`

## Flujo base

1. `POST /api/auth/register` o `POST /api/auth/login`
2. Usar `Authorization: Bearer <token>`
3. Consultar dashboard, alimentos, historial y perfil.
