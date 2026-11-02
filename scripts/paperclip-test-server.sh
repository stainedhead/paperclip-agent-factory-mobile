#!/usr/bin/env bash
# Starts or stops the pinned, authenticated Paperclip test server (docker/paperclip-test/compose.yml).
#   scripts/paperclip-test-server.sh up      start, wait until healthy, and check it reports authenticated mode
#   scripts/paperclip-test-server.sh down    stop and delete its data
#   scripts/paperclip-test-server.sh logs    follow its logs (dump prints the last 200 lines and exits)
#   scripts/paperclip-test-server.sh health  print /api/health
# Environment: PAPERCLIP_SERVER_VERSION overrides the pin (for the nightly job against `latest`),
#              PAPERCLIP_TEST_PORT overrides host port 3199.
set -euo pipefail
cd "$(dirname "$0")/.."

pinned="$(sed -n 's/^paperclip-server = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
export PAPERCLIP_SERVER_VERSION="${PAPERCLIP_SERVER_VERSION:-$pinned}"
port="${PAPERCLIP_TEST_PORT:-3199}"
export PAPERCLIP_TEST_PORT="$port"
compose=(docker compose -f docker/paperclip-test/compose.yml -p pafm-paperclip-test)

case "${1:-}" in
  up)
    "${compose[@]}" up --detach --wait --wait-timeout 180
    health="$(curl --silent --fail "http://127.0.0.1:${port}/api/health")"
    echo "$health"
    mode="$(printf '%s' "$health" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("deploymentMode",""))')"
    if [ "$mode" != "authenticated" ]; then
      echo "expected deploymentMode=authenticated but got '${mode}'" >&2
      exit 1
    fi
    echo "Paperclip ${PAPERCLIP_SERVER_VERSION} is up in authenticated mode at http://127.0.0.1:${port}"
    ;;
  down) "${compose[@]}" down --volumes --remove-orphans ;;
  logs) "${compose[@]}" logs --follow ;;
  dump) "${compose[@]}" logs --no-color --tail 200 ;;
  health) curl --silent --fail "http://127.0.0.1:${port}/api/health" ;;
  *) echo "usage: $0 up|down|logs|dump|health" >&2; exit 64 ;;
esac
