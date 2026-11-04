#!/usr/bin/env bash
# Device-level checks of the live (non-demo) Android app that need no Paperclip server (T8.9, acceptance criteria 4 to 6):
#   1. a launch without the demo flag opens the real graph (first run or the lock screen), never the demo Inbox
#   2. the app's files (run-as, debug build), shared preferences and the log hold no board key (prefix `pcp_board_`)
#   3. backup is refused: the manifest says allowBackup=false and `bmgr` / `adb backup` export nothing
#   4. the release manifest has no network security config and no cleartext, so a stored http address cannot be reached
# The pairing proof needs the pinned server (scripts/android-live-pairing.sh); the Keystore record and stored http
# address are proven by the umbrella device tests (`SecretsAbsentDeviceTest`, `AndroidLiveGraphDeviceTest`).
# Needs `adb` and one running, unlocked device. ANDROID_SERIAL=<serial> picks one when several are connected.
set -euo pipefail
cd "$(dirname "$0")/.."

package="com.stainedhead.paperclipmobile"
activity="$package/com.stainedhead.paperclipmobile.android.MainActivity"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
aapt2=$(ls "$sdk"/build-tools/*/aapt2 | sort | tail -n 1)
fail() { echo "FAIL: $*" >&2; exit 1; }

./gradlew :androidApp:assembleDebug :androidApp:assembleRelease --console=plain -q
debug_apk=$(ls androidApp/build/outputs/apk/debug/*.apk | head -n 1)
release_apk=$(ls androidApp/build/outputs/apk/release/*.apk | head -n 1)

adb install -r "$debug_apk" >/dev/null
adb shell pm clear "$package" >/dev/null
adb logcat -b all -c
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell am start -W -n "$activity" >/dev/null
sleep 6

# 1. The real graph, not the demo.
adb shell uiautomator dump /sdcard/pafm-ui.xml >/dev/null
ui=$(adb shell cat /sdcard/pafm-ui.xml)
adb shell rm -f /sdcard/pafm-ui.xml
echo "$ui" | grep -Eq "Add your first factory|Paperclip is locked|systemui:id/biometric_prompt" \
  || fail "the live launch shows neither the first-run screen nor the lock screen"
if echo "$ui" | grep -q "Ship the billing migration"; then fail "the live launch shows demo data"; fi
echo "OK 1: a launch without the demo flag opens the real graph"

# (The patterns are written pcp_boar[d]_ so that the adb shell command line, which adbd logs, does not match itself.)
# 2. No board key anywhere the app can write, and none in the log. (A fresh install holds no key; with a paired factory on
#    this device the same scan checks the real record: the key may be in the Keystore-wrapped record only, as ciphertext.)
files=$(adb shell run-as "$package" sh -c 'find . -type f 2>/dev/null' | tr -d '\r')
[ -n "$files" ] || fail "run-as listed no files: the scan would prove nothing"
hits=$(adb shell run-as "$package" sh -c 'grep -rl "pcp_boar[d]_" . 2>/dev/null' | tr -d '\r' || true)
[ -z "$hits" ] || fail "a board key prefix is in: $hits"
log_hits=$(adb logcat -d | grep -c "pcp_boar[d]_" || true)
[ "$log_hits" = "0" ] || fail "$log_hits logcat lines hold a board key prefix"
echo "OK 2: no board key in $(echo "$files" | wc -l | tr -d ' ') app files (shared_prefs, databases, no_backup, cache) or logcat"

# 3. Backup is refused.
debug_manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$debug_apk")
echo "$debug_manifest" | grep -q "allowBackup.*=false" || fail "allowBackup is not false"
echo "bmgr backupnow: $(adb shell bmgr backupnow "$package" 2>&1 | tr -d '\r' | tr '\n' ' ' | cut -c1-300)"
ab=$(mktemp -d)/pafm.ab
# adb backup waits for a confirmation on the device; give it a few seconds, then stop it. It must export no app data.
(perl -e 'alarm 15; exec @ARGV' adb backup -f "$ab" -noapk "$package" >/dev/null 2>&1 || true) 2>/dev/null
size=$(wc -c < "$ab" 2>/dev/null || echo 0)
if [ "$size" -gt 4096 ]; then
  if LC_ALL=C grep -aq "pcp_boar[d]_" "$ab"; then fail "adb backup exported a board key"; fi
fi
echo "OK 3: allowBackup=false; bmgr refused; adb backup exported $size bytes (it waits for a confirmation on the device)"

# 4. Release cannot reach cleartext at all.
manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$release_apk")
if echo "$manifest" | grep -q "networkSecurityConfig"; then fail "release names a network security config"; fi
echo "$manifest" | grep -q "usesCleartextTraffic.*=false" || fail "release does not set usesCleartextTraffic=false"
echo "OK 4: the release manifest allows no cleartext traffic"
