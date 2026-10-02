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
backup="/opt/backups/scalegrams/ux-audit-$revision"
mkdir -p "$backup"
if [[ ! -f "$backup/READY" ]]; then
  docker exec "$postgres_id" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup/postgres.dump.tmp"
  test -s "$backup/postgres.dump.tmp"
  docker exec -i "$postgres_id" pg_restore --list < "$backup/postgres.dump.tmp" > "$backup/postgres.contents"
  mv "$backup/postgres.dump.tmp" "$backup/postgres.dump"
  api_image="$(docker inspect --format '{{.Image}}' "$api_id")"
  docker image save "$api_image" | gzip > "$backup/api-image.tar.gz.tmp"
  gzip -t "$backup/api-image.tar.gz.tmp"
  mv "$backup/api-image.tar.gz.tmp" "$backup/api-image.tar.gz"
  docker ps --format '{{.Names}} {{.Image}} {{.ID}}' | grep scalegrams > "$backup/previous-containers.txt"
  printf '%s\n' "$api_image" > "$backup/api-image-id.txt"
  curl --fail --silent --show-error https://scalegrams.neticar.com.ar/api/version > "$backup/previous-api-version.json"
  touch "$backup/READY"
fi
GIT_HASH="$revision" /opt/infra/bin/deploy-service scalegrams api
for attempt in $(seq 1 12); do
  health="$(curl --fail --silent https://scalegrams.neticar.com.ar/api/health || true)"
  version="$(curl --fail --silent https://scalegrams.neticar.com.ar/api/version || true)"
  if grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"' <<<"$health" && grep -Fq "$revision" <<<"$version"; then
    printf 'API healthy; published revision %s; backup %s\n' "$revision" "$backup"
    exit 0
  fi
  sleep 5
done
printf 'API revision/health verification failed. Backup: %s\n' "$backup" >&2
exit 1
