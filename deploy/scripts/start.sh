#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 deploy/scripts/init-env.py
if [[ -d /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ]]; then export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home; export PATH="$JAVA_HOME/bin:$PATH"; fi
if [[ -z "${DOCKER_HOST:-}" && -S "$HOME/.docker/run/docker.sock" ]]; then export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"; fi
mvn -f flowpilot/pom.xml verify
npm --prefix flowpilot-web ci
npm --prefix flowpilot-web run build
docker compose --env-file deploy/env/.env -f deploy/compose/docker-compose.yml up -d --build --wait --wait-timeout 240
bash deploy/scripts/health-check.sh
