# Plan: iPhone app on a shared Kotlin core

**Spec dir:** `specs/261101-iphone-app/` · **Source:** [PRD](../../../paperclip-agent-factory-mobile-PRD.md), [design mocks](../../../Paperclip-Remote-mobile-UI-design.html), [AGENTS.md](../../../AGENTS.md), [INTENT.md](../../../INTENT.md)
**Status:** Draft for owner review · **Date:** 2026-11-01

This is the implementation plan. The other spec files (`spec.md`, `research.md`, `data-dictionary.md`, `architecture.md`, `tasks.md`, `implementation-notes.md`) are produced next with the `prd-to-spec` / `dev-flow:create-spec` skills, then `tasks.md` is broken down from the milestones below.

## 1. Decisions in this plan

| # | Decision | Why |
|---|---|---|
| D1 | **iPhone first in the UI, shared-first in the build.** The PRD suggests Android first when only one UI stream exists; you asked for iPhone, so that is overridden. Android joins later and reuses everything below the UI. | The user's request. The cost is slower feedback on the KMP code, covered by JVM tests and demo mode. |
| D2 | **Build the shared core before any SwiftUI screen.** Milestone 1a (domain, application, presentation, fakes, demo data) gates all iOS UI work. | Principle 5, "build once". The iOS app only renders state, so it can be built entirely on fakes. |
| D3 | **iOS consumes `PaperclipKit.xcframework` through a local Swift Package binary target**, not a Gradle "embed and sign" build phase. | Xcode-native resolution, deterministic CI, no Gradle invocation inside every Xcode build. The dev loop uses a script that builds only the simulator slice (see 4.3). |
| D4 | **Shared modules are separate Gradle modules with strict inward dependencies** (section 3), so `domain`, `application` and `api` can later be reused by the Android app and the Phase 3 relay without dragging UI code. | The reuse requirement. |
| D5 | **Polling is a shared use case; the WebSocket is an optional adapter.** | Verified: the server's live-events WebSocket authenticates bearer tokens only against agent keys, so board keys fail (PRD risk 1). Fixing it upstream then changes one adapter. |

## 2. What the installed server tells us (read-only check, 2026-11-01)

Source: `paperclipai` package **2026.1023.0** in the local npx cache. No server was started or changed.

- **Pin:** `paperclip-server = 2026.1023.0` in `gradle/libs.versions.toml`.
- **Confirmed present** (route registrations read, full lists, no cap): `GET /api/health`, `GET /api/openapi.json`; pairing: `POST /api/cli-auth/challenges`, `GET /api/cli-auth/challenges/{id}`, `GET /api/cli-auth/me`, `POST /api/cli-auth/revoke-current`; keys: `GET|POST /api/board-api-keys`, `DELETE /api/board-api-keys/{keyId}`; inbox: `GET /api/companies/{c}/attention`, `/inbox-dismissals` (GET, POST, DELETE by item key), `GET|PUT /decision-triage/{kind}/{id}`; decisions: `GET /api/decisions/{id}`, `POST …/decide`, `…/dismiss`; approvals: `GET /api/approvals/{id}`, `…/comments`, `…/issues`, `POST …/approve`, `…/reject`, `…/request-revision`, `…/comments`; budgets: `POST /api/companies/{c}/budget-incidents/{id}/resolve`, `PATCH /api/agents/{a}/budgets`; agents: `GET /api/companies/{c}/agents`, `POST /api/agents/{a}/pause|resume|heartbeat/invoke`; runs: `GET /api/heartbeat-runs/{r}`, `…/events`, `…/log`, `POST …/cancel`; issues: `GET|POST /api/companies/{c}/issues`, `GET|PATCH /api/issues/{id}`, `GET|POST /api/issues/{id}/comments`, `GET /api/issues/{id}/runs`, interactions `GET|POST /api/issues/{id}/interactions` with `accept`, `reject`, `respond`, `verdicts`; dashboard: `GET /api/companies/{c}/dashboard`, `/costs/window-spend`.
- **Not found:** nothing the PRD names is missing at this version. (Checked by route registration only; behaviour is unverified.)
- **WebSocket:** the path is `/api/companies/{c}/events/ws`, and `realtime/live-events-ws.js` authenticates bearer tokens only against agent keys. Board keys fail, which confirms D5 (polling by default).
- **Health probe (FR-005) works for refusing `local_trusted`:** the normal response branch returns `deploymentMode` and `deploymentExposure` to anonymous callers in every mode. The field is `deploymentExposure`, not `exposure`; the PRD's DTO and FR-001 wording should follow. The server `version` and `serverInfo` appear only in the full-detail response (board or agent callers, or any caller when not `authenticated`), so the version-compatibility warning runs after pairing. Recorded as a 1b-1 contract test.
- **Not every endpoint was exercised.** Route presence is not behaviour; contract tests against a running pinned server (section 5) are the proof.

