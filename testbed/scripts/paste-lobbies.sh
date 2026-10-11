#!/usr/bin/env bash
# Paste hub schematic and build a simple minigame arena via RCON.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/docker-compose.yml")

wait_rcon() {
  local service="$1"
  local tries=60
  for ((i = 1; i <= tries; i++)); do
    if "${COMPOSE[@]}" exec -T "$service" rcon-cli "list" >/dev/null 2>&1; then
      echo "$service rcon ready"
      return 0
    fi
    sleep 5
  done
  echo "timeout waiting for $service rcon" >&2
  return 1
}

echo "Waiting for hub and minigame..."
wait_rcon hub
wait_rcon minigame

# Hub: install schematic into FAWE folder and paste at spawn
"${COMPOSE[@]}" exec -T hub bash -lc '
  set -e
  mkdir -p /data/plugins/FastAsyncWorldEdit/schematics
  if [[ -f /schematics/hub-lobby.schem ]]; then
    cp -f /schematics/hub-lobby.schem /data/plugins/FastAsyncWorldEdit/schematics/hub-lobby.schem
  fi
'
"${COMPOSE[@]}" exec -T hub rcon-cli "gamemode creative @a"
"${COMPOSE[@]}" exec -T hub rcon-cli "tp @a 0 80 0"
# FAWE commands may require a player context on some builds; try console paste.
"${COMPOSE[@]}" exec -T hub rcon-cli "//schem load hub-lobby" || true
"${COMPOSE[@]}" exec -T hub rcon-cli "//paste -a" || true
"${COMPOSE[@]}" exec -T hub rcon-cli "setworldspawn 0 80 0"

# Minigame: procedural arena (no third-party schematic)
"${COMPOSE[@]}" exec -T minigame rcon-cli "execute in minecraft:overworld run fill -24 64 -24 24 64 24 minecraft:stone"
"${COMPOSE[@]}" exec -T minigame rcon-cli "execute in minecraft:overworld run fill -24 65 -24 24 65 24 minecraft:air"
"${COMPOSE[@]}" exec -T minigame rcon-cli "execute in minecraft:overworld run fill -25 64 -25 25 68 -25 minecraft:stone_bricks"
"${COMPOSE[@]}" exec -T minigame rcon-cli "execute in minecraft:overworld run fill -25 64 25 25 68 25 minecraft:stone_bricks"
"${COMPOSE[@]}" exec -T minigame rcon-cli "execute in minecraft:overworld run fill -25 64 -25 -25 68 25 minecraft:stone_bricks"
"${COMPOSE[@]}" exec -T minigame rcon-cli "execute in minecraft:overworld run fill 25 64 -25 25 68 25 minecraft:stone_bricks"
"${COMPOSE[@]}" exec -T minigame rcon-cli "setworldspawn 0 65 0"
"${COMPOSE[@]}" exec -T minigame rcon-cli "tp @a 0 65 0"

echo "Lobby paste pass finished. Hub uses SpringtimeSpawn schematic when FAWE accepts console paste."
echo "Minigame arena is a stone platform at y=64."
