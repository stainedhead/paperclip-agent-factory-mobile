# Technical details

How the code is organised and built. The rules behind it are in [AGENTS.md](../AGENTS.md); the reasons for the main choices are in the [decision record](./architectural-decision-record.md).

## Layers and modules

Dependencies point inward only (enforced by Konsist, see below):

```
iosApp (SwiftUI)  ──imports only──►  shared/umbrella  (Koin graph, PaperclipKit.xcframework)
                                          │
                       presentation ─► application ─► domain ◄─ infrastructure/*
```

| Module | Holds today | Status |
|---|---|---|
| `shared/domain` | `BaseUri` (address rules), `BaseUriError`, `BaseUriResult` | in use |
| `shared/application` | `ValidateInstanceAddress` use case | in use |
| `shared/presentation` | `FirstRunStateHolder`, `FirstRunState`, `FirstRunIntent`, `AddressIssue` | in use |
| `shared/umbrella` | `PaperclipCore.createGraph(...)`, `PaperclipGraph`, the XCFramework and SKIE setup, the Konsist rules | in use |
| `shared/testing` | fakes and demo data | empty until Phase 1a |
| `shared/infrastructure/{api,realtime,persistence,securestore,platform}` | adapters | empty until Phase 1b |

Every module is a Kotlin Multiplatform library with these targets: `jvm` (fast tests, the future push relay), `android` (AGP's KMP library plugin) and `iosArm64` / `iosSimulatorArm64`. Module build files are a few lines each; targets, the JDK 21 toolchain, "warnings are errors" and the common test dependencies live in the convention plugin `build-logic/src/main/kotlin/pafm.kmp-library.gradle.kts`.

Package root: `com.stainedhead.paperclipmobile`. Every dependency version is in `gradle/libs.versions.toml`, including `paperclip-server`, the Paperclip release that contract tests and fixtures are pinned to.

## How iOS consumes the shared core

1. `./scripts/build-kit.sh` runs Gradle to build `PaperclipKit.xcframework` (SKIE turns Kotlin `Flow` into Swift `AsyncSequence` and sealed types into Swift enums) and copies it to `iosApp/Frameworks/` (git-ignored).
2. `iosApp/Packages/PaperclipKit` is a local Swift package whose binary target points at that XCFramework.
3. `iosApp/project.yml` is the source of truth for the Xcode project; `xcodegen generate` creates `iosApp.xcodeproj` (git-ignored). The app is Swift 6 with complete strict concurrency, iOS 17.
4. SwiftUI views render state from a `*Model` that mirrors a Kotlin state holder and forwards intents. They hold no rules.

Kotlin/Native renames functions that start with `new`, `alloc`, `copy`, `mutableCopy` or `init` (for example `newFoo` becomes `doNewFoo` in Swift). A Konsist rule keeps the Swift-facing modules free of those names.

## Slice 1a-1: factories, secrets, settings, auth, roles

- **Domain** (`shared/domain`): `Instance`, `Company`, `AppSettings`, `PairingChallenge`, `BoardKey`, `UserProfile`; value objects `InstanceId`, `CompanyId`, `Secret` (redacts `toString()`, can be wiped, `reveal()` hidden from Objective-C), `SpkiPin`, `SecretKey` (`instance/{id}/{name}`); `DomainError` (nine variants) and `DomainResult`; `Role`, `Action`, `Permissions` (one Role x Action table), `DeviceAuthPolicy` (which actions need device authentication), `AppLockPolicy`. Ports live in `domain.port`: `InstanceRepository`, `SecretStore`, `AppSettingsStore`, `Clock`, `IdGenerator`, `Biometrics`, `Connectivity`, `PairingBrowser`, `HealthGateway`, `AuthGateway`.
- **Application**: one use case per action under `application.instance`, `application.auth` and `application.settings`. Destructive use cases call `DeviceAuthGuard` first; no gateway or repository call happens unless it returns Approved. Reads go through `retryingRead` (500 ms, 1 s, 2 s backoff, up to 3 retries); mutations are never retried. `PairInstance` polls with `delay()` and the `Clock` port, writes the token to the `SecretStore` only after approval and wipes every in-memory secret on every exit (ADR-012).
- **Presentation**: `FirstRunStateHolder` (screen 15), `FactoriesStateHolder` (8), `ManageFactoriesStateHolder` (16), `EditInstanceStateHolder` (9, add and edit), `SettingsStateHolder` (13) and `AppStateHolder` (app lock, selected factory and company). Each takes a use-case bundle, an injected `CoroutineScope` and exposes `state` and `onIntent`; `close()` cancels its work. Errors and notices are enum keys (`ErrorKey`, `DeviceAuthIssue`, ...).
- **Testing**: a fake per port with call counters (`FakeAuthGateway.count("revokeCurrentKey")`), `TestData` fixtures, and contract suites for `InstanceRepository`, `SecretStore` and `AppSettingsStore` (ADR-014).
- **Umbrella**: `PaperclipCore.createGraph(allowInsecureLocal)` builds a Koin graph on the fakes (demo mode) and offers `create...StateHolder()` for each screen.

## Slice 1a-2: Inbox, decisions, approvals, budget incidents

- **Domain**: `AttentionItem` and `AttentionFeed` in the server's shape (eleven `AttentionSourceKind`s plus `Unsupported(raw)`, twelve `AttentionDetail` variants, `ResolverAudience`), `Severity`, `Money` (integer minor units, no float, ADR-016), `DecideBy` (sealed, parsed from the server string, ADR-015), `AttentionRules` (mirrors the server's `isDecideNow` and `isNewToday` with UTC day boundaries) and `InboxGrouping`; `InlineActionPolicy` (server flag AND an action the app supports AND the role, ADR-019); `Decision` with `DecisionOption`, six `DecisionEffect` types plus `Unsupported`, `DecisionInput` (on the decision, not the option, research Q8), `DecisionOutcome`; `Approval` with a payload per type and `ApprovalActions`; `BudgetIncident` and `BudgetResolution`; `SnoozeTimes` (device time zone, ADR-020); `CompanyScope` and `CompanyContext` (scope + role + user id). Ports: `AttentionGateway`, `AttentionCache`, `DecisionGateway`, `ApprovalGateway`, `BudgetGateway`, `DeviceTimeZone`. `kotlinx-datetime` joined the domain.
- **Application**: `attention` (`LoadInbox` cache first then refresh, `RefreshInbox`, `ReadCachedInbox`, `LoadMoreInbox`, `SnoozeItem`, `DismissItem`, `UndoDismiss` with a five second window on the `Clock`, `SetDecideBy`, `AvailableSnoozePresets`), `decision` (`LoadDecision`, `DecideDecision`, `DismissDecision`, `PollDecisionExecution`), `approval` (`LoadApproval`, `ApproveApproval`, `RejectApproval`, `RequestRevision`, `CommentOnApproval`), `budget` (`LoadBudgetIncident`, `ResolveBudgetIncident`) and `ResolveCompanyContext`. Mutations return `MutationResult` (`Done`, `Denied`, `Invalid`, `NotAuthorized`, `Failed`, `Reconciled`) and always run in the same order: permission, validation, device authentication, one gateway call, then one re-read after a timeout or conflict (ADR-019).
- **Presentation**: `InboxStateHolder` (screen 1), `DecisionStateHolder` (2), `ApprovalStateHolder` (3), `BudgetIncidentStateHolder` (12). They never import a gateway: a Konsist rule (`UseCaseBoundaryRulesTest`) allows only use cases to call gateway ports. All text is keys (`ErrorKey`, `FeedbackUi`, `InvalidKey`, `ActionsDisabledReason`); `updatedAtEpochMillis` carries the "Updated" label.
- **Testing**: `FakeAttentionGateway` (serves the feed with the server's cursor rules and hides dismissed items), `FakeAttentionCache` (with `AttentionCacheContract`), `FakeDecisionGateway`, `FakeApprovalGateway` (the server's approval state machine), `FakeBudgetGateway`, `FakeDeviceTimeZone`, and `AttentionFixtures` (Acme Labs, PAP-1182, Engineer 2).
- **Umbrella**: `createInboxStateHolder(instanceId, companyId)`, `createDecisionStateHolder(...)`, `createApprovalStateHolder(...)`, `createBudgetIncidentStateHolder(...)`.

