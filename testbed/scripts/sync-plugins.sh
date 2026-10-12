#!/usr/bin/env bash
# Copy built plugin jars + Docker-ready configs into the compose plugin mounts.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PAPER_OUT="$ROOT/testbed/plugins/paper"
VEL_OUT="$ROOT/testbed/plugins/velocity"
FABRIC_OUT="$ROOT/testbed/plugins/fabric"
PAPER_CFG="$ROOT/testbed/plugin-config/paper"
VEL_CFG="$ROOT/testbed/plugin-config/velocity"

mkdir -p "$PAPER_OUT" "$VEL_OUT" "$FABRIC_OUT"
# Remove previous jars and staged config dirs (keep .gitkeep).
find "$PAPER_OUT" "$VEL_OUT" "$FABRIC_OUT" -mindepth 1 ! -name '.gitkeep' -exec rm -rf {} + 2>/dev/null || true

copy_module_jars() {
  local module="$1"
  local dest="$2"
  local dir="$ROOT/$module/build/libs"
  [[ -d "$dir" ]] || return 0
  local jar
  for jar in "$dir"/*.jar; do
    [[ -e "$jar" ]] || continue
    case "$(basename "$jar")" in
      *-sources.jar|*-javadoc.jar|*-plain.jar|*-dev.jar|*-dev-shadow.jar) continue ;;
    esac
    cp -f "$jar" "$dest/"
    echo "copied $(basename "$jar") -> $(basename "$dest")"
  done
}

for mod in aegis-paper loom spotlight cull ledger amber tether; do
  copy_module_jars "$mod" "$PAPER_OUT"
done

for mod in aegis-velocity harbor gatekeep; do
  copy_module_jars "$mod" "$VEL_OUT"
done

copy_module_jars cairn "$FABRIC_OUT"

# Stage plugin data folders so itzg copies them into /data/plugins (or Velocity
# /server/plugins) without bind-mounting into the writable volume (that broke perms).
if [[ -d "$PAPER_CFG" ]]; then
  cp -a "$PAPER_CFG"/. "$PAPER_OUT/"
  echo "staged paper plugin configs from plugin-config/paper"
fi
if [[ -d "$VEL_CFG" ]]; then
  cp -a "$VEL_CFG"/. "$VEL_OUT/"
  echo "staged velocity plugin configs from plugin-config/velocity"
fi

echo "Paper plugins: $(find "$PAPER_OUT" -name '*.jar' 2>/dev/null | wc -l)"
echo "Velocity plugins: $(find "$VEL_OUT" -name '*.jar' 2>/dev/null | wc -l)"
echo "Fabric mods: $(find "$FABRIC_OUT" -name '*.jar' 2>/dev/null | wc -l)"