### The local instance cannot be used as is

- The server is **not running** (only its embedded Postgres is up; nothing answers on `127.0.0.1:3100`).
- Its config (`~/.paperclip/instances/default/config.json`) is `deploymentMode: local_trusted`, `bind: loopback`. The app is required to refuse this mode (AGENTS.md security rules, FR-005), and `local_trusted` has no board-key pairing at all.
- **Proposal, not done:** do not change that instance, because its web app relies on that mode. Run a **second instance** in `authenticated` / `private` mode (separate instance directory and port) for contract and manual testing. Section 5 has the details.

## 3. Architecture and shared packages

```
                     iosApp (SwiftUI)            androidApp (Compose, later)
                           │  imports only                 │
                           ▼                               ▼
                 ┌──────────────────────── umbrella ───────────────────────┐
                 │  Koin graph, wiring, exports PaperclipKit.xcframework   │  composition root
                 └───────┬───────────────────────────────────┬─────────────┘
                         ▼                                   ▼
                  presentation  ──────►  application  ──►  domain  ◄──── infrastructure/*
                  (state holders,         (use cases)       (entities,     api · realtime ·
                   UI models, intents)                       ports,        persistence ·
                                                             errors)       securestore · platform
                                              testing: fakes for every port + demo data
                                              (depends on domain only; used by tests and demo mode)
```

Rules (all enforced by Konsist in `shared/umbrella/src/konsistTest`): dependencies point inward only; DTOs, Ktor, SQLDelight, Keychain types never leave `infrastructure/*`; Swift sees only `umbrella`.

### What lives where (and so what Android and the relay reuse)

| Module | Holds | Reused by |
|---|---|---|
| `shared/domain` | `Instance`, `Company`, `AttentionItem`, `Approval`, `Decision`, `Issue`, `Agent`, `HeartbeatRun`, value objects (`BaseUri`, `InstanceId`, `Severity`, `Money`, `Secret`), ports (`InstanceRepository`, `SecretStore`, `PaperclipGateway`, `LiveEvents`, `Clock`, `Biometrics`, `AppSettingsStore`, `Connectivity`), `DomainError` | everything |
| `shared/application` | One use case per action: `AddInstance`, `ProbeInstance`, `PairInstance`, `RemoveInstance`, `ReorderInstances`, `LoadInbox`, `ResolveApproval`, `DecideDecision`, `ResolveBudgetIncident`, `PauseAgent`, `CancelRun`, `RefreshVisibleScreen` (the polling policy), `UpdateAppSettings`, … | Android, relay (read use cases) |
| `shared/presentation` | One state holder per screen, `StateFlow<ScreenState>` plus `Intent`s; every UI rule (what is enabled, what needs confirmation, error wording keys) | Android, iOS |
| `shared/infrastructure/api` | Ktor client (Darwin engine on iOS), DTOs, mappers, bearer auth, error-body mapping | Android, relay |
| `shared/infrastructure/persistence` | SQLDelight: `instance_config`, `app_settings`, entity cache | Android, iOS |
| `shared/infrastructure/securestore` | `expect`/`actual` `SecretStore`: Keychain (iOS now), Keystore (Android later) | per platform |
| `shared/infrastructure/platform` | Clock, connectivity, `Biometrics` (LocalAuthentication on iOS), push-token bridge (Phase 3) | per platform |
| `shared/infrastructure/realtime` | WebSocket client, built and disabled until the server accepts board keys | Android, iOS |
| `shared/testing` | Fakes for every port, fixtures, demo data set; the same contract tests run against fake and real adapters | tests, demo mode |
| `shared/umbrella` | Koin modules, `PaperclipKit` facade, SKIE configuration | iOS (Android uses the graph directly) |

### Screen to state holder map (all in `shared/presentation`)

