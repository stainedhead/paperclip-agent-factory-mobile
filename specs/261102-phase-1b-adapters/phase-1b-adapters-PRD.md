# PRD: Phase 1b shared adapters (real server, storage, device services)

**Created:** 2026-11-02
**Status:** Draft
**Owner:** J
**Related:** [Main PRD](../../paperclip-agent-factory-mobile-PRD.md) · [iPhone plan](../261101-iphone-app/plan.md) section 4 (Phase 1b) · [Phase 1a spec](../archive/261101-phase-1a-shared-contracts/spec.md) · [ADR-030 to ADR-037](../../docs/architectural-decision-record.md)

---

## Problem Statement

Phase 1a delivered the whole non-UI contract layer in shared Kotlin (domain, use cases, state holders, fakes, contract suites) but every port is backed by a fake. Nothing talks to a real Paperclip server, stores a credential in the Keychain, persists a factory, asks Face ID, or detects connectivity. Until the adapters exist the app cannot pair with, read from, or act on a real factory, and the contract suites that were written for the real adapters have never been run against one.

Phase 1b implements every port with a real adapter, proves each against the shared contract suites and against the pinned Paperclip server (2026.1023.0, authenticated mode), and adds a live graph so a platform app can switch from fakes to the real thing by changing one factory call.

## Goals

- **G1** A Ktor-based API client for every gateway port (health, auth and pairing, attention, decisions, approvals, budget incidents, issues, interactions, agents, runs, dashboard and costs), with hand-written DTOs and mappers; DTOs never leave `infrastructure/api`.
- **G2** Typed error mapping from the server's `{error, details?}` bodies and HTTP statuses to `DomainError` (401 `Unauthorized`, 403 `Forbidden`, 404 `NotFound`, 409 and stale `Conflict`, 422 `Rejected(reason)`, 429 `RateLimited`, 5xx `ServerError`, undecodable `Malformed`, TLS failures `CertificateInvalid`, timeouts `Timeout`, no route `Unreachable`).
- **G3** SQLDelight persistence for `instance_config`, `app_settings` and the feed cache, behind the existing `InstanceRepository`, `AppSettingsStore` and `AttentionCache` ports, with migrations and in-memory drivers for tests.
- **G4** Secure storage and device services on iOS in Kotlin/Native `iosMain`: Keychain `SecretStore`, LocalAuthentication `Biometrics`, `Connectivity` (NWPathMonitor), `Clock`, `DeviceTimeZone`, `IdGenerator`, `AppLifecycle`; Android and JVM actuals fail closed (never an in-memory stand-in for secrets).
- **G5** A `PaperclipCore.createLiveGraph(...)` that wires the real adapters into the same graph shape the demo graph uses, so the iOS app changes one call. The Swift side supplies only `PairingBrowser` (ASWebAuthenticationSession needs a UIKit anchor).
- **G6** Prove it: every adapter passes its shared contract suite through `MockEngine` fixtures; a JVM `contractTest` runs the real flows (sign-up a board user, pair, read, mutate seeded data) against the pinned Docker server in CI (`contract.yml`).
- **G7** Keep every gate green (`check lint`, Kover 90% on core and presentation, Konsist, SwiftLint, swift-format, iOS tests) and keep infra coverage meaningful.

## Non-Goals

- Any SwiftUI or Compose screen (Phase 2i and 2a).
- Android Keystore, DataStore and OkHttp-specific hardening (Phase 2a); Android and JVM `SecretStore` actuals fail closed.
- Push relay and notifications (Phase 3).
- A WebSocket live-events adapter that works with board keys: the pinned server rejects board keys on the WebSocket; the adapter is built behind a capability check and stays off.
- Certificate pinning enforcement beyond storing `pinnedSpkiSha256` (build the hook; enforcement is a 1b stretch goal listed in tasks).
- Changing the pinned server version or any server code.

## Functional Requirements

Requirement ids continue the main PRD's numbering where they exist; new ids use `AD-`.

