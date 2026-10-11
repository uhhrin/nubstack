#!/usr/bin/env bash
# Fails if tracked (or provided) text files contain forbidden characters or terms.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

EM_DASH=$'\u2014'
TERMS_FILE="$ROOT/scripts/writing-policy/forbidden-terms.txt"

mapfile -t FORBIDDEN_TERMS < <(grep -v '^[[:space:]]*$' "$TERMS_FILE" | grep -v '^#')

is_excluded() {
  local path="$1"
  case "$path" in
    ./.git/*|.git/*) return 0 ;;
    ./.gradle/*|.gradle/*) return 0 ;;
    */build/*|./build/*) return 0 ;;
    ./gradle/wrapper/*|gradle/wrapper/*) return 0 ;;
    ./.agents/*|.agents/*) return 0 ;;
    ./.cursor/*|.cursor/*) return 0 ;;
    */node_modules/*|./node_modules/*) return 0 ;;
    ./skills-lock.json|skills-lock.json) return 0 ;;
    ./scripts/check-writing-policy.sh|scripts/check-writing-policy.sh) return 0 ;;
    ./scripts/writing-policy/*|scripts/writing-policy/*) return 0 ;;
    ./*.local.md|*.local.md|./docs/*.local.md|docs/*.local.md) return 0 ;;
  esac
  return 1
}

collect_files() {
  if [[ $# -gt 0 ]]; then
    printf '%s\n' "$@"
    return
  fi
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git ls-files -z | while IFS= read -r -d '' f; do
      printf '%s\n' "$f"
    done
  else
    find . -type f \
      ! -path './.git/*' \
      ! -path './.gradle/*' \
      ! -path '*/build/*' \
      ! -path './.agents/*' \
      ! -path './.cursor/*' \
      ! -path '*/node_modules/*' \
      ! -path './scripts/writing-policy/*' \
      ! -name 'skills-lock.json' \
      ! -name '*.local.md'
  fi
}

is_text_candidate() {
  local path="$1"
  local base
  base="$(basename "$path")"
  case "$base" in
    LICENSE|Dockerfile|.gitignore|.gitattributes|.editorconfig) return 0 ;;
  esac
  [[ "$path" =~ \.(md|txt|yml|yaml|json|xml|properties|gradle|kts|java|kt|js|mjs|cjs|ts|tsx|sh|bash|csv|toml|html|css|svg|mdc)$ ]]
}

failures=0

while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  [[ -f "$file" ]] || continue
  is_excluded "./$file" && continue
  is_excluded "$file" && continue
  is_text_candidate "$file" || continue

  if grep -Fq "$EM_DASH" "$file" 2>/dev/null; then
    echo "em-dash: $file"
    failures=$((failures + 1))
  fi

  # .gitignore must name local tooling files; skip term scan there.
  base="$(basename "$file")"
  if [[ "$base" == ".gitignore" ]]; then
    continue
  fi

  for term in "${FORBIDDEN_TERMS[@]}"; do
    if grep -Fq "$term" "$file" 2>/dev/null; then
      echo "forbidden-term (${term}): $file"
      failures=$((failures + 1))
    fi
  done
done < <(collect_files "$@")

if [[ "$failures" -gt 0 ]]; then
  echo "writing-policy: ${failures} violation(s)"
  exit 1
fi

echo "writing-policy: ok"
