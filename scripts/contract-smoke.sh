#!/usr/bin/env bash
# Checks the parts of the Paperclip API the app relies on against a RUNNING server (start it with
# scripts/paperclip-test-server.sh up). It fails with one line per broken expectation, so a changed server version
# (the nightly job runs this against `latest`) shows up as a red check, not as a surprise in Phase 1b.
#
#   scripts/contract-smoke.sh [base-url]        default http://127.0.0.1:${PAPERCLIP_TEST_PORT:-3199}
#
# It reads only what an anonymous caller can read: GET /api/health, and the pairing challenge create and poll
# (POST /api/cli-auth/challenges, GET /api/cli-auth/challenges/{id}). It never prints a token.
set -euo pipefail

base="${1:-http://127.0.0.1:${PAPERCLIP_TEST_PORT:-3199}}"
export PAFM_BASE="${base%/}"

exec python3 -I - <<'PY'
import json
import os
import sys
import urllib.error
import urllib.request

base = os.environ["PAFM_BASE"]
problems = []


def call(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(
        base + path, data=data, method=method, headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, None
    except (urllib.error.URLError, ValueError) as error:
        problems.append(f"{method} {path} failed: {error}")
        return None, None


def expect(condition, message):
    if not condition:
        problems.append(message)


status, health = call("GET", "/api/health")
expect(status == 200, f"GET /api/health answered {status}, expected 200")
if health is not None:
    expect(health.get("status") in ("ok", "starting", "unhealthy"), f"health.status is {health.get('status')!r}")
    expect(health.get("status") == "ok", f"health.status is {health.get('status')!r}, the server is not ready")
    expect(
        health.get("deploymentMode") in ("local_trusted", "authenticated"),
        f"health.deploymentMode is {health.get('deploymentMode')!r}, the app knows local_trusted and authenticated",
    )
    expect(
        health.get("deploymentMode") == "authenticated",
        f"health.deploymentMode is {health.get('deploymentMode')!r}; the app refuses anything but authenticated",
    )
    expect(
        health.get("deploymentExposure") in ("private", "public"),
        f"health.deploymentExposure is {health.get('deploymentExposure')!r}, expected private or public",
    )
    expect(
        health.get("bootstrapStatus") in ("ready", "bootstrap_pending"),
        f"health.bootstrapStatus is {health.get('bootstrapStatus')!r}, expected ready or bootstrap_pending",
    )
    expect(
        isinstance(health.get("bootstrapInviteActive"), bool),
        "health.bootstrapInviteActive is missing or not a boolean",
    )

status, challenge = call(
    "POST", "/api/cli-auth/challenges", {"command": "pafm contract smoke", "clientName": "pafm-contract-smoke"}
)
expect(status == 201, f"POST /api/cli-auth/challenges answered {status}, expected 201")
if challenge is not None:
    for field in ("id", "token", "boardApiToken", "approvalPath", "pollPath", "expiresAt"):
        expect(isinstance(challenge.get(field), str) and challenge[field], f"challenge.{field} is missing or empty")
    expect(
        isinstance(challenge.get("suggestedPollIntervalMs"), int) and challenge["suggestedPollIntervalMs"] > 0,
        "challenge.suggestedPollIntervalMs is missing or not a positive integer",
    )
    expect("approvalUrl" in challenge, "challenge.approvalUrl is missing (it may be null, the key must exist)")
    if isinstance(challenge.get("id"), str) and isinstance(challenge.get("token"), str):
        status, polled = call(
            "GET", f"/api/cli-auth/challenges/{challenge['id']}?token={challenge['token']}"
        )
        expect(status == 200, f"polling a new challenge answered {status}, expected 200")
        if polled is not None:
            expect(polled.get("status") == "pending", f"a new challenge polls as {polled.get('status')!r}, not pending")
            expect(polled.get("id") == challenge["id"], "the polled challenge has another id")
            for secret_field in ("token", "boardApiToken"):
                expect(secret_field not in polled, f"the poll response now carries {secret_field}")
        status, _ = call("GET", f"/api/cli-auth/challenges/{challenge['id']}?token=wrong-token")
        expect(status == 404, f"polling with a wrong token answered {status}, expected 404")

if problems:
    print("Paperclip contract smoke FAILED:", file=sys.stderr)
    for problem in problems:
        print(f"  - {problem}", file=sys.stderr)
    sys.exit(1)
print(f"Paperclip contract smoke passed against {base}")
PY
