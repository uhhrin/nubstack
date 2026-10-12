#!/usr/bin/env bash
# Seed the running testbed for demos and the Mineflayer suite.
#   - op Bot1 and Bot2 on every running backend (and the optional IGN)
#   - set Bot1, Bot2 and the IGN to a 500 ledger balance through the hub console (idempotent)
#   - optional IGN: add staff nodes on Velocity so /aegis, /gatekeep and /harbor admin work
# Usage: ./testbed/scripts/seed-demo.sh [IGN]
#
# Ledger only targets players who are online or in the server name cache, so a bot or IGN
# that has never joined is reported and skipped; join once and run this again.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
COMPOSE=(docker compose --profile minigame --profile fabric)
IGN="${1:-}"
PLAYERS=(Bot1 Bot2)
[[ -n "$IGN" ]] && PLAYERS+=("$IGN")

if [[ -n "$IGN" && ! "$IGN" =~ ^[A-Za-z0-9_]{3,16}$ ]]; then
  echo "invalid Minecraft name: $IGN" >&2
  exit 1
fi

backends=()
for svc in hub minigame fabric; do
  if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx "$svc"; then
    backends+=("$svc")
  fi
done
if [[ ! " ${backends[*]} " =~ " hub " ]]; then
  echo "hub is not running; start the stack first" >&2
  exit 1
fi

for player in "${PLAYERS[@]}"; do
  for svc in "${backends[@]}"; do
    "${COMPOSE[@]}" exec -T "$svc" rcon-cli "op $player" >/dev/null && echo "op $player on $svc"
  done
done

for player in "${PLAYERS[@]}"; do
  out="$("${COMPOSE[@]}" exec -T hub rcon-cli "ledger set $player 500 seed-demo" 2>&1 || true)"
  echo "ledger $player: ${out:-set to 500}"
done

if [[ -n "$IGN" ]]; then
  # Velocity has no op list. LuckPerms (JSON storage) reads user files named by the offline UUID.
  uuid="$(python3 - "$IGN" <<'PY'
import hashlib, sys
h = bytearray(hashlib.md5(("OfflinePlayer:" + sys.argv[1]).encode()).digest())
h[6] = (h[6] & 0x0F) | 0x30
h[8] = (h[8] & 0x3F) | 0x80
x = h.hex()
print(f"{x[:8]}-{x[8:12]}-{x[12:16]}-{x[16:20]}-{x[20:]}")
PY
)"
  json="{\"uuid\":\"$uuid\",\"name\":\"$IGN\",\"primaryGroup\":\"staff\",\"permissions\":[{\"group.staff\":true}]}"
  "${COMPOSE[@]}" exec -T velocity sh -c "printf '%s' '$json' > /server/plugins/luckperms/json-storage/users/$uuid.json"
  echo "velocity: $IGN is now staff ($uuid); LuckPerms reloads the file, rejoin if commands still say no permission"
fi

echo "done"
