# Spec: board token cache for the foreground unlock session (ADR-144 option C, ADR-145)

Owner decision 2026-11-05: ADR-144 = option C; DEC-1 (a budget raise or set to zero asks the device owner) and DEC-2 (an INTERNET-capable but unvalidated network counts as online) are kept.

## Problem

With app lock on, the board token is a protected secret and `StoredCredentials.call` reads it for every gateway call; since ADR-139 each read is its own system prompt. A refresh therefore asks for the fingerprint several times.

## Requirements

1. A decorator over the `SecretStore` port (`SessionCachingSecretStore`) caches only the board token, per `InstanceId`, in memory. The device stores stay per-use.
2. Cleared by: `put`, `delete`, `deleteNamespace` on that factory; the app going to the background (all); a 401 or `KeyInvalidated` for that factory. No timer.
3. Single flight per factory: concurrent calls cause one protected read. `Denied`, `Invalidated` and `Missing` are never cached; a cancelled read caches nothing.
4. Callers get a fresh `Secret.copy()` (they wipe what they are given). A read that started before a clear never repopulates the cache.
5. Destructive actions still go through `Biometrics` before the use case.
6. Nothing persists: a Konsist rule keeps the cache memory only.
7. Unlock: investigate making the unlock prompt the protected read; do not ship two prompts silently.
8. Docs: ADR-145, ADR-144 and ADR-139 amended, DEC-1 and DEC-2 recorded as confirmed, technical details, user docs, device checklist.

## Out of scope

A `SecretStore` port change so that one prompt both unlocks the app and reads the token (decision for the owner, see implementation notes). Any device measurement.
