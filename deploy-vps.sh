#!/usr/bin/env bash
set -euo pipefail

exec 9>/tmp/kcals-deploy.lock
until flock -n 9; do
  echo "Another KCALS deploy is running; waiting for the deploy lock..."
  sleep 10
done

cd "$(dirname "$0")"

git fetch origin main
git reset --hard origin/main

if [ -d ../kcalFrontend ]; then
  git -C ../kcalFrontend fetch origin main
  git -C ../kcalFrontend reset --hard origin/main
fi

GIT_HASH=$(git rev-parse --short HEAD)
export GIT_HASH
docker compose --env-file .env -f docker-compose.prod.yml config --quiet
docker compose --env-file .env -f docker-compose.prod.yml build app frontend
docker compose --env-file .env -f docker-compose.prod.yml rm -f -s app frontend
docker compose --env-file .env -f docker-compose.prod.yml up -d --remove-orphans
docker compose --env-file .env -f docker-compose.prod.yml ps
