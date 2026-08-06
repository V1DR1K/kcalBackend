#!/usr/bin/env bash
set -euo pipefail

exec /opt/infra/bin/deploy-service scalegrams "${1:-api}"