## Slice 1a-3: Work, interactions, agents, runs, factory home

- **Domain**: `Issue`, `IssueDetail`, `IssueFilter` (the five saved filters, with the one rule the server lacks, priority, in `matches`, ADR-022) and `IssueQuery`; `Comment`, `PlanDocument`, `Attachment`; the sealed `Interaction` (`SuggestTasks`, `AskUserQuestions`, `RequestConfirmation`, `RequestCheckboxConfirmation`, `RequestItemVerdicts`, `UnsupportedInteraction`) with `InteractionAnswer` and the pure `validate` that returns an `AnswerIssue` (ADR-023); `Agent` with its grouping and action rules; `HeartbeatRun`, `RunEvents`, `LogTail`; `DashboardSummary` and `SpendWindows`. Ports: `IssueGateway`, `InteractionGateway`, `AgentGateway`, `RunGateway`, `DashboardGateway`.
- **Application**: `issue` (`ListIssues`, `SearchIssues`, `LoadIssue` joins names, `LoadIssuePlan`, `ListIssueAttachments`, `CommentOnIssue`, `ChangeIssueStatus`, `AssignIssue`, `CreateIssue`), `interaction` (`LoadInteractions`, `RespondToInteraction`), `agent` (`ListAgents`, `LoadAgent`, `PauseAgent` with device authentication, `ResumeAgent`, `InvokeHeartbeat`, `SetAgentBudget` owner only), `run` (`LoadRun`, `TailRunLog`, `CancelRun` with device authentication, `ListIssueRuns`), `dashboard` (`LoadDashboard`, `LoadWindowSpend`). Mutations follow ADR-019: permission, validation, device authentication, one gateway call, one re-read after a timeout or conflict.
- **Presentation**: `WorkListStateHolder` (10), `IssueStateHolder` (4, with tabs Thread, Plan, Runs, Files and a draft per interaction card), `FactoryHomeStateHolder` (5), `AgentsStateHolder` (6), `AgentDetailStateHolder` (11), `RunViewerStateHolder` (7, with an `autoScroll` flag the screen turns off when the person scrolls up). Every state exposes `isOffline`, `actionsDisabledReason`, `updatedAtEpochMillis` and `refreshIntervalSeconds` (15, or 5 for a live run, 0 for a finished one) so the slice 1a-4 polling policy only has to send `Refresh`.
- **Testing**: `FakeIssueGateway` (the server's list params, no priority filter, reopen on comment), `FakeInteractionGateway` (pending only, wrong kind is a 422), `FakeAgentGateway`, `FakeRunGateway` (events by sequence, log by offset, cancel only while active), `FakeDashboardGateway`, and the fixtures `WorkFixtures` and `AgentFixtures` (PAP-1182, Engineer 1).
- **Umbrella**: `graph.work.createWorkListStateHolder(...)` and `createIssueStateHolder(...)`, `graph.agents.createAgentsStateHolder(...)` and `createAgentDetailStateHolder(...)`, `graph.runs.createRunViewerStateHolder(...)`, `graph.factory.createFactoryHomeStateHolder(...)`, wired in `SliceThreeModules.kt`.
- **Konsist**: the mutation-use-case rule now covers the `issue`, `interaction`, `agent`, `run` and `dashboard` packages; `DeviceAuthRulesTest` pins the set of use cases that depend on `DeviceAuthGuard` to the destructive ones.

## Slice 1a-4: demo data, contract suites, refresh policy

- **Demo data**: `shared/testing/demo` (`DemoIds`, `DemoFactories`, `DemoInbox`, `DemoAttentionDetails`, `DemoWork`, `DemoAgents`) seeds the fakes for `PaperclipCore.createDemoGraph()` (ADR-026). Demo clock: Wednesday 2026-11-04T12:00Z. The Home factory is signed in with a fake `pcp_board_TESTONLY...` token, Cloud factory has an expired key, Test factory is signed out. Acme Labs has the nine Inbox cards of the mock, six agents, PAP-1204 with three runs and two files, and the $1,240 of $2,000 factory home. `DemoGraphTest` (umbrella) drives every screen's state holder through the graph and asserts those facts.
- **Contract suites**: one for every port in `shared/testing/contract` (health, auth pairing and account, attention feed and triage, decision, approval, budget, issue read and write, interaction, agent, run read and write, dashboard, biometrics, clock, connectivity, id generator, time zone, pairing browser, live events, app lifecycle, plus the four of earlier slices). Each fake passes its suite through a thin subclass in `FakeGatewayContractTest`; Phase 1b adapters subclass the same suites. Konsist checks every port is named by a suite (ADR-028).
- **Refresh policy**: `RefreshVisibleScreen` (application) with `RefreshTarget` and `RefreshSession`; the `LiveEvents` and `AppLifecycle` ports; `InvalidationRules` (domain) mapping `agent.status`, `heartbeat.run.*` and `activity.logged` to cache entries. The ten polled state holders expose `refreshTarget`; the graph exposes `graph.refresh.start(target)` and `graph.lifecycle` (ADR-027).
- **Decision Snooze**: `DecisionHints` carries the card's `dismissalKey` into the Decision screen (ADR-025).
- **Traceability**: `RequirementsTraceabilityTest` (Konsist module, JVM) requires every Phase 1a FR id to appear in a `commonTest` source.
- **Known limitations**: (1) a budget incident that has left the server's active list cannot be told apart as resolved or dismissed, because the overview lists only open incidents (ADR-029, research B1); the Budget screen shows "no longer active". (2) The demo fakes ignore the company id, so Side Project Co shows the Acme Labs data. (3) The model has no field for an agent's next heartbeat time or for the network type (Tailscale), so those lines of the mocks are not in the demo state; the web server does not return the next heartbeat in the agent list (research A1) and "Tailscale" is the factory address host.

## Quality gates

`./gradlew check lint` runs all of these; `shared.yml` runs it on every pull request.

| Gate | Tool | What it checks |
|---|---|---|
| Format | Spotless with ktlint | `.kt` and `.kts` files, config in `.editorconfig` |
| Static analysis | detekt | all shared sources, config in `config/detekt/detekt.yml`, zero issues |
| Architecture | Konsist (`:shared:umbrella:konsistTest`) | import rules per layer, one `operator fun invoke` per use case, state holders expose `state` and `onIntent`, no Objective-C reserved names |
| Coverage | Kover | `koverVerifyCore`: 90% line coverage over domain + application (one aggregate); `koverVerifyPresentation`: 90% for presentation |
| Tests | kotlin.test, Kotest, Turbine | `allTests`: JVM, Android host (`androidHostTest`) and iOS simulator |
| Android Lint | AGP | `lint` |
| Swift | SwiftLint `--strict`, swift-format `--strict`, Swift Testing, XCUITest | `ios.yml` and `./scripts/ios-test.sh` |

Each gate was shown to fail on a deliberate violation before it was trusted. Konsist rules also assert they can see the code they guard, so an empty scan cannot pass.

## CI

| Workflow | Runs on | Does |
|---|---|---|
| `shared.yml` | ubuntu | `./gradlew check lint` |
| `ios.yml` | macOS | Kotlin/Native tests, XCFramework, SwiftLint, swift-format, Xcode unit and UI tests |
| `contract.yml` | ubuntu | starts the pinned Paperclip in Docker and checks it reports `authenticated`; nightly also checks `latest` |
| `security.yml` | ubuntu | gitleaks on the full history |

Dependabot (`.github/dependabot.yml`) proposes weekly grouped updates for Gradle dependencies and GitHub Actions. Dependabot alerts and dependency review need the repository's Dependency graph switched on in GitHub settings, which is not done yet.

## The Paperclip test server

`docker/paperclip-test/compose.yml` runs `ghcr.io/paperclipai/paperclip:<paperclip-server>` with `PAPERCLIP_DEPLOYMENT_MODE=authenticated`, exposure `private`, no LLM keys, on host port 3199. `scripts/paperclip-test-server.sh` reads the version from the catalog, starts it, waits for health and checks the mode. The app refuses `local_trusted` servers, so a developer's usual local Paperclip cannot be used for contract tests.

## Known server facts (Paperclip 2026.1023.0)

- Slice 1a-2 facts are in `specs/261101-phase-1a-shared-contracts/research.md` (answers Q2 to Q5, Q7, Q8): `inlineResolvable` is true for eight source kinds but the app has inline actions only for approvals and hard-stop budget alerts; `subject.href` is a relative path and can be null; `POST /decisions/{id}/decide` runs the effects inside the request; decision inputs belong to the decision; there is no single-incident budget endpoint; approving or rejecting an already resolved approval to the other status is a 422, not a 409.

- Board keys are rejected on the live-events WebSocket (`/api/companies/{id}/events/ws` accepts agent keys and browser sessions), so the app polls.
- `/api/health` returns `deploymentMode` and `deploymentExposure` to anyone; the server `version` is only in the response to signed-in callers. The `!db` branch omits the exposure and the 503 branch omits the mode, so the probe fails closed.
- The board API token is returned when a pairing challenge is created, before the person approves; the app holds it in memory and stores it only on approval. The company role comes from `GET /api/cli-auth/me` `memberships[].membershipRole`; `viewer` is enforced by the server (ADR-011).
- A fresh authenticated server reports `bootstrapStatus: bootstrap_pending` (no users yet). Phase 1b contract tests must complete the bootstrap invite to create the first board user before they can pair.
