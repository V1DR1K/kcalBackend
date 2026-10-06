#!/usr/bin/env bash
set -euo pipefail
umask 077
revision="${1:?Expected full Git revision}"
[[ "$revision" =~ ^[a-f0-9]{40}$ ]] || exit 2
test "$(git rev-parse HEAD)" = "$revision"
compose=(docker compose -f docker-compose.prod.yml)
postgres_id="$("${compose[@]}" ps -q postgres)"
api_id="$("${compose[@]}" ps -q app)"
test -n "$postgres_id" && test -n "$api_id"
container_value() {
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$1" \
    | sed -n "s/^$2=//p" | head -n 1
}
POSTGRES_USER="${POSTGRES_USER:-$(container_value "$postgres_id" POSTGRES_USER)}"
POSTGRES_DB="${POSTGRES_DB:-$(container_value "$postgres_id" POSTGRES_DB)}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(container_value "$postgres_id" POSTGRES_PASSWORD)}"
POSTGRES_USER="${POSTGRES_USER:-$(container_value "$api_id" SPRING_DATASOURCE_USERNAME)}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(container_value "$api_id" SPRING_DATASOURCE_PASSWORD)}"
if [[ -z "$POSTGRES_DB" ]]; then
  datasource_url="$(container_value "$api_id" SPRING_DATASOURCE_URL)"
  if [[ "$datasource_url" =~ ^jdbc:postgresql://[^/]+/([^?]+) ]]; then
    POSTGRES_DB="${BASH_REMATCH[1]}"
  fi
fi
if [[ -z "$POSTGRES_USER" || -z "$POSTGRES_DB" || -z "$POSTGRES_PASSWORD" ]]; then
  printf 'PostgreSQL credentials could not be recovered from the running services; deployment stopped before backup.\n' >&2
  exit 1
fi
export POSTGRES_USER POSTGRES_DB POSTGRES_PASSWORD
backup_root="${SCALEGRAMS_BACKUP_ROOT:-${XDG_STATE_HOME:-$HOME/.local/state}/scalegrams/backups}"
backup="$backup_root/ux-audit-$revision"
mkdir -p "$backup"
if [[ ! -f "$backup/READY" ]]; then
  docker exec -e POSTGRES_USER="$POSTGRES_USER" -e POSTGRES_DB="$POSTGRES_DB" \
    -e PGPASSWORD="$POSTGRES_PASSWORD" "$postgres_id" \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup/postgres.dump.tmp"
  test -s "$backup/postgres.dump.tmp"
  docker exec -i "$postgres_id" pg_restore --list < "$backup/postgres.dump.tmp" > "$backup/postgres.contents"
  mv "$backup/postgres.dump.tmp" "$backup/postgres.dump"
  api_image="$(docker inspect --format '{{.Image}}' "$api_id")"
  docker image save "$api_image" | gzip > "$backup/api-image.tar.gz.tmp"
  gzip -t "$backup/api-image.tar.gz.tmp"
  mv "$backup/api-image.tar.gz.tmp" "$backup/api-image.tar.gz"
  docker ps --format '{{.Names}} {{.Image}} {{.ID}}' | grep scalegrams > "$backup/previous-containers.txt"
  printf '%s\n' "$api_image" > "$backup/api-image-id.txt"
  if ! curl --fail --silent --show-error https://scalegrams.neticar.com.ar/api/version > "$backup/previous-api-version.json"; then
    printf '{"status":"unavailable"}\n' > "$backup/previous-api-version.json"
  fi
  touch "$backup/READY"
fi
# The API deployer only replaces the app service; refresh Postgres so V50 can load pgvector.
"${compose[@]}" pull postgres
"${compose[@]}" up -d --no-deps postgres
postgres_id="$("${compose[@]}" ps -q postgres)"
for attempt in $(seq 1 30); do
  postgres_health="$(docker inspect --format '{{.State.Health.Status}}' "$postgres_id" 2>/dev/null || true)"
  [[ "$postgres_health" == "healthy" ]] && break
  sleep 2
done
if [[ "$postgres_health" != "healthy" ]]; then
  docker logs --tail 100 "$postgres_id" >&2 || true
  printf 'PostgreSQL did not become healthy after updating its image. Backup: %s\n' "$backup" >&2
  exit 1
fi
GIT_HASH="$revision" /opt/infra/bin/deploy-service scalegrams api
for attempt in $(seq 1 12); do
  health="$(curl --fail --silent https://scalegrams.neticar.com.ar/api/health || true)"
  version="$(curl --fail --silent https://scalegrams.neticar.com.ar/api/version || true)"
  if grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"' <<<"$health" && grep -Fq "$revision" <<<"$version"; then
    /opt/infra/bin/update-repository-images || printf "Warning: repository image snapshot was not refreshed.\n" >&2
    printf 'API healthy; published revision %s; backup %s\n' "$revision" "$backup"
    exit 0
  fi
  sleep 5
done
printf 'API revision/health verification failed. Backup: %s\n' "$backup" >&2
exit 1
