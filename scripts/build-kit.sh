#!/usr/bin/env bash
# Builds PaperclipKit.xcframework from the shared Kotlin modules and places it where the iOS app's Swift package
# expects it (iosApp/Frameworks, git-ignored).
#   scripts/build-kit.sh [debug|release]     default: debug
set -euo pipefail
cd "$(dirname "$0")/.."

config="${1:-debug}"
case "$config" in
  debug) task="assemblePaperclipKitDebugXCFramework" ;;
  release) task="assemblePaperclipKitReleaseXCFramework" ;;
  *) echo "usage: $0 [debug|release]" >&2; exit 64 ;;
esac

./gradlew ":shared:umbrella:${task}"

source="shared/umbrella/build/XCFrameworks/${config}/PaperclipKit.xcframework"
destination="iosApp/Frameworks/PaperclipKit.xcframework"
rm -rf "$destination"
mkdir -p iosApp/Frameworks
cp -R "$source" "$destination"
echo "PaperclipKit.xcframework ($config) -> $destination"
