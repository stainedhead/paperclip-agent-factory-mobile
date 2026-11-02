#!/usr/bin/env bash
# Checks the failure modes of scripts/build-kit.sh and scripts/ios-test.sh without running Gradle or Xcode.
#   scripts/test-build-kit.sh        exits 0 when every check passes
set -euo pipefail
cd "$(dirname "$0")/.."

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0
check() { # check <description> <condition-exit-code>
  if [ "$2" -eq 0 ]; then echo "ok   - $1"; else echo "FAIL - $1"; failures=$((failures + 1)); fi
}

dest="$work/Frameworks/PaperclipKit.xcframework"
mkdir -p "$dest"; echo old > "$dest/marker.txt"
good_source="$work/built/PaperclipKit.xcframework"
mkdir -p "$good_source"; echo new > "$good_source/marker.txt"

# 1. Gradle fails: exit 1 with a clear message, the old framework survives.
set +e
out="$(GRADLE_CMD=false KIT_SOURCE="$good_source" KIT_DESTINATION="$dest" scripts/build-kit.sh debug 2>&1)"; code=$?
set -e
check "a failing Gradle build exits 1" "$([ "$code" -eq 1 ]; echo $?)"
check "...with a clear message" "$(echo "$out" | grep -q 'Gradle failed'; echo $?)"
check "...and keeps the old framework" "$([ "$(cat "$dest/marker.txt")" = old ]; echo $?)"

# 2. Gradle succeeds but the output is missing: exit 1, the old framework survives.
set +e
out="$(GRADLE_CMD=true KIT_SOURCE="$work/nothing" KIT_DESTINATION="$dest" scripts/build-kit.sh debug 2>&1)"; code=$?
set -e
check "a missing build output exits 1" "$([ "$code" -eq 1 ]; echo $?)"
check "...with a clear message" "$(echo "$out" | grep -q 'does not exist'; echo $?)"
check "...and keeps the old framework" "$([ "$(cat "$dest/marker.txt")" = old ]; echo $?)"

# 3. Success: the new framework replaces the old one and the marker names the configuration.
GRADLE_CMD=true KIT_SOURCE="$good_source" KIT_DESTINATION="$dest" scripts/build-kit.sh release >/dev/null
check "a good build replaces the framework" "$([ "$(cat "$dest/marker.txt")" = new ]; echo $?)"
check "...and records the configuration" "$([ "$(cat "$work/Frameworks/PaperclipKit.configuration")" = release ]; echo $?)"
check "...with no staging copy left" "$([ ! -e "$dest.new" ]; echo $?)"

# 4. ios-test.sh without a framework: exit 1 and tell the person what to run.
set +e
out="$(KIT_DESTINATION="$work/absent/PaperclipKit.xcframework" scripts/ios-test.sh 2>&1)"; code=$?
set -e
check "ios-test.sh without a framework exits 1" "$([ "$code" -eq 1 ]; echo $?)"
check "...and says to run scripts/build-kit.sh" "$(echo "$out" | grep -q 'scripts/build-kit.sh'; echo $?)"

[ "$failures" -eq 0 ] && echo "all build-kit checks passed" || { echo "$failures check(s) failed"; exit 1; }
