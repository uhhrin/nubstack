#!/usr/bin/env bash
# Wait for every running backend (hub, minigame, fabric), print plugin startup lines, and op players.
# Usage: ./testbed/scripts/wait-and-op.sh [player ...]   (default: thatnubarin)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
COMPOSE=(docker compose --profile minigame --profile fabric)
if [[ $# -gt 0 ]]; then
  PLAYERS=("$@")
else
  PLAYERS=(thatnubarin)
fi

running_backends() {
  local svc
  for svc in hub minigame fabric; do
    if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx "$svc"; then
      echo "$svc"
    fi
  done
}

wait_rcon() {
  local service="$1"
  local i
  for i in $(seq 1 90); do
    if "${COMPOSE[@]}" exec -T "$service" rcon-cli list >/dev/null 2>&1; then
      echo "$service rcon ready ($i)"
      return 0
    fi
    sleep 5
  done
  echo "$service rcon timeout" >&2
  return 1
}

mapfile -t BACKENDS < <(running_backends)
if [[ ${#BACKENDS[@]} -eq 0 ]]; then
  echo "no backend is running; start the stack first (see testbed/README.md)" >&2
  exit 1
fi

echo "Waiting for: ${BACKENDS[*]}"
for service in "${BACKENDS[@]}"; do
  wait_rcon "$service"
done

for service in "${BACKENDS[@]}"; do
  echo "=== $service plugin lines ==="
  "${COMPOSE[@]}" logs "$service" 2>&1 | grep -Ei 'Done \(|Error occurred|No suitable|enabled|loaded [0-9]+ grave|aegis|ledger|loom|tether|cairn' | tail -30 || true
done
echo "=== velocity ==="
"${COMPOSE[@]}" logs velocity 2>&1 | grep -Ei 'Done|Loaded|ERROR|Could.t|enabled|AccessDenied|chown' | tail -30 || true

for player in "${PLAYERS[@]}"; do
  for service in "${BACKENDS[@]}"; do
    echo "=== op $player on $service ==="
    "${COMPOSE[@]}" exec -T "$service" rcon-cli "op $player"
  done
done

for service in "${BACKENDS[@]}"; do
  echo "=== $service ops.json ==="
  "${COMPOSE[@]}" exec -T "$service" cat /data/ops.json
  echo
done
"${COMPOSE[@]}" ps -a
