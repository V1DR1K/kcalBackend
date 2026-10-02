# Release UX/UI

La rama fix/ux-ui-audit sólo ejecuta CI. Producción se despliega únicamente desde main después de aprobar build. Requiere Java 21 y Docker operativo; PostgreSQL 17 y Flyway no se omiten si falta Docker.

## Orden

1. Completar los 33 commits, publicar ambas ramas y esperar CI aprobado, incluidos PostgreSQL 17 desde base nueva y upgrade V43→V47 y recorridos Chromium/WebKit. Integrar cambios de origin/main sin sobrescribir trabajo ajeno.
2. Publicar backend main primero. release-api.sh exige la revisión completa, identifica contenedores desde el compose existente y falla antes de desplegar si no los encuentra. Guarda pg_dump custom, verifica su índice con pg_restore --list y conserva la imagen anterior comprimida. Los archivos privados quedan en /opt/backups/scalegrams/ux-audit-REVISION con permisos restrictivos. READY sólo existe tras completar respaldo y registro de versión anterior.
3. Verificar salud y /api/version con la revisión exacta; luego publicar frontend main. Su workflow comprueba version.json y conserva su imagen anterior antes de desplegar.
4. Comprobar ingreso, rutas y recursos de producción. Las verificaciones públicas no requieren escrituras. Cualquier recorrido con registros usa nombres y fechas aislados y deja evidencia de su limpieza.

## Compatibilidad

Se conservan cuerpos antiguos mediante campos opcionales, formato de errores y endpoints existentes. Nuevos clientes interpretan completitud/estados/versiones. La reutilización de archivados devuelve 409 explícito; un cliente anterior puede registrar alimentos activos y conservar historial. Una alternativa nunca cambia la meta manual. Las migraciones V44–V47 agregan columnas/índices sin modificar migraciones aplicadas, recortar fechas anteriores ni reescribir calorías históricas.

## Recuperación

Frontend: cargar la imagen guardada con docker image load y configurar el servicio web con su imagen anterior; mantener backend compatible. Conservar assets de la imagen anterior como parte de ella. Comprobar salud web y rutas después de restaurar.

Backend: si existen escrituras posteriores con estados nuevos, aplicar una corrección hacia adelante. No restaurar el dump antiguo sobre esos datos. El respaldo es para recuperación ante pérdida de base, con una decisión explícita sobre datos posteriores y en una instancia aislada antes de sustituir producción. No ejecutar pg_restore --clean contra producción como rollback rutinario.

El dump contiene datos privados: no subirlo a Git ni adjuntarlo a CI. La imagen y revisión anterior permiten reproducir el estado previo. Las pruebas de teclado emulan visualViewport; no sustituyen validación en Chrome Android/Safari iOS físicos.
