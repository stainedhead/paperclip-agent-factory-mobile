#!/usr/bin/env bash
# Proves that konsistTest cannot pass stale locally (R-014): the rules read the version catalog, the app's build files,
# its R8 rules, its merged manifests and the documents whose claims the rules check (README, docs, the archived Android
# spec status), so changing any of them must make the task run again.
#   scripts/test-konsist-inputs.sh [catalog|proguard|app-build|docs|archive-status]    default: all five
# Each case runs the task twice to reach "up to date", changes ONLY that one file (a trailing comment, restored on exit),
# runs it a third time and fails when Gradle skipped the task. Uses the local Gradle build; takes a few minutes.
set -euo pipefail
cd "$(dirname "$0")/.."

task=":shared:umbrella:konsistTest"
changed=""
backup="$(mktemp)"
restore() {
  if [ -n "$changed" ]; then cp "$backup" "$changed"; changed=""; fi
}
trap 'restore; rm -f "$backup"' EXIT

runs_again() {
  # Prints the task line Gradle reports: "> Task ... UP-TO-DATE", "FROM-CACHE", or plain (it ran).
  ./gradlew "$task" --console=plain 2>&1 | grep -E "^> Task ${task}( |$)" || true
}

check_case() {
  local name="$1" file="$2" comment="$3"
  echo "== $name: $file"
  ./gradlew "$task" --console=plain -q >/dev/null
  local settled
  settled=$(runs_again)
  case "$settled" in
    *UP-TO-DATE*) ;;
    *) echo "error: $name: the task did not settle to UP-TO-DATE before the change: $settled" >&2; exit 2 ;;
  esac
  cp "$file" "$backup"
  changed="$file"
  printf '\n%s touched by scripts/test-konsist-inputs.sh\n' "$comment" >>"$file"
  local after
  after=$(runs_again)
  restore
  case "$after" in
    *UP-TO-DATE*|*FROM-CACHE*|*NO-SOURCE*) echo "error: $name: changing only $file did not re-run konsistTest ($after)" >&2; exit 1 ;;
    *) echo "ok: $name re-ran konsistTest" ;;
  esac
}

cases="${1:-catalog proguard app-build docs archive-status}"
for c in $cases; do
  case "$c" in
    catalog) check_case catalog gradle/libs.versions.toml "#" ;;
    proguard) check_case proguard androidApp/proguard-rules.pro "#" ;;
    app-build) check_case app-build androidApp/build.gradle.kts "//" ;;
    docs) check_case docs docs/technical-details.md "-" ;;
    archive-status) check_case archive-status specs/archive/261104-phase-2a-android-ui/status.md "-" ;;
    *) echo "usage: $0 [catalog|proguard|app-build|docs|archive-status]" >&2; exit 64 ;;
  esac
done
# Leave the build settled again after the restore.
./gradlew "$task" --console=plain -q >/dev/null
