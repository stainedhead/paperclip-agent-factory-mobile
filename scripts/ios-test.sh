#!/usr/bin/env bash
# Generates the Xcode project and runs the iOS unit and UI tests on an installed iPhone simulator.
# Needs PaperclipKit.xcframework first: scripts/build-kit.sh
#   IOS_SIMULATOR_ID=<udid>  pick a specific simulator (default: first available iPhone on the newest iOS runtime)
#   TEST_SET=all|functional|audits  which tests run (default all). CI runs `functional` as the blocking step and
#                            `audits` as a separate, non-blocking one (docs/architectural-decision-record.md, ADR-080).
#   SHARD=1|2|3             with TEST_SET=functional, run only that third of the functional tests (CI runs the three in
#                            parallel jobs to stay well under the job timeout, ADR-104). Unset runs them all.
#   RESULT_BUNDLE=<path>     also write the .xcresult (failure screenshots and element hierarchies) there
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

# UI tests type through the on-screen keyboard. A connected hardware keyboard hides it and makes focus and caret
# handling vary between machines and CI, so turn it off before the simulator boots, and keep autocorrection and
# predictions from rewriting what the tests type.
defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool false
xcrun simctl bootstatus "$device_id" -b >/dev/null
xcrun simctl spawn "$device_id" defaults write com.apple.Preferences KeyboardAutocorrection -bool false || true
xcrun simctl spawn "$device_id" defaults write com.apple.Preferences KeyboardPrediction -bool false || true
xcrun simctl spawn "$device_id" defaults write com.apple.Preferences KeyboardAutocapitalization -bool false || true

echo "Testing on simulator $device_id"
result_bundle_args=()
if [ -n "${RESULT_BUNDLE:-}" ]; then
  rm -rf "$RESULT_BUNDLE"
  result_bundle_args=(-resultBundlePath "$RESULT_BUNDLE")
fi
derived="build/ios-derived"
xcodebuild build-for-testing \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination "id=${device_id}" \
  -derivedDataPath "$derived" \
  CODE_SIGNING_ALLOWED=NO

# Warm-up: a freshly booted simulator makes the first launch of the app very slow (first-run caches, keychain, font and
# accessibility services), which the first UI tests of a run used to absorb as 15 to 30 s waits. Install the app, launch
# it once on the demo graph, let it reach the shell and terminate it, so every test starts against a warm simulator.
app_path=$(find "$derived/Build/Products" -maxdepth 2 -name 'iosApp.app' -type d | head -1)
bundle_id="com.stainedhead.paperclipmobile"
if [ -n "$app_path" ]; then
  xcrun simctl install "$device_id" "$app_path" || true
  xcrun simctl launch "$device_id" "$bundle_id" -demo || true
  sleep "${WARMUP_SECONDS:-15}"
  xcrun simctl terminate "$device_id" "$bundle_id" || true
fi

audit_tests=(
  iosAppUITests/EditFactoryUITests/testAccessibilityAuditOfTheEditForm
  iosAppUITests/FactoriesUITests/testAccessibilityAuditOfTheFactoriesTab
  iosAppUITests/FirstRunUITests/testAccessibilityAuditOfTheAddressStep
  iosAppUITests/ManageFactoriesUITests/testAccessibilityAuditOfManageFactories
  iosAppUITests/SettingsUITests/testAccessibilityAuditOfSettings
  iosAppUITests/ShellUITests/testAccessibilityAuditBaselineOfTheShell
  iosAppUITests/OperationsAuditUITests
  iosAppUITests/InboxUITests/testAccessibilityAuditOfTheInbox
  iosAppUITests/InboxUITests/testAccessibilityAuditOfTheOfflineInbox
  iosAppUITests/ApprovalUITests/testAccessibilityAuditOfTheApprovalScreen
  iosAppUITests/ApprovalUITests/testAccessibilityAuditOfTheOfflineApprovalScreen
  iosAppUITests/BudgetUITests/testAccessibilityAuditOfTheBudgetSheet
  iosAppUITests/BudgetUITests/testAccessibilityAuditOfTheOfflineBudgetScreen
  iosAppUITests/DecisionUITests/testAccessibilityAuditOfTheDecisionScreen
  iosAppUITests/DecisionUITests/testAccessibilityAuditOfTheOfflineDecisionScreen
)
# The functional UI test classes by shard. Every UI test class must be listed in a shard or be audit-only, so a new
# class cannot silently run nowhere.
shard_targets() {
  case "$1" in
    1) echo "iosAppTests iosAppUITests/FirstRunUITests iosAppUITests/EditFactoryUITests iosAppUITests/FactoriesUITests iosAppUITests/ManageFactoriesUITests iosAppUITests/SettingsUITests iosAppUITests/ShellUITests" ;;
    2) echo "iosAppUITests/InboxUITests iosAppUITests/DecisionUITests" ;;
    3) echo "iosAppUITests/ApprovalUITests iosAppUITests/BudgetUITests iosAppUITests/FactoryHomeUITests iosAppUITests/AgentsUITests iosAppUITests/AgentDetailUITests iosAppUITests/RunViewerUITests" ;;
    *) echo "error: SHARD must be 1, 2 or 3" >&2; exit 1 ;;
  esac
}
audit_only_classes="OperationsAuditUITests"
for class in $(grep -rhoE 'final class [A-Za-z]+UITests' iosApp/Tests/iosAppUITests | awk '{print $3}'); do
  if ! { for n in 1 2 3; do shard_targets "$n"; done; echo "$audit_only_classes"; } | grep -qw "$class"; then
    echo "error: $class is in no shard of scripts/ios-test.sh" >&2
    exit 1
  fi
done

filter_args=()
case "${TEST_SET:-all}" in
  audits) for t in "${audit_tests[@]}"; do filter_args+=("-only-testing:$t"); done ;;
  functional)
    if [ -n "${SHARD:-}" ]; then
      for target in $(shard_targets "$SHARD"); do filter_args+=("-only-testing:$target"); done
    fi
    for t in "${audit_tests[@]}"; do filter_args+=("-skip-testing:$t"); done ;;
  all) ;;
  *) echo "error: TEST_SET must be all, functional or audits" >&2; exit 1 ;;
esac

xcodebuild test-without-building \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination "id=${device_id}" \
  -derivedDataPath "$derived" \
  ${filter_args[@]+"${filter_args[@]}"} \
  ${result_bundle_args[@]+"${result_bundle_args[@]}"} \
  CODE_SIGNING_ALLOWED=NO
