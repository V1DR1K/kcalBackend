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

# V50 uses pgvector. Refresh the configured image before the logical backup so
# pg_dump can inspect vector indexes left by an earlier release. Never let this
# step turn into an implicit PostgreSQL major-version upgrade.
postgres_image="$("${compose[@]}" config --images | grep '^pgvector/pgvector:' | head -n 1)"
target_pg_major="${postgres_image##*:pg}"
current_pg_version_num="$(docker exec "$postgres_id" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc 'SHOW server_version_num')"
if [[ ! "$target_pg_major" =~ ^[0-9]+$ || ! "$current_pg_version_num" =~ ^[0-9]+$ ]]; then
  printf 'Could not verify the PostgreSQL major version; deployment stopped before backup.\n' >&2
  exit 1
fi
current_pg_major="$((current_pg_version_num / 10000))"
if [[ "$current_pg_major" != "$target_pg_major" ]]; then
  printf 'PostgreSQL data uses major %s but the configured image targets %s; refusing an automatic major-version upgrade.\n' \
    "$current_pg_major" "$target_pg_major" >&2
  exit 1
fi

previous_postgres_image="$(docker inspect --format '{{.Config.Image}}' "$postgres_id")"
if [[ ! -f "$backup/READY" ]]; then
  printf '%s\n' "$previous_postgres_image" > "$backup/postgres-image-before-release.txt"
fi
restore_previous_postgres_image() {
  local override
  override="$(mktemp)"
  printf 'services:\n  postgres:\n    image: %s\n' "$previous_postgres_image" > "$override"
  if ! "${compose[@]}" -f "$override" up -d --no-deps postgres; then
    printf 'Could not restore the previous PostgreSQL image (%s).\n' "$previous_postgres_image" >&2
  fi
  rm -f "$override"
}
"${compose[@]}" pull postgres
if ! "${compose[@]}" up -d --no-deps postgres; then
  restore_previous_postgres_image
  printf 'Could not start the configured PostgreSQL image. Deployment stopped before backup.\n' >&2
  exit 1
fi
postgres_id="$("${compose[@]}" ps -q postgres)"
postgres_health=""
for attempt in $(seq 1 30); do
  postgres_health="$(docker inspect --format '{{.State.Health.Status}}' "$postgres_id" 2>/dev/null || true)"
  [[ "$postgres_health" == "healthy" ]] && break
  sleep 2
done
if [[ "$postgres_health" != "healthy" ]]; then
  docker logs --tail 100 "$postgres_id" >&2 || true
  restore_previous_postgres_image
  printf 'PostgreSQL did not become healthy after refreshing its image. Deployment stopped before backup.\n' >&2
  exit 1
fi

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