**API client (FR-097, FR-096, FR-005)**
- **AD-001** One `PaperclipHttpClient` per instance: base URI + `/api`, bearer auth from `SecretStore` (never cached outside the request), 15 s connect and 30 s read timeouts, JSON with unknown keys ignored, `Accept: application/json`, a redacting logger that never prints headers or bodies.
- **AD-002** Reads retry with exponential backoff per `RetryingRead` (never mutations). Mutations are sent exactly once.
- **AD-003** Every non-2xx and every transport failure maps to a `DomainError` through one `ErrorMapper` with a test per status and body shape (including empty bodies, HTML error pages from proxies, and non-JSON 200s).
- **AD-004** TLS and hostname failures map to `CertificateInvalid` with the user-facing key already defined; `http` is only usable when `allowInsecureLocal` is set (debug), enforced in the client, not only in `BaseUri`.
- **AD-005** Pinned-key hook: if an instance has `pins`, a mismatch fails every request with `CertificateInvalid` (stretch: full SPKI extraction on Darwin; if not feasible in 1b, record why and keep the field).

**Gateways (FR-005, FR-020 to FR-025, FR-030 to FR-035, FR-040 to FR-044, FR-050 to FR-052, FR-060 to FR-063, FR-070 to FR-072, FR-075, FR-078)**
- **AD-010** `HealthGateway` for `GET /api/health` (mode, exposure, status; version only when present).
- **AD-011** `AuthGateway` for `POST/GET /cli-auth/challenges`, `GET /cli-auth/me`, `GET/POST/DELETE /board-api-keys`, `POST /cli-auth/revoke-current`, plus validating a pending key against an explicit host (`meWithKey`, `listBoardKeysWithKey`).
- **AD-012** `AttentionGateway` (feed with cursor, dismissals, undo, decide-by triage), `DecisionGateway`, `ApprovalGateway`, `BudgetGateway`.
- **AD-013** `IssueGateway`, `InteractionGateway`, `AgentGateway`, `RunGateway`, `DashboardGateway` (including window spend).
- **AD-014** Each gateway adapter has fixtures (JSON bodies taken from the server's source types and, in CI, recorded from the pinned container), mapper tests, and runs the shared contract suite for its port against a `MockEngine` that behaves like the server.

**Persistence (FR-002, FR-095)**
- **AD-020** SQLDelight schema with migrations for `instance_config` (all non-secret fields of `Instance`), `app_settings`, and a feed cache keyed by instance and company with the "Updated" timestamp; the database never contains a secret (a test asserts every column and every stored value).
- **AD-021** `SqlInstanceRepository`, `SqlAppSettingsStore`, `SqlAttentionCache` pass the existing repository, settings and cache contract suites with in-memory drivers; a native-driver smoke test runs on the iOS simulator.
- **AD-022** Deleting an instance removes config, cache rows and (through the use case) secrets; a test proves no rows remain.

**Device services on iOS (FR-010 to FR-016, FR-014, FR-090)**
- **AD-030** `KeychainSecretStore` (generic password items, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, never synchronizable, namespaced keys `instance/{id}/{name}`, optional access control `.biometryCurrentSet` with `.userPresence` fallback, `deleteNamespace`, invalidation reported as a typed result). Run on the simulator; assert nothing reaches the database or backups.
- **AD-031** `LocalAuthenticationBiometrics` mapping approved, cancelled, failed, locked out and unavailable to the existing `BiometricResult`; fail closed when no passcode is set.
- **AD-032** `Connectivity` from `NWPathMonitor`, `AppLifecycle` bridge, system `Clock`, `DeviceTimeZone`, `IdGenerator` (UUID).
- **AD-033** Android and JVM actuals for `SecretStore` and `Biometrics` return a typed "unavailable" and never store secrets; a test pins that.

**Live graph (G5)**
- **AD-040** `PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser)` builds the same graph surface as `createDemoGraph` with real adapters and the `SignedOutAware` decorators; `createGraph` stays for tests. A Konsist rule keeps fakes out of the live graph's module wiring.
- **AD-041** First launch with an empty database yields an empty factory list (first-run flow), not demo data.

**Contract tests (G6)**
- **AD-050** A JVM `contractTest` source set and Gradle task. In CI it: waits for the pinned container, creates the first board user through the server's own sign-up and bootstrap flow, pairs a device via the challenge flow (approving the challenge with the board user's session), then exercises reads for every gateway and mutations on data it seeds through the server API. Anything that cannot be seeded through the public API is covered by `MockEngine` instead, with the gap listed.
- **AD-051** `contract.yml` runs it on pull requests that touch `shared/infrastructure/**` and nightly against `latest` (drift report, failing visibly).

