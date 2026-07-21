#!/usr/bin/env bash
set -euo pipefail

exec /opt/infra/bin/deploy-service kcals "${1:-api}"
