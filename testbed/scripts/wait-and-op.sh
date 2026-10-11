#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
COMPOSE=(docker compose --profile minigame)
PLAYER="${1:-thatnubarin}"

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

echo "Waiting for hub and minigame..."
wait_rcon hub
wait_rcon minigame

echo "=== hub plugin lines ==="
"${COMPOSE[@]}" logs hub 2>&1 | rg -i 'Done \(|Error occurred|No suitable|enabled|aegis|ledger|loom|tether' | tail -40 || true
echo "=== minigame plugin lines ==="
"${COMPOSE[@]}" logs minigame 2>&1 | rg -i 'Done \(|Error occurred|No suitable|enabled|aegis|ledger|loom|tether' | tail -40 || true
echo "=== velocity ==="
"${COMPOSE[@]}" logs velocity 2>&1 | rg -i 'Done|Loaded|ERROR|Could.t|enabled|AccessDenied|chown' | tail -30 || true

echo "=== op $PLAYER ==="
"${COMPOSE[@]}" exec -T hub rcon-cli "op $PLAYER"
"${COMPOSE[@]}" exec -T minigame rcon-cli "op $PLAYER"
"${COMPOSE[@]}" exec -T hub cat /data/ops.json
echo
"${COMPOSE[@]}" exec -T minigame cat /data/ops.json
echo
"${COMPOSE[@]}" ps -a