## Non-Functional Requirements

- **Security:** no secret in logs, exceptions, crash text, SQLDelight, UserDefaults, files or backups; bearer tokens only in memory during a request; only the owning instance's host ever receives its key (host-recording fake tests extended to the real client); https enforced in the client.
- **Reliability:** no new unbounded waits; every network call has a timeout; cancellation is never swallowed; mutations never retried.
- **Performance:** feed load from the cache under 100 ms on an iPhone-class simulator; cold start unchanged within 10%.
- **Compatibility:** supports the pinned Paperclip version; unknown enum values and extra fields never crash (map to `Unsupported`/ignored); missing optional fields tolerated.
- **Observability:** typed errors only; no logging framework added to the shared layer.
- **Quality:** Kover gates unchanged (infra not in the gate, but each adapter has tests); Konsist: infrastructure imports only domain; no new third-party dependency beyond Ktor, kotlinx-serialization and SQLDelight at catalog-pinned versions.

## Acceptance Criteria

1. Every port in `shared/domain/port` has a real adapter (or a documented fail-closed actual on Android/JVM) and each passes its shared contract suite through the adapter's tests.
2. A table-driven test maps every server status and error body shape listed in AD-003 to the right `DomainError`.
3. A secrets audit test proves no secret value appears in the SQL database, in logs, or in any exception message across the adapter test suites.
4. The key-to-wrong-host defects stay fixed with the real client: a recording `MockEngine` test shows a pending key is only ever sent to the host that issued the challenge.
5. `contractTest` passes in CI against the pinned server for pairing, health, key listing/revoking and at least one read per gateway; the run's output lists which operations were mutation-tested live and which only via `MockEngine`.
6. `createLiveGraph` builds on the iOS simulator and a Swift test creates it, creates every state holder from it, and performs a health probe against a local mock server (URLProtocol-free: Ktor Darwin engine against a loopback stub).
7. Keychain tests on the simulator: store, read, overwrite, delete, `deleteNamespace`, not-synchronizable and accessibility attributes verified through `SecItemCopyMatching`, and a restart-simulating re-read.
8. `./gradlew check lint`, Swift lint/format/tests and all four workflows are green; coverage on core and presentation does not drop.

## Dependencies and Risks

| # | Item | Impact | Mitigation |
|---|---|---|---|
| 1 | Docker Desktop will not start on the dev machine | contract tests can only run in CI | develop with `MockEngine`; push early to see CI; keep the contract task runnable locally when a server exists |
| 2 | Server DTO shapes are only in TypeScript types and source | mapping mistakes | derive from `@paperclipai/shared` types and route source; fixtures verified in CI; ADR per surprise |
| 3 | Creating the first board user and approving a challenge in CI needs the server's auth flow | contract test complexity | research first (server source: Better Auth sign-up, bootstrap invite, `/cli-auth/challenges/:id/approve`); fall back to `MockEngine` and record the gap |
| 4 | Kotlin/Native Security.framework and LocalAuthentication interop | Keychain/biometrics bugs | simulator tests; thin cinterop-free wrappers using `platform.Security` |
| 5 | Ktor Darwin engine TLS error mapping | wrong `CertificateInvalid` mapping | test with a self-signed loopback stub on the simulator |
| 6 | Parallel work with the Phase 2i UI stream | merge conflicts in `shared/umbrella` and `iosApp` | 1b owns `shared/infrastructure/**` and umbrella wiring; 2i owns `iosApp/**`; agreed API: `createLiveGraph` |
| 7 | One real device is needed for biometric invalidation | cannot fully test `.biometryCurrentSet` | simulator covers the call path; document the manual device check |

## Open Questions

1. Is full SPKI pin enforcement required in 1b or can it land in a later security pass? (Default: hook and tests in 1b, enforcement stretch.)
2. Should the feed cache keep more than the last page per company? (Default: last loaded pages up to 200 items.)
3. How should the app behave when the server's anonymous health omits `version`? (Default: show nothing before pairing, warn after.)
