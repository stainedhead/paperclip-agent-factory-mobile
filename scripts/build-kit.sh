#!/usr/bin/env bash
# Builds PaperclipKit.xcframework from the shared Kotlin modules and places it where the iOS app's Swift package
# expects it (iosApp/Frameworks, git-ignored).
#   scripts/build-kit.sh [debug|release]     default: debug
# The existing framework is replaced only after the new one built and was copied completely; a failed build or a missing
# output leaves the old one untouched. A marker file next to it records which configuration it is.
# For tests (scripts/test-build-kit.sh): GRADLE_CMD, KIT_SOURCE and KIT_DESTINATION override the defaults.
set -euo pipefail
cd "$(dirname "$0")/.."

config="${1:-debug}"
case "$config" in
  debug) task="assemblePaperclipKitDebugXCFramework" ;;
  release) task="assemblePaperclipKitReleaseXCFramework" ;;
  *) echo "usage: $0 [debug|release]" >&2; exit 64 ;;
esac

gradle_cmd="${GRADLE_CMD:-./gradlew}"
source="${KIT_SOURCE:-shared/umbrella/build/XCFrameworks/${config}/PaperclipKit.xcframework}"
destination="${KIT_DESTINATION:-iosApp/Frameworks/PaperclipKit.xcframework}"
marker="${destination%.xcframework}.configuration"

if ! $gradle_cmd ":shared:umbrella:${task}"; then
  echo "error: Gradle failed to build ${task}; the existing framework (if any) was left in place." >&2
  exit 1
fi

if [ ! -d "$source" ]; then
  echo "error: Gradle succeeded but ${source} does not exist; the existing framework (if any) was left in place." >&2
  exit 1
fi

staging="${destination}.new"
rm -rf "$staging"
mkdir -p "$(dirname "$destination")"
if ! cp -R "$source" "$staging"; then
  rm -rf "$staging"
  echo "error: copying ${source} failed; the existing framework (if any) was left in place." >&2
  exit 1
fi
rm -rf "$destination"
mv "$staging" "$destination"
echo "$config" > "$marker"
echo "PaperclipKit.xcframework ($config) -> $destination"
