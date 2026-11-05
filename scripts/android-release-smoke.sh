#!/usr/bin/env bash
# Installs the R8-minified release APK on the connected device or emulator, launches it and fails on a crash.
#   live  the shipping `release` build: must reach the lock screen (device with a screen lock) or the first-run screen
#   demo  the `releaseDemo` build (same R8 rules and code, plus the demo entry): must reach the demo Inbox
# A shipping release ignores demo launch extras on purpose, so the demo check uses the one build type that offers them
# (docs/architectural-decision-record.md, ADR-132). Needs `adb` and one running device; the build is run here.
#   ANDROID_SERIAL=<serial>  pick a device when several are connected
set -euo pipefail
cd "$(dirname "$0")/.."

mode="${1:-live}"
case "$mode" in
  live) task=":androidApp:assembleRelease"; dir="release"; package="com.stainedhead.paperclipmobile"
        launch=(); expect=("Paperclip is locked|Add your first factory|systemui:id/biometric_prompt") ;;
  demo) task=":androidApp:assembleReleaseDemo"; dir="releaseDemo"; package="com.stainedhead.paperclipmobile.demo"
        launch=(--ez demo true); expect=("Inbox") ;;
  *) echo "usage: $0 live|demo" >&2; exit 2 ;;
esac

./gradlew "$task" --console=plain -q
apk=$(ls androidApp/build/outputs/apk/"$dir"/*.apk | head -n 1)
# The manifest that ships is the one inside the APK (after the merge, R8 and resource shrinking), so it is checked
# here too: the same three permissions and no debuggable flag (AndroidManifestRulesTest checks the merged manifest).
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
aapt2=$(ls "$sdk"/build-tools/*/aapt2 | sort | tail -n 1)
perms=$("$aapt2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\(.*\)'.*/\1/p" | grep -v "^$package\." | sort | tr '\n' ' ')
want="android.permission.ACCESS_NETWORK_STATE android.permission.INTERNET android.permission.USE_BIOMETRIC "
if [ "$perms" != "$want" ]; then echo "FAIL: APK permissions are: $perms" >&2; exit 1; fi
if "$aapt2" dump xmltree --file AndroidManifest.xml "$apk" | grep -q "debuggable.*=true"; then
  echo "FAIL: the APK is debuggable" >&2; exit 1
fi
echo "Installing $apk"
adb install -r "$apk" >/dev/null

adb logcat -b all -c
adb shell am force-stop "$package"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell am start -W -n "$package/com.stainedhead.paperclipmobile.android.MainActivity" ${launch[@]+"${launch[@]}"} >/dev/null
sleep 6

if [ -z "$(adb shell pidof "$package")" ]; then
  echo "FAIL: $package is not running after launch" >&2
  adb logcat -b crash -d | tail -n 40 >&2
  exit 1
fi
crashes=$(adb logcat -b crash -d | grep -c "FATAL EXCEPTION" || true)
if [ "$crashes" != "0" ]; then
  echo "FAIL: $crashes crash(es) in logcat" >&2
  adb logcat -b crash -d | tail -n 40 >&2
  exit 1
fi
# R8 stripping shows up as these even when the process survives (a caught-and-logged failure).
if adb logcat -d | grep -E "ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError|SerializationException|MissingMethodException" | grep "$package"; then
  echo "FAIL: class loading problems in logcat" >&2
  exit 1
fi
adb shell uiautomator dump /sdcard/pafm-ui.xml >/dev/null
if ! adb shell cat /sdcard/pafm-ui.xml | grep -Eq "${expect[0]}"; then
  echo "FAIL: expected screen text '${expect[0]}' not found" >&2
  exit 1
fi
adb shell rm -f /sdcard/pafm-ui.xml
# Live mode on a device with a screen lock shows the system prompt on top of the lock screen; close it.
if [ "$mode" = "live" ]; then adb shell input keyevent KEYCODE_BACK; fi
echo "OK: $mode release launched and shows '${expect[0]}'"
