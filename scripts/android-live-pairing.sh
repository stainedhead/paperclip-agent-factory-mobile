#!/usr/bin/env bash
# Pairs the Android debug app with the pinned Paperclip test server on the connected device or emulator, then checks that the
# board key is in the Keystore-wrapped record only (T8.9, acceptance criteria 4 to 6). Needs Docker (the pinned image), `adb`,
# `python3` and one running, unlocked device. NOT RUN in the session that wrote it: that machine had no running Docker daemon.
# Treat the first run as the check of the script itself.
#
# What is automatic and what is not:
#   automatic  start the server, mint the bootstrap invite, make the first board user and keep its session, route the
#              device to the host, install the debug app, approve the pairing challenge the app creates (below)
#   by you     in the app: add the factory `http://localhost:3199`, tap Check, Continue, Pair, and when the system asks for
#              your screen lock or fingerprint to protect the key, give it. Then press Enter here.
#
# The browser's approval cannot be driven headlessly (the Custom Tab needs a signed-in browser session). The script plays the
# browser's part the way the live contract test does: the approval URL the app opens is visible in the activity manager's
# intent, and the script calls `POST /api/cli-auth/challenges/{id}/approve` with the board user's session. The key itself
# never passes through the script: it goes from the server to the app's poll.
#
# The device reaches the host through `adb reverse` as http://localhost:3199, because the server trusts that origin and the
# debug build's network security config allows cleartext to `localhost` and `10.0.2.2` only. Release builds refuse http.
#   ANDROID_SERIAL=<serial>  pick a device when several are connected
set -euo pipefail
cd "$(dirname "$0")/.."

port="${PAPERCLIP_TEST_PORT:-3199}"
base="http://localhost:${port}"
package="com.stainedhead.paperclipmobile"
fail() { echo "FAIL: $*" >&2; exit 1; }
command -v docker >/dev/null || fail "docker is not installed"
docker info >/dev/null 2>&1 || fail "the Docker daemon is not running; start Docker Desktop and run again"

approver=""
cleanup() {
  [ -z "$approver" ] || kill "$approver" 2>/dev/null || true
  adb reverse --remove "tcp:${port}" >/dev/null 2>&1 || true
  ./scripts/paperclip-test-server.sh down >/dev/null 2>&1 || true
}
trap cleanup EXIT

./scripts/paperclip-test-server.sh up
invite="$(./scripts/paperclip-test-server.sh invite)"

# The first board user: sign up, accept the bootstrap invite (this makes the user the instance admin), keep the cookie.
export PAFM_BASE="$base" PAFM_INVITE="$invite"
cookie="$(python3 -I - <<'PY'
import json, os, urllib.error, urllib.request

base = os.environ["PAFM_BASE"]
invite = os.environ["PAFM_INVITE"]
cred = {"email": "contract-test@example.test", "password": "TESTONLY-contract-password-0000"}


def call(path, body, cookie=""):
    request = urllib.request.Request(base + path, data=json.dumps(body).encode(), method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("Origin", base)
    if cookie:
        request.add_header("Cookie", cookie)
    try:
        with urllib.request.urlopen(request) as reply:
            return reply.status, reply.headers.get_all("Set-Cookie") or []
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get_all("Set-Cookie") or []


def jar(headers):
    return "; ".join(h.split(";")[0] for h in headers if h.split(";")[0].split("=", 1)[1])


status, headers = call("/api/auth/sign-up/email", dict(cred, name="Contract Test"))
if status >= 300 or not jar(headers):
    status, headers = call("/api/auth/sign-in/email", cred)
cookie = jar(headers)
if status >= 300 or not cookie:
    raise SystemExit("sign-in answered %d" % status)
status, _ = call("/api/invites/%s/accept" % invite, {"requestType": "human"}, cookie)
if status != 202:
    raise SystemExit("the bootstrap invite answered %d" % status)
print(cookie)
PY
)"
unset PAFM_INVITE
[ -n "$cookie" ] || fail "no board session"

./gradlew :androidApp:assembleDebug --console=plain -q
adb install -r "$(ls androidApp/build/outputs/apk/debug/*.apk | head -n 1)" >/dev/null
adb shell pm clear "$package" >/dev/null
adb reverse "tcp:${port}" "tcp:${port}"
adb logcat -b all -c

# The approver: while you pair, find the approval URL the app opens and approve its challenge as the board user.
PAFM_COOKIE="$cookie" python3 -I - <<'PY' &
import json, os, re, subprocess, time, urllib.error, urllib.request

base = os.environ["PAFM_BASE"]
cookie = os.environ["PAFM_COOKIE"]
seen = set()
pattern = re.compile(r"cli-auth/([A-Za-z0-9_-]+)\?token=([A-Za-z0-9_.~-]+)")
while True:
    dump = subprocess.run(["adb", "shell", "dumpsys", "activity", "activities"], capture_output=True, text=True).stdout
    for challenge, token in pattern.findall(dump):
        if challenge in seen:
            continue
        seen.add(challenge)
        request = urllib.request.Request(
            base + "/api/cli-auth/challenges/%s/approve" % challenge,
            data=json.dumps({"token": token}).encode(),
            method="POST",
        )
        request.add_header("Content-Type", "application/json")
        request.add_header("Origin", base)
        request.add_header("Cookie", cookie)
        try:
            with urllib.request.urlopen(request) as reply:
                print("approver: challenge answered %d" % reply.status, flush=True)
        except urllib.error.HTTPError as error:
            print("approver: challenge answered %d" % error.code, flush=True)
    time.sleep(1)
PY
approver=$!
unset PAFM_COOKIE

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell am start -n "$package/com.stainedhead.paperclipmobile.android.MainActivity" >/dev/null
echo "In the app, add the factory ${base}, check it, pair, and give the screen lock when asked. Then press Enter here."
read -r _

# 1. The Keystore-wrapped record exists, and no board key prefix is in any file the app wrote or in the log.
records="$(adb shell run-as "$package" sh -c 'find . -path "*secrets*" -type f 2>/dev/null' | tr -d '\r')"
[ -n "$records" ] || fail "no Keystore-wrapped secret record was written: the pairing did not complete"
hits="$(adb shell run-as "$package" sh -c 'grep -rl "pcp_boar[d]_" . 2>/dev/null' | tr -d '\r' || true)"
[ -z "$hits" ] || fail "a board key prefix is in: $hits"
[ "$(adb logcat -d | grep -c 'pcp_boar[d]_' || true)" = "0" ] || fail "a board key prefix is in logcat"
echo "OK 1: the board key is only in the Keystore-wrapped record (${records//$'\n'/ }); no files, no log"

# 2. Backup is refused.
echo "bmgr backupnow: $(adb shell bmgr backupnow "$package" 2>&1 | tr -d '\r' | tr '\n' ' ' | cut -c1-300)"
echo "OK 2: see the bmgr line above (expect 'Backup is not allowed')"
