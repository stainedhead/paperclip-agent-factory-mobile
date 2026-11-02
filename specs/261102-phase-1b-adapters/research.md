# Research: Phase 1b shared adapters

**Date:** 2026-11-02 · **Source PRD:** [phase-1b-adapters-PRD.md](./phase-1b-adapters-PRD.md)

## Research questions

1. How are the first board user created and a pairing challenge approved against a fresh authenticated server in CI (Better Auth sign-up route and body, bootstrap_pending handling, `/cli-auth/challenges/:id/approve` and the session cookie it needs)? Read server/src and record the exact requests.
2. What are the exact request and response bodies (including error bodies and status codes) of every endpoint the gateway ports use? Record per endpoint with file:line, starting from the Phase 1a research answers.
3. Which SQLDelight and Ktor versions are compatible with Kotlin 2.4.21 and the Android KMP library plugin 9.5.0? Check the catalog candidates before adding them.
4. What does Ktor's Darwin engine surface for TLS and certificate failures, and how can they be mapped to `CertificateInvalid` testably on the simulator (self-signed loopback stub)?
5. Which Security.framework and LocalAuthentication APIs are callable from Kotlin/Native without cinterop definitions, and what does Keychain behave like on the simulator (accessibility, biometry access control)?
6. Can the Phase 1a fakes' behaviors (decide-then-poll, cursor paging, approval state machine) be reproduced by a MockEngine server stub without re-implementing the server, to run the contract suites against real adapters?

## Industry standards

OWASP MASVS L2 storage, crypto, auth and network controls (already in the PRD NFR).

## Existing implementations

The Paperclip web app (`~/Code/Test/paperclip/ui/src`) and server source (`server/src`, `packages/shared/src`) are the behavioral reference. Phase 1a code and ADR-010 to ADR-037 are the in-repo precedent.

## API documentation

Pinned server 2026.1023.0 (`@paperclipai/shared` types and route source). Answers are recorded here with file and line.

## Best practices

Test first; fixtures from the server's own types; one commit per slice task; every decision that surprises goes into an ADR.

## Open questions

1. Is full SPKI pin enforcement required in 1b or can it land in a later security pass? (Default: hook and tests in 1b, enforcement stretch.)
2. Should the feed cache keep more than the last page per company? (Default: last loaded pages up to 200 items.)
3. How should the app behave when the server's anonymous health omits `version`? (Default: show nothing before pairing, warn after.)

## References

[phase-1b-adapters-PRD.md](./phase-1b-adapters-PRD.md)

## Answers: milestone 1b-1 (client, errors, health, auth)

Server paths are relative to `~/Code/Test/paperclip` (the pinned 2026.1023.0 source).

**Q3 Versions (ANSWERED, verified by building).** Maven Central metadata (2026-11-02): Ktor `3.6.1` (latest, published against Kotlin 2.3.21, `ktor-client-darwin-3.6.1-kotlin-tooling-metadata.json`), kotlinx-serialization-json `1.12.0` (built with Kotlin 2.4.10), plugin `org.jetbrains.kotlin.plugin.serialization:2.4.21` exists. SQLDelight latest is `2.4.1` (added to the catalog in 1b-2, with its module). A spike with `ktor-client-core` + `ktor-client-mock` compiled and ran green on `jvmTest`, `testAndroidHostTest` and `iosSimulatorArm64Test` under Kotlin 2.4.21 and AGP 9.5.0 with `allWarningsAsErrors`.

**Q2 `GET /api/health`** (`server/src/routes/health.ts`). Unauthenticated caller of an `authenticated` server: 200 redacted body `{status: "ok"|"starting", deploymentMode, deploymentExposure, localAiLoginSupported, commit, bootstrapStatus: "ready"|"bootstrap_pending", bootstrapInviteActive, ...}` (lines 394-418), no `version`. `local_trusted` or an authenticated caller gets `version`/`serverVersion` too (lines 421-448). Database down: 503 `{status:"unhealthy", version, serverVersion, commit, error:"database_unreachable"}` (lines 318-330), no `deploymentMode`. No-db branch (lines 270-290) omits `deploymentExposure`. Mapping: status string -> `ServerStatus`, absent mode -> `Unknown` (refused, fail closed), `version ?: serverVersion` -> `serverVersion`.

