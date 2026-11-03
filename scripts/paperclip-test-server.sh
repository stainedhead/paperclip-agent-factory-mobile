#!/usr/bin/env bash
# Starts or stops the pinned, authenticated Paperclip test server (docker/paperclip-test/compose.yml).
#   scripts/paperclip-test-server.sh up      start, wait until healthy, and check it reports authenticated mode
#   scripts/paperclip-test-server.sh down    stop and delete its data
#   scripts/paperclip-test-server.sh logs    follow its logs (dump prints the last 200 lines and exits)
#   scripts/paperclip-test-server.sh health  print /api/health
#   scripts/paperclip-test-server.sh invite  create a bootstrap CEO invite in the running container and print ONLY its
#                                            token (pcp_bootstrap_...). The contract test accepts it to create the first
#                                            admin: export PAPERCLIP_CONTRACT_BOOTSTRAP_INVITE="$(... invite)".
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
  invite)
    # The pinned image starts only the server, so nothing prints the bootstrap invite that `paperclipai run` would.
    # `paperclipai auth bootstrap-ceo` inserts one row into `invites`; the same row is inserted here with the image's
    # own `postgres` client, which needs no CLI and no config file. The token never leaves this shell except on stdout.
    token="pcp_bootstrap_$(python3 -I -c 'import secrets; print(secrets.token_hex(24))')"
    hash="$(printf '%s' "$token" | python3 -I -c 'import hashlib,sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')"
    "${compose[@]}" exec -T -w /app/packages/db -e PAFM_INVITE_HASH="$hash" paperclip node --input-type=module - >&2 <<'JS'
import postgres from "postgres";
import { readFileSync } from "node:fs";
let port = 54329;
try {
  const fromPid = Number(readFileSync("/paperclip/instances/default/db/postmaster.pid", "utf8").split("\n")[3]);
  if (fromPid > 0) port = fromPid;
} catch {}
const sql = postgres(`postgres://paperclip:paperclip@127.0.0.1:${port}/paperclip`, { max: 1 });
await sql`update invites set revoked_at = now(), updated_at = now()
  where invite_type = 'bootstrap_ceo' and revoked_at is null and accepted_at is null`;
await sql`insert into invites (invite_type, token_hash, allowed_join_types, expires_at, invited_by_user_id)
  values ('bootstrap_ceo', ${process.env.PAFM_INVITE_HASH}, 'human', now() + interval '72 hours', 'system')`;
await sql.end();
JS
    active="$(curl --silent --fail "http://127.0.0.1:${port}/api/health" | python3 -I -c 'import json,sys; print(json.load(sys.stdin).get("bootstrapInviteActive"))')"
    if [ "$active" != "True" ]; then
      echo "the invite was inserted but /api/health does not report bootstrapInviteActive=true (got '${active}')" >&2
      exit 1
    fi
    printf '%s\n' "$token"
    ;;
  *) echo "usage: $0 up|down|logs|dump|health|invite" >&2; exit 64 ;;
esac
