#!/usr/bin/env bash
# Runs the Macrobenchmarks (cold start to the Inbox, median of 10; frame timing over a 200 card Inbox fling) on the
# connected device or emulator and prints the medians. On demand only: `./gradlew check` never runs this (ADR-135).
#   ANDROID_SERIAL=<serial>  pick a device when several are connected
#   PAFM_BENCHMARK_ALLOW_EMULATOR=1  record on an emulator (the default allows it: this is the regression guard in CI;
#                                     the PRD's Pixel 6 target needs a real Pixel 6, run this with it connected and
#                                     PAFM_BENCHMARK_ALLOW_EMULATOR=0)
# Needs `adb`, one running unlocked device, and the baseline profile (scripts/generate-baseline-profile.sh) committed.
set -euo pipefail
cd "$(dirname "$0")/.."

suppress=()
if [ "${PAFM_BENCHMARK_ALLOW_EMULATOR:-1}" = "1" ]; then
  # Macrobenchmark refuses emulators because their timings are not hardware timings. This module is the only place the
  # refusal is lifted, by an instrumentation argument, and the numbers are labelled as emulator numbers (ADR-135).
  suppress=(-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR)
fi

./gradlew :androidApp:benchmark:connectedBenchmarkAndroidTest --console=plain ${suppress[@]+"${suppress[@]}"}

python3 -I - <<'PY'
import glob, json, statistics
files = glob.glob("androidApp/benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/*/*benchmarkData.json")
if not files:
    raise SystemExit("FAIL: no benchmarkData.json was written")
for path in files:
    data = json.load(open(path))
    print("device:", data["context"]["build"].get("model"), "api", data["context"]["build"].get("version", {}).get("sdk"),
          "| emulator" if "emulator" in data["context"]["build"].get("fingerprint", "").lower() or "sdk_gphone" in data["context"]["build"].get("fingerprint", "") else "")
    for b in data["benchmarks"]:
        print(b["name"])
        for metric, m in b["metrics"].items():
            print(f"  {metric}: median {m['median']:.1f}  min {m['minimum']:.1f}  max {m['maximum']:.1f}  runs {len(m['runs'])}")
        for metric, m in b.get("sampledMetrics", {}).items():
            print(f"  {metric}: P50 {m['P50']:.1f}  P90 {m['P90']:.1f}  P95 {m['P95']:.1f}  P99 {m['P99']:.1f}")
PY