| Screen | State holder | Main use cases |
|---|---|---|
| 15 First-run setup | `FirstRunStateHolder` | `ProbeInstance`, `AddInstance`, `PairInstance`, `LoadDemoFactory` |
| 8 Factories | `FactoriesStateHolder` | `ListInstances`, `SwitchInstance`, `SwitchCompany` |
| 16 Manage factories | `ManageFactoriesStateHolder` | `ReorderInstances`, `RemoveInstance` (Biometrics-gated) |
| 9 Add / edit factory | `EditInstanceStateHolder` | `ProbeInstance`, `UpdateInstance`, `SignOutInstance`, `RePairInstance` |
| 13 Settings and profile | `SettingsStateHolder` | `UpdateAppSettings`, `ListBoardKeys`, `RevokeBoardKey`, `SignOutEverywhere` |
| 1 Inbox | `InboxStateHolder` | `LoadInbox`, `SnoozeItem`, `DismissItem`, `UndoDismiss`, inline approve/reject/keep-paused |
| 2 Decision | `DecisionStateHolder` | `LoadDecision`, `DecideDecision`, `DismissDecision`, `PollDecisionExecution` |
| 3 Approval | `ApprovalStateHolder` | `LoadApproval`, `ResolveApproval`, `RequestRevision`, `CommentOnApproval` |
| 12 Budget incident sheet | `BudgetIncidentStateHolder` | `ResolveBudgetIncident` |
| 4 Issue + interactions | `IssueStateHolder` | `LoadIssue`, `CommentOnIssue`, `UpdateIssue`, `RespondToInteraction` |
| 10 Work list | `WorkListStateHolder` | `ListIssues`, `SearchIssues`, `CreateIssue` |
| 5 Factory home | `FactoryHomeStateHolder` | `LoadDashboard`, `LoadWindowSpend` |
| 6 Agents, 11 Agent detail | `AgentsStateHolder`, `AgentDetailStateHolder` | `ListAgents`, `PauseAgent`, `ResumeAgent`, `InvokeHeartbeat`, `SetAgentBudget` |
| 7 Run viewer | `RunViewerStateHolder` | `LoadRun`, `TailRunLog`, `CancelRun` |
| app shell | `AppStateHolder` | `ObserveAppLock`, `ObserveSelectedInstance`, deep-link routing |

Swift-facing rule: UI models are plain data classes with Swift-friendly names; no generics, default arguments or `Result` on the iOS surface; SKIE turns `Flow` into `AsyncSequence` and sealed classes into Swift enums.

## 4. Phases, milestones and exit criteria

Sizes are relative (S, M, L), not calendar estimates. Every milestone is TDD: failing test first, minimum code, refactor. Every PR passes `spotlessCheck`, `detekt`, `lint`, `allTests`, `konsistTest`, `koverVerify`. `status.md` is updated after each task.

### Phase 0: Foundations (M)

1. **Repository: decided.** Own repo `stainedhead/paperclip-agent-factory-mobile` (public, `main`, no branch protection). Workflows live in `.github/workflows/`. Next step here is a `.gitignore` and an initial import PR.
2. Gradle build: version catalog (`paperclip-server = 2026.1023.0`), KMP module skeleton from the table in section 3, Spotless/ktlint, detekt, Kover (90% gate on `domain` + `application`, separate 90% gate on `presentation`), Konsist rules.
3. `iosApp/` Xcode project (iOS 17, Swift 6), Swift Package `PaperclipKit` with a local binary target, SKIE, SwiftLint `--strict`, swift-format `--strict`, a Swift Testing target and an XCUITest target.
4. CI workflows `shared`, `ios` (Android and contract workflows are stubbed until their phases).
5. **Exit:** empty modules build, all gates run in CI, the app launches to a blank SwiftUI screen that calls one function from `PaperclipKit`.

### Phase 1a: Shared contracts (L), gates all UI work

Slices, each its own shared-module PR:

| Slice | Content |
|---|---|
| 1a-1 | Instance, secret, settings and auth domain: `BaseUri` validation (FR-003), `Instance`, `Secret` (redacting `toString`), `SecretStore`, `Biometrics`, `AppSettings`; use cases `AddInstance`, `ProbeInstance`, `RemoveInstance`, `ReorderInstances`, `UpdateAppSettings`; state holders for screens 15, 8, 16, 9, 13 |
| 1a-2 | Attention, decision, approval, budget incident: entities, `LoadInbox`, `DecideDecision`, `ResolveApproval`, `ResolveBudgetIncident`, role gating; state holders for 1, 2, 3, 12 |
| 1a-3 | Work, agents, runs, factory: entities, use cases, state holders for 4, 10, 5, 6, 11, 7 |
| 1a-4 | `shared/testing` complete: a fake per port, one demo data set that matches the mock screens (Acme Labs, PAP-1182, Engineer 1 and so on), shared contract-test suites that real adapters later also pass |

