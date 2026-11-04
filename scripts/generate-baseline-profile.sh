#!/usr/bin/env bash
# Regenerates the app's baseline profile (androidApp/src/main/baseline-prof.txt) on the connected device or emulator.
# It runs BaselineProfileGenerator against the `nonMinified` build type (real class names; R8 renames the others), then
# copies the recorded profile into the app's main source set, where AGP packages it into every non-debuggable build.
# Takes about six minutes. Needs `adb`, one running device on API 33 or newer (or a rooted older one) and an unlocked
# screen. Commit the result when it changes meaningfully; the profile is rules about the app's own code, not data.
#   ANDROID_SERIAL=<serial>  pick a device when several are connected
set -euo pipefail
cd "$(dirname "$0")/.."

# Emulators are refused by Macrobenchmark because their timings are not representative. A profile records which code runs,
# not how fast, so it is safe to record on one; the flag is passed to this one on-demand module only (ADR-135).
./gradlew :androidApp:benchmark:connectedNonMinifiedAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  -Pandroid.testInstrumentationRunnerArguments.class=com.stainedhead.paperclipmobile.android.benchmark.BaselineProfileGenerator

out="androidApp/benchmark/build/outputs/connected_android_test_additional_output/nonMinified/connected"
profile=$(find "$out" -name 'BaselineProfileGenerator_generate-baseline-prof.txt' -o -name 'BaselineProfileGenerator_generate-startup-prof.txt' | sort | head -n 1)
if [ -z "$profile" ]; then echo "FAIL: no profile was recorded under $out" >&2; exit 1; fi
cp "$profile" androidApp/src/main/baseline-prof.txt
echo "Wrote androidApp/src/main/baseline-prof.txt ($(wc -l < androidApp/src/main/baseline-prof.txt) rules) from $(basename "$profile")"
