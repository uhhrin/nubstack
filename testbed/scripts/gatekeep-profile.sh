#!/usr/bin/env bash
# Swap the running Velocity gatekeep config between the default and the strict overlay.
# Usage: ./testbed/scripts/gatekeep-profile.sh strict|default
#
# Velocity has no RCON and the console is not attached, so the script only copies the file.
# Apply it with `/gatekeep reload` as a staff player (Bot1 is staff through LuckPerms), or
# restart velocity. The Mineflayer scenario `npm run test:gatekeep` does both steps itself.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${1:-}"
case "$PROFILE" in
  strict) SRC="$ROOT/plugin-config/velocity/gatekeep-strict/config.yml" ;;
  default) SRC="$ROOT/plugin-config/velocity/gatekeep/config.yml" ;;
  *)
    echo "usage: $0 strict|default" >&2
    exit 1
    ;;
esac

docker compose -f "$ROOT/docker-compose.yml" cp "$SRC" velocity:/server/plugins/gatekeep/config.yml
echo "copied $PROFILE gatekeep config; run /gatekeep reload in game (or restart velocity) to apply"
