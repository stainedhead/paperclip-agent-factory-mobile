#!/usr/bin/env bash
# Generates the Xcode project and runs the iOS unit and UI tests on an installed iPhone simulator.
# Needs PaperclipKit.xcframework first: scripts/build-kit.sh
#   IOS_SIMULATOR_ID=<udid>  pick a specific simulator (default: first available iPhone on the newest iOS runtime)
set -euo pipefail
cd "$(dirname "$0")/.."

framework="${KIT_DESTINATION:-iosApp/Frameworks/PaperclipKit.xcframework}"
if [ ! -d "$framework" ]; then
  echo "error: $framework is missing; run scripts/build-kit.sh first." >&2
  exit 1
fi
echo "Using PaperclipKit.xcframework ($(cat "${framework%.xcframework}.configuration" 2>/dev/null || echo 'unknown configuration'))"

(cd iosApp && xcodegen generate --quiet)

device_id="${IOS_SIMULATOR_ID:-$(xcrun simctl list devices available -j | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
for runtime in sorted(devices, reverse=True):
    if "iOS" not in runtime:
        continue
    for device in devices[runtime]:
        if device.get("isAvailable") and "iPhone" in device["name"]:
            print(device["udid"])
            sys.exit(0)
sys.exit("no available iPhone simulator found")
')}"

echo "Testing on simulator $device_id"
xcodebuild test \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination "id=${device_id}" \
  CODE_SIGNING_ALLOWED=NO
