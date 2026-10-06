# Backend architecture status

This file is the current operational architecture summary for the backend. The cross-repository cleanup guide is maintained in `kcalFrontend/docs/INFORME_AUDITORIA_CALIDAD.md`.

The backend is organized by business domain. Food and recipe catalogs return bounded pages with stable ordering. Any new unbounded collection endpoint must implement pagination before release.

Verification: `.\\mvnw.cmd test`.

## Production hardening completed

- Actuator health probes and authenticated Prometheus metrics.
- Global food catalog writes restricted to `ADMIN`.
- User role included in the authentication response for role-aware clients.
- Pagination is bounded and indexed. Cursor/keyset pagination remains an explicit migration only when measured catalog volume makes offset latency unacceptable, preserving the current alphabetical contract meanwhile.
- Prometheus now scrapes the private management port from the internal Docker network, retains 30 days and evaluates availability, HTTP 5xx and JVM heap alerts.
