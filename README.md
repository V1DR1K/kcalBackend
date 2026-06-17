# kcalBackend

Backend Spring Boot para Vitality Peak / NutriTrack Pro.

## Stack

- Java 21
- Spring Boot 3.5
- Spring Security + JWT token
- Spring Data JPA / Hibernate Jakarta
- PostgreSQL en Docker
- Bean Validation
- OpenAPI Swagger UI

## Correr local

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

API: `http://localhost:8080/api`

Swagger: `http://localhost:8080/swagger-ui.html`

PostgreSQL queda expuesto en `localhost:5433` para no chocar con instalaciones locales en `5432`.

## Usuario demo

- Email: `alex@vitality.com`
- Password: `password123`

## Flujo base

1. `POST /api/auth/register` o `POST /api/auth/login`
2. Usar `Authorization: Bearer <token>`
3. Consultar dashboard, alimentos, historial y perfil.
