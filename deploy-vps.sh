#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
GIT_HASH=$(git rev-parse --short HEAD)
export GIT_HASH
docker compose --env-file .env -f docker-compose.prod.yml config --quiet
docker compose --env-file .env -f docker-compose.prod.yml build app frontend
docker compose --env-file .env -f docker-compose.prod.yml up -d --remove-orphans
docker compose --env-file .env -f docker-compose.prod.yml ps