**Q2 Pairing** (`server/src/routes/access.ts`). `POST /api/cli-auth/challenges` (2786-2808): body validated by `createCliAuthChallengeSchema` (`packages/shared/src/validators/access.ts:73-78`): `command` REQUIRED 1-240 chars, `clientName` <=120, `requestedAccess` default `board`, `requestedCompanyId` guid; 201 `{id, token, boardApiToken, approvalPath, approvalUrl|null, pollPath, expiresAt, suggestedPollIntervalMs}`. `approvalUrl` is null when the server cannot derive its own origin, so the client builds `baseUri + approvalPath` (a web path, not under `/api`). Rate limited with 429 and `Retry-After` (2690-2698). `GET /api/cli-auth/challenges/:id?token=` (2810-2838): non-UUID id -> 400 `Invalid CLI auth challenge ID`, unknown id or wrong token -> 404 `CLI auth challenge not found`, else 200 `{id,status,...,expiresAt}`; `status` is one of `pending|approved|cancelled|expired` (`board-auth.ts:49-54`). Approve (2842) and cancel (2872) take `{token}` and need a browser session, so the app never calls them; the stub emulates them for `approveInBrowser`.
**Q2 Account.** `GET /api/cli-auth/me` (2885-2898): 200 `{user:{id,name,email}|null, userId, isInstanceAdmin, companyIds, memberships:[{companyId,membershipRole,status}], source, keyId|null}`; 401 `Board authentication required` otherwise. `GET /api/board-api-keys` (2901): 200 array `{id,name,createdAt,lastUsedAt,revokedAt,expiresAt}` (`board-auth.ts:247-274`; active only unless `includeInactive=true`). `POST /api/board-api-keys` (2912): creates a named key; not used by the app (pairing is the only way a device gets a key). `DELETE /api/board-api-keys/:keyId` (2954): non-UUID -> 400 `Invalid board API key ID`, not the caller's -> 404 `Board API key not found`, else 200 `{ok:true,keyId}`. `POST /api/cli-auth/revoke-current` (2970): 400 `Current board API key context is required` unless the actor is a board key, else 200 `{revoked:true,keyId}`. `GET /api/companies?scope=accessible` (`server/src/routes/companies.ts:380-400`): 200 array of companies (`id`, `name`, ...); any other `scope` -> 400.

**Q2 Errors.** Every thrown `HttpError` becomes `{error: message, details?, code?...}` with the error's status (`server/src/middleware/error-handler.ts:157-215`); `HttpError` helpers in `server/src/errors.ts:1-60`: 400, 401, 403, 404, 409, 413, 415, 422, 429. Zod failures -> 400 `{error:"Validation error", details:[...]}` (225-232); malformed JSON -> 400 `{error:"Invalid JSON body"}` (236-243); crashes -> 500 `{error:"Internal server error"}`; shutdown -> 503. Secret-sensitive routes (cli-auth, board keys) replace 5xx bodies with `{error:"Internal server error"}` (205-215). Routes: health 200/503; create challenge 201/400/429; poll 200/400/404/429; me 200/401; keys list 200/401; revoke key 200/400/401/404; revoke-current 200/400/401; companies 200/400/401/403.

**Q4 Darwin engine** (`ktor-client-darwin-3.6.1-sources.jar`, read from Maven Central). `TimeoutUtils.kt:29` maps `NSURLErrorTimedOut` to Ktor's `SocketTimeoutException`; every other NSError is `DarwinHttpRequestException(origin: NSError)` (`DarwinUtils.kt:132`), so TLS failures arrive as NSURLErrorDomain codes -1200 to -1206 (secure connection failed, certificate bad date, untrusted, unknown root, not yet valid, client certificate rejected or required) and -1022 (ATS). `classifyUrlError` maps those to `CertificateInvalid`; everything else, including -1004, -1003, -1009, -1005, to `Unreachable`. Tested in `iosTest` against real `NSError` values wrapped in `DarwinHttpRequestException`. A live self-signed loopback test is NOT done in 1b-1 (needs a TLS stub server on the simulator); it moves to 1b-5 with the Swift loopback test. Darwin also ships `certificates/CertificatePinner` (SPKI sha256 pins per host pattern, `handleChallenge`), the building block for AD-005 enforcement (see ADR-040).
**JVM/OkHttp:** `javax.net.ssl.SSLException` (handshake, peer unverified, hostname) -> `CertificateInvalid`; `ConnectException`, `UnknownHostException`, `NoRouteToHostException`, `SocketException` -> `Unreachable`; `java.net.SocketTimeoutException` -> `Timeout`.

**Q6 Contract suites on real adapters.** Suites in `shared/testing/.../contract` are abstract classes with `create()` plus scenario hooks (`approveInBrowser`, `letExpire`, addresses); `Fake*ContractTest` subclasses in `shared/testing/src/commonTest/.../FakeGatewayContractTest.kt` implement them and call `runAll()` inside `runTest`. The real-adapter subclasses live in `shared/infrastructure/api/src/commonTest` and implement the same hooks on `StubPaperclipServer`.

**Ktor facts the design relies on.** `HttpRequestTimeoutException`, `ConnectTimeoutException`, `SocketTimeoutException` are `kotlinx.io.IOException`s whose messages contain the request URL (the poll URL carries the challenge token), so no exception message is ever copied. The `Auth` plugin caches tokens and re-sends after a 401, and `HttpRequestRetry` would replay mutations; neither is installed. Redirects are not followed (a redirected request would carry the bearer to another host).
**Test fixtures on Kotlin/Native.** `commonTest/resources` files cannot be read from a Kotlin/Native test (no classpath), so server bodies are Kotlin raw strings in `ServerFixtures` (deviation from "fixtures under src/commonTest/resources", ADR-041).
