#!/usr/bin/env bash
# Prefer:  ./scripts/use-java.sh ./gradlew build
# Or:      source ./scripts/use-java.sh && ./gradlew build
#
# Running this file alone does nothing to your current shell.

set_java_home() {
  local path
  local -a candidates=()

  shopt -s nullglob 2>/dev/null || true
  candidates+=(
    "$HOME"/.local/jdk/jdk-25*/
    "$HOME"/.local/jdk/jdk-21*/
    /usr/lib/jvm/temurin-25-jdk-amd64
    /usr/lib/jvm/temurin-21-jdk-amd64
  )
  shopt -u nullglob 2>/dev/null || true

  for path in "${candidates[@]}"; do
    path="${path%/}"
    if [[ -x "$path/bin/java" ]]; then
      export JAVA_HOME="$path"
      export PATH="$JAVA_HOME/bin:/usr/bin:/bin:${PATH:-}"
      echo "Using JAVA_HOME=$JAVA_HOME"
      "$JAVA_HOME/bin/java" -version
      return 0
    fi
  done

  echo "No JDK 21+ found under ~/.local/jdk or /usr/lib/jvm." >&2
  return 1
}

# Sourced into the current shell
if [[ "${BASH_SOURCE[0]:-}" != "${0:-}" ]]; then
  set_java_home
  return $?
fi

# Executed as a program
set_java_home || exit 1
if [[ $# -eq 0 ]]; then
  echo
  echo "Java was set only for this process, not your shell."
  echo "Use one of:"
  echo "  ./scripts/use-java.sh ./gradlew build"
  echo "  source ./scripts/use-java.sh && ./gradlew build"
  exit 0
fi
exec "$@"