**Exit:** all use cases and state holders fully tested on fakes; coverage gate met; the iOS app can run every screen in demo mode with no server; a Swift-interop review of the exported surface (SKIE output, Sendable warnings) is done and recorded in `implementation-notes.md`.

### Phase 1b: Shared adapters (L), runs in parallel with Phase 2i

| Slice | Content | Test |
|---|---|---|
| 1b-1 | Ktor client (Darwin engine), bearer plugin, error-body to `DomainError`, `/api/health` probe, challenge pairing flow (`ProbeInstance`, `PairInstance`) | `MockEngine` + fixtures recorded from the pinned server; one test per mapping and error shape |
| 1b-2 | SQLDelight: `instance_config`, `app_settings`, entity cache; in-memory driver in tests | persistence tests, shared repository contract tests |
| 1b-3 | iOS Keychain `SecretStore` (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, not synchronizable, `.biometryCurrentSet` with `.userPresence` fallback), `Biometrics` via LocalAuthentication, connectivity and clock | simulator tests; assert secrets never reach SQLDelight or backups |
| 1b-4 | Per-feature gateway adapters and DTOs (inbox, decisions, approvals, issues, agents, runs, dashboard, costs), hand-written where `openapi.json` does not cover them | one contract test per DTO against the pinned server |
| 1b-5 | `RefreshVisibleScreen` polling (15 s, 5 s on a running run, stops in background); WebSocket adapter built but off by capability check | `kotlinx-coroutines-test` virtual time |

**Exit:** contract tests green against the pinned authenticated server (section 5); Keychain tests green; the iOS app switches from fakes to real adapters by changing one Koin module.

### Phase 2i: iPhone UI (L), starts when 1a is merged

Each screen ships as two PRs: shared state holder (already done in 1a), then the SwiftUI view in `iosApp/` that only renders state and forwards intents. Order follows dependency:

| Milestone | Screens | Delivers acceptance criteria (PRD) | Also needs |
|---|---|---|---|
| 2i-0 | Xcode project wired to `PaperclipKit`, demo-mode launch, app shell, tab bar, app lock | none (scaffolding) | 1b-3 (Keychain, biometrics) for app lock |
| 2i-1 | 15 First-run, 8 Factories, 9 Add / edit, 16 Manage, 13 Settings | 1 (two factories, pair, secrets only in Keychain), 2 (`local_trusted` and `http://` refused), 3 (delete removes config, cache, secrets, revokes key), 11 (first run), 12 (remove with confirmation), 13 (device rename) | 1b-1 (probe, pairing), 1b-2 (persistence), 1b-3 (Keychain) |
| 2i-2 | 1 Inbox | 6 (inline actions, badge updates without refresh) | 1b-4 (inbox adapters) |
| 2i-3 | 2 Decision, 3 Approval, 12 Budget sheet | 4 (Face ID on reject and destructive actions), 5 (decision effects and per-effect results) | 1b-3 (biometrics), 1b-4 (decision, approval, budget adapters) |
| 2i-4 | 5 Factory home | none (feeds criterion 9 later) | 1b-4 (dashboard, costs) |
| 2i-5 | 6 Agents, 11 Agent detail, 7 Run viewer | 8 (cancel run reaches `cancelled` within one refresh) | 1b-4 (agents, runs), 1b-5 (run refresh) |
| 2i-6 | 10 Work list, 4 Issue + interactions | 7 (`ask_user_questions` answered on phone, visible in web thread) | 1b-4 (issues, interactions) |
| 2i-7 | Offline and polish: cached-first rendering with "Updated" labels, disabled actions offline, accessibility pass (Dynamic Type 200%, VoiceOver labels), XCUITest screenshots (iOS snapshot library is an open choice; AGENTS.md names none) | 9 (offline) | 1b-2 (cache), 1b-3 (connectivity) |

**Exit:** acceptance criteria 1 to 9 and 11 to 13 pass on iOS; SwiftLint, swift-format and `xcodebuild test` green.

