#!/usr/bin/env bash
# Copy built plugin jars into the compose plugin mounts.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PAPER_OUT="$ROOT/testbed/plugins/paper"
VEL_OUT="$ROOT/testbed/plugins/velocity"

mkdir -p "$PAPER_OUT" "$VEL_OUT"
rm -f "$PAPER_OUT"/*.jar "$VEL_OUT"/*.jar 2>/dev/null || true

copy_module_jars() {
  local module="$1"
  local dest="$2"
  local dir="$ROOT/$module/build/libs"
  [[ -d "$dir" ]] || return 0
  local jar
  for jar in "$dir"/*.jar; do
    [[ -e "$jar" ]] || continue
    case "$(basename "$jar")" in
      *-sources.jar|*-javadoc.jar|*-plain.jar) continue ;;
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

echo "Paper plugins: $(find "$PAPER_OUT" -name '*.jar' 2>/dev/null | wc -l)"
echo "Velocity plugins: $(find "$VEL_OUT" -name '*.jar' 2>/dev/null | wc -l)"
