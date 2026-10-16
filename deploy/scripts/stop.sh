#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
docker compose --env-file deploy/env/.env -f deploy/compose/docker-compose.yml down