### Later (outside this plan, in order)

Phase 3 notifications (relay, APNs, screen 14, acceptance criterion 14), Android UI reusing `shared/` unchanged, then v1.1 items.

### iOS dev loop

- `scripts/build-kit.sh` builds a **debug, `iosSimulatorArm64`-only** XCFramework (fast); CI builds the full release XCFramework.
- Daily work happens in `commonTest` on the JVM (seconds); Xcode is for view work, which runs on demo mode.
- Consider `-Xbinary=...` incremental flags and the Gradle configuration cache once rebuild time is measured; do not optimise before then.

## 5. Dev and test server strategy

1. **UI work needs no server:** demo mode runs on `shared/testing`.
2. **Contract and manual testing need a server in `authenticated` mode** at the pinned version. Two options, pick one in Phase 0:
   - **A (not chosen; you ruled out a second local instance):** a second local instance with its own directory and port, `deploymentMode: authenticated`, `exposure: private`, a seeded board user and company. Needs `allowedHostnames` for any non-loopback name; for the simulator, `http://localhost:<port>` is allowed in debug builds by the security rules. Check in the package source what authenticated mode requires on loopback (base URL, auth secret, a created user) before writing the setup script; do not assume.
   - **B (chosen):** the same server in Docker at the pinned version, started by `contract.yml` in CI (required there anyway). Locally it is slower to inspect.
3. A seed script creates the demo data set on that server (company, agents, a decision, an approval, an `ask_user_questions` interaction, a budget incident) so manual runs match the mocks.
4. **Do not change the existing local instance.** Its `local_trusted` mode is what the web app uses on this machine.
5. Contract tests run in CI and on demand, not on every local build. The nightly run against latest detects drift (PRD risk 6).

## 6. Risks specific to iPhone and the shared core

| Risk | Mitigation |
|---|---|
| Swift 6 strict concurrency vs Kotlin/Native exported types (Sendable warnings) while `swiftlint --strict` demands zero warnings | Spike in Phase 0 on a real exported state holder; wrap in `@MainActor` adapters in Swift; keep the exported surface small and plain |
| SKIE supports a limited range of Kotlin versions | Pin Kotlin to a SKIE-supported release in the catalog; bump both together in their own PR |
| XCFramework rebuild time slows the Xcode loop | Simulator-only debug slice, JVM tests for logic, measure before tuning |
| `.biometryCurrentSet` key invalidation cannot be fully exercised on the simulator | Cover logic with the `Biometrics` fake and `SecretStore` contract tests; one manual device test per release (acceptance criterion 4) |
| `ASWebAuthenticationSession` pairing flow is hard to automate | Isolate behind a `PairingBrowser` port; test the polling and state machine in shared code; one XCUITest with a stubbed browser; one manual run per release |
| Anonymous health omits server version in `authenticated` mode | Show mode and exposure before pairing; run the version check after pairing (section 2) |
| Board keys rejected on WebSocket | Polling use case in shared code (D5); upstream change tracked, adapter ready |
| Fakes drift from real behaviour | One shared contract-test suite runs against fake and real adapter |
| Demo data diverges from mocks | Demo data set is the single source for mocks, screenshots in docs and UI tests |

## 7. Open questions that block or shape work

1. ~~Repository~~ **Decided:** own repo, `stainedhead/paperclip-agent-factory-mobile`, public, `main`, no branch protection.
2. ~~Test server~~ **Decided:** Docker at the pinned version, no second local instance (assumed from your answer; confirm).
3. ~~Apple account and bundle id~~ **Decided for now:** `com.stainedhead.paperclipmobile`; not publishing yet.
4. ~~Distribution~~ **Deferred:** not yet.
5. **Tools:** Xcode 27.2 and JDK 21 are installed; `swiftlint` is not (`brew install swiftlint`); `swift-format` comes with the Xcode toolchain.

## 8. Documentation and quality tracking

Each milestone updates in the same PR: `README.md` (how to build and run demo mode), `docs/product-details.md` (screens and flows, with screenshots from demo mode per AGENTS.md), `docs/technical-details.md`, and `docs/architectural-decision-record.md` (D1 to D5 above become the first entries). `user-docs/` gets the pairing, Tailscale and allowed-hostname guides in 2i-1. `specs/261101-iphone-app/status.md` is the single source of progress.
