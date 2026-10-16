#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
port=$(python3 -c "from pathlib import Path; print(dict(l.split('=',1) for l in Path('deploy/env/.env').read_text().splitlines() if l and not l.startswith('#')).get('WEB_PORT','18080'))")
curl -fsS "http://127.0.0.1:$port/health"
curl -fsS "http://127.0.0.1:$port/actuator/health"
printf '\nFlowPilot: http://localhost:%s\n' "$port"
