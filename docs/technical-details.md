# Technical details

How the code is organised and built, as it exists on the branch `feat/phase-1a-shared-contracts`. The rules behind it are in [AGENTS.md](../AGENTS.md); the reasons for the main choices are in the [decision record](./architectural-decision-record.md). The behaviour of each screen is in [product-details.md](./product-details.md). For a hands-on start see [user-docs/getting-started.md](../user-docs/getting-started.md).

## State of the build

| Area | State |
|---|---|
| Shared core: domain, application, presentation, testing fakes, umbrella | Built (Phase 1a). Runs entirely on fakes. |
| Real adapters (`shared/infrastructure/*`) | Not built. The five modules exist as Gradle stubs with no source. Phase 1b. |
| iPhone app | One screen: the first-run address check. Every other screen has a tested state holder and no UI. |
| Android app | Not started. There is no `androidApp/` directory. The shared modules do build an Android library target. |
| Push relay | Not started. |

No Paperclip server is contacted by either `PaperclipCore.createGraph` or `PaperclipCore.createDemoGraph`; both are wired to the fakes in `shared/testing`.

## Layers and modules

Dependencies point inward only (enforced by Konsist, see below):

```
iosApp (SwiftUI)  ──imports only──►  shared/umbrella  (Koin graph, PaperclipKit.xcframework)
                                          │
                       presentation ─► application ─► domain ◄─ infrastructure/*
```

| Module | Contents (all `commonMain` Kotlin) | Status |
|---|---|---|
| `shared/domain` | Entities and value objects (`Instance`, `Company`, `AppSettings`, `AttentionItem`, `Decision`, `Approval`, `BudgetIncident`, `Issue`, `Interaction`, `Agent`, `HeartbeatRun`, `DashboardSummary`, `BaseUri`, `Secret`, `Money`, `DecideBy`, `Severity`, `Role`, `Action`), policies (`Permissions`, `DeviceAuthPolicy`, `AppLockPolicy`, `AttentionRules`, `InlineActionPolicy`, `InvalidationRules`, `SnoozeTimes`), `DomainError` and `DomainResult`. 23 ports in `domain.port`. Depends on the Kotlin stdlib, kotlinx-coroutines-core and kotlinx-datetime only. | Built |
| `shared/application` | Use cases in `instance`, `auth`, `settings`, `attention`, `decision`, `approval`, `budget`, `issue`, `interaction`, `agent`, `run`, `dashboard`, `refresh`; shared helpers `DeviceAuthGuard`, `MutationResult`, `retryingRead`, `ResolveCompanyContext`, `ValidateInstanceAddress`. 71 `operator fun invoke` declarations. Depends on domain only. | Built |
| `shared/presentation` | 16 state holders (one per screen plus `AppStateHolder`), their `State` and `Intent` types, UI models, renderers. Depends on application and domain. | Built |
| `shared/testing` | 23 fakes (one per port), the contract suites for every port, fixtures (`TestData`, `AttentionFixtures`, `WorkFixtures`, `AgentFixtures`) and the demo data set (`testing/demo`). Depends on domain. Ships inside the XCFramework, so it uses no test framework. | Built |
| `shared/umbrella` | `PaperclipCore`, `PaperclipGraph`, the Koin modules, `AppLifecycleBridge`, `RefreshScreens`, the XCFramework `PaperclipKit` and SKIE setup; the Konsist rules (`src/konsistTest`). | Built |
| `shared/infrastructure/{api,realtime,persistence,securestore,platform}` | Only a `build.gradle.kts` each (`api` and `testing` depend on `domain`). | Stubs |

Every module is a Kotlin Multiplatform library with the targets `jvm` (fast tests, the future push relay), `android` (AGP's KMP library plugin, minSdk 28, compileSdk 37, host tests) and `iosArm64` / `iosSimulatorArm64`. Targets, the JDK 21 toolchain, "warnings are errors" and the common test dependencies live in the convention plugin `build-logic/src/main/kotlin/pafm.kmp-library.gradle.kts`.

Package root: `com.stainedhead.paperclipmobile`. Every dependency version is in `gradle/libs.versions.toml`, including `paperclip-server`, the Paperclip release that contract tests and fixtures are pinned to (`2026.1023.0`). See [configuration-reference.md](../user-docs/configuration-reference.md).

### Ports (23)

| Group | Ports |
|---|---|
| Device and platform | `Clock`, `IdGenerator`, `SecretStore`, `Biometrics`, `Connectivity`, `AppLifecycle`, `PairingBrowser`, `DeviceTimeZone` |
| Local persistence | `InstanceRepository`, `AppSettingsStore`, `AttentionCache` |
| Server (gateways) | `HealthGateway`, `AuthGateway`, `AttentionGateway`, `DecisionGateway`, `ApprovalGateway`, `BudgetGateway`, `IssueGateway`, `InteractionGateway`, `AgentGateway`, `RunGateway`, `DashboardGateway` |
| Server push | `LiveEvents` (no real adapter: board keys are rejected on the WebSocket) |

## The graph

`PaperclipCore` (in `shared/umbrella`) is the composition root. A platform app creates one graph and asks it for state holders.

| Call | Meaning |
|---|---|
| `PaperclipCore.createGraph(allowInsecureLocal)` | A graph on empty fakes: no factories, a `FakeClock` at 2026-11-01T12:00Z that does not advance, `FakeIdGenerator("factory-")`, a `FakeHealthGateway` that answers "authenticated, private, ok". `allowInsecureLocal` is true only in debug builds and lets `http://localhost` and `http://10.0.2.2` through address validation. The iPhone app calls this. |
| `PaperclipCore.createDemoGraph()` | The same wiring on fakes seeded with the demo data set of the design mocks (ADR-026). Clock frozen at Wednesday 2026-11-04T12:00Z. |

Both use an isolated Koin application, so graphs never share state. The "live" graph on real adapters is Phase 1b; `createGraph` keeps its signature and will gain them.

| Member of `PaperclipGraph` | Creates / does |
|---|---|
| `createFirstRunStateHolder()`, `createAppStateHolder()`, `createFactoriesStateHolder()`, `createManageFactoriesStateHolder()`, `createSettingsStateHolder()`, `createEditInstanceStateHolder(instanceId: String?)` | Setup screens (15, app shell, 8, 16, 13, 9). `null` adds a factory. |
| `attention` | `createInboxStateHolder`, `createDecisionStateHolder(…, hints: DecisionHints)`, `createApprovalStateHolder`, `createBudgetIncidentStateHolder` (screens 1, 2, 3, 12) |
| `work` | `createWorkListStateHolder`, `createIssueStateHolder` (10, 4) |
| `agents` | `createAgentsStateHolder`, `createAgentDetailStateHolder` (6, 11) |
| `runs` | `createRunViewerStateHolder` (7) |
| `factory` | `createFactoryHomeStateHolder` (5) |
| `refresh` | `start(target: RefreshTarget): RefreshSession`; call `stop()` when the screen goes away |
| `lifecycle` | `AppLifecycleBridge`: `enterBackground()`, `enterForeground()`, `isForeground` |

Each `create...StateHolder` call returns a new holder with its own `CoroutineScope` on `Dispatchers.Default`; the screen owns it and calls `close()` when it goes away. Holders expose `state: StateFlow<XState>` and `onIntent(XIntent)`; ten of them also expose `refreshTarget`.

`PaperclipGraph.resolve<T>()` is `internal`: only code inside `shared/umbrella` can reach the fakes behind the graph (for example the `FakeLiveEvents`). To script a fake from outside, build the use cases and fakes yourself (see [usage-examples.md](../user-docs/usage-examples.md)).

## Layering rules and how they are enforced

Konsist tests in `shared/umbrella/src/konsistTest` (task `:shared:umbrella:konsistTest`, part of `check`) fail the build on a violation. 23 rules:

| File | Rule |
|---|---|
| `DependencyRulesTest` | Domain imports only the standard library, coroutines and datetime. Application imports domain only. Presentation imports application and domain only. Infrastructure modules import domain only and never each other. |
| `UseCaseBoundaryRulesTest` | Presentation never imports a gateway or `AttentionCache`. Domain ports named `*Gateway` are used only by application classes. Every mutation use case that needs a role takes a `CompanyContext`. |
| `ConventionRulesTest` | Every use case has exactly one public `operator fun invoke`. Every state holder exposes `state` and `onIntent`. Holders that refresh expose `refreshTarget`. Swift-facing modules have no function or property starting with `new`, `alloc`, `copy`, `mutableCopy` or `init`. |
| `PresentationSurfaceRulesTest` | No public presentation class, function or property declares type parameters; no public presentation signature mentions `Result` or `DomainResult`. |
| `PortAndSecretRulesTest` | Every port has a `FakeX` in `shared/testing` and a contract suite there that names it. `Secret.reveal()` is called only from infrastructure, the fakes and `Secret` itself. |
| `DeviceAuthRulesTest` | Exactly these use cases take `DeviceAuthGuard`: `RemoveInstance`, `DecideDecision`, `RejectApproval`, `ResolveBudgetIncident`, `PauseAgent`, `CancelRun`. |
| `RequirementsTraceabilityTest` | Each of the 47 Phase 1a requirement ids appears in a `commonTest` source. The exemption map is empty. |

Each rule also asserts it can see the code it guards, so an empty scan cannot pass vacuously, with one exception: the infrastructure rule skips any module that has no `src/commonMain`, and today all five are such stubs, so it checks nothing yet.

## Mutation use cases: the fixed order

Every mutation takes a `CompanyContext(scope, role, userId)` and runs in this order (ADR-019, ADR-021):

1. **Permission.** `Permissions.allows(role, action)`; otherwise `MutationResult.Denied`. A viewer never reaches a gateway. Device-local actions (add, edit, remove, sign out, revoke key, settings) are open to every role.
2. **Validation.** Required notes and bodies, input values, status preconditions, snooze time in the future, interaction answers. Failure returns `Invalid(reason)`.
3. **Device authentication.** Only if `DeviceAuthPolicy.requires(action)`: pause agent, cancel run, reject approval, destructive decision option, raise-and-resume of a budget, remove factory. `DeviceAuthGuard` calls the `Biometrics` port; anything but `Approved` returns `NotAuthorized(result)`. There is no setting that turns this off.
4. **One gateway call.** Never retried. A decision carries an idempotency key from `IdGenerator`.
5. **One re-read** only after `Timeout` or `Conflict`: the result is `Reconciled(error, current)` with the entity as the server has it now. If the re-read also fails the original error is returned as `Failed`.

`MutationResult` is `Done`, `Denied`, `Invalid`, `NotAuthorized`, `Failed` or `Reconciled`. Everything except `Done`, `Failed` and `Reconciled` means no gateway call happened. Offline gating is the state holders' job (the use cases have no `Connectivity`): offline, every action is disabled with `ActionsDisabledReason.Offline`.

Reads go through `retryingRead`: up to three retries with 500 ms, 1 s and 2 s backoff, only for `Unreachable`, `Timeout` and `ServerError`, so a persistent failure makes four calls.

## Error taxonomy

| Layer | Type | Values |
|---|---|---|
| Domain | `DomainError` | `Unreachable`, `Timeout`, `Unauthorized`, `Forbidden`, `NotFound`, `Conflict`, `Rejected(reason)`, `ServerError`, `Malformed`. Failures are values (`DomainResult.Failure`), never thrown. |
| Application | `MutationResult`, `InvalidInput` | see above; `InvalidInput` is `NoteRequired`, `BodyRequired`, `WrongStatus`, `UnknownOption`, `SnoozeNotInFuture`, `UndoWindowClosed`, `TitleRequired`, `NotAddressedToYou`, `Answer`, `Input`, `Amount` |
| Application | `BaseUriError`, `ProbeRefusal`, `InstanceNameIssue` | address, server-mode and name problems when adding or editing a factory |
| Presentation | `ErrorKey` | `Unreachable`, `Timeout`, `SignedOut` (from `Unauthorized`), `NoPermission` (from `Forbidden`), `NotFound`, `Conflict`, `Rejected` (with the server's reason as `detail`), `ServerError`, `Malformed` |
| Presentation | `DeviceAuthIssue`, `AddressIssue`, `ActionsDisabledReason` (`Offline`, `ViewerRole`) | |

All text a screen shows is a key; the platform UI owns the wording and the localisation. Raw server JSON never crosses the adapter boundary.

## Refresh policy

`RefreshVisibleScreen(connectivity, lifecycle, liveEvents)(scope, target)` returns a `RefreshSession` (ADR-027). The platform passes a holder's `refreshTarget` to `graph.refresh.start` when the screen appears and calls `session.stop()` when it goes away.

- Active only when the phone is online **and** the app is in the foreground.
- While active, refreshes every `target.refreshIntervalSeconds`. The interval is part of each holder's state, so it can change: 15 s by default; 5 s on the Run viewer while the run is live and on Agent detail while the agent has a run; 0 (no timer) for a finished run, a final Decision, a resolved Approval and a closed Budget incident.
- Refreshes at once when the app returns to the foreground or the phone comes back online (not at start: the holder has just loaded).
- Refreshes when a `LiveEvent` maps, through `InvalidationRules`, to something the target says it is affected by (`agent.status`, `heartbeat.run.*`, `activity.logged`), at most once a second, newer events replacing older ones. There is no real `LiveEvents` adapter yet, so this path only runs with a fake.
- Timers use coroutine `delay`, not the `Clock` port, so tests run in virtual time.
- The state holders ignore a `Refresh` while one is in flight, so the policy cannot double-load.
- Nothing refreshes in the background or offline. The shell reports the background through `graph.lifecycle`.

The Inbox is cache-first (ADR-017): `LoadInbox` emits the cached first page (marked `isFromCache`), then the network result; the label "Updated" is the server's `generatedAt`. Only the first page is cached.

## Demo data and fakes

`shared/testing/fakes` has a `FakeX` for every port (23; `FakeAppLifecycle` lives next to `FakeLiveEvents`). The graph itself uses `AppLifecycleBridge` from the umbrella as its `AppLifecycle`. Fakes are behaviourally honest where the server's behaviour matters (cursor rules, the approval state machine, idempotency-key replay, per-address health, key state) and expose call counters (`count("decide")`), scripted failures (`failures["get"] = DomainError.Timeout`) and, for the platform fakes, a switch (`FakeBiometrics.result`, `FakeConnectivity.setOnline`).

`shared/testing/demo` (`DemoIds`, `DemoFactories`, `DemoInbox`, `DemoAttentionDetails`, `DemoWork`, `DemoAgents`) seeds the fakes of `createDemoGraph()` with the facts of the design mocks:

- Device "Alex's iPhone"; person Alex Rivera (Owner of Acme Labs, Operator of Side Project Co).
- Factories: Home factory `https://paperclip.tail1234.ts.net` (signed in with a fake `pcp_board_TESTONLY…` key, 23 days left), Cloud factory `https://paperclip.example.com` (signed out, expired key), Test factory `http://localhost:3100` (signed out, no key). Ids in `DemoIds`: `demo-home`, `demo-cloud`, `demo-test`, `company-acme`, `company-side`.
- Acme Labs: nine Inbox cards (badge 3, three Decide-now cards), six agents, PAP-1182 and PAP-1204 with runs and files, a $1,240 of $2,000 factory home.

The tests in `DemoGraphTest.kt` and `DemoGraphDeviceTest.kt` (umbrella) drive every screen through the graph and assert those facts; `DemoGraphInteropTests.swift` does the same from Swift.

## Contract suites

Every port has a contract suite in `shared/testing/contract`: a plain abstract class with abstract hooks (`create()`, and scripting hooks such as `willAnswer`) and `suspend fun runAll()` that uses `check` (ADR-014). The fake passes it through a thin `@Test` subclass in `shared/testing/src/commonTest` (27 subclasses in `FakeContractTest.kt` and `FakeGatewayContractTest.kt`). Phase 1b adapters subclass the same suites in their own test source sets. Suites use only port calls and handles the subclass guarantees, never a fake's `calls` or `store` (ADR-028). The Docker-based `contractTest` against a real Paperclip does not exist yet.

## Tests and coverage

Fresh numbers from the last local run (2026-11-01, HEAD `3533a16`):

| Where | Tests (JVM) |
|---|---|
| `shared/domain` | 135 |
| `shared/application` | 242 |
| `shared/presentation` | 266 |
| `shared/testing` | 49 |
| `shared/umbrella` | 30 |
| Konsist (`konsistTest`) | 23 |
| Swift (`iosApp/Tests`) | 28 Swift Testing cases in `iosAppTests` (counted from source) and 1 XCUITest |

`commonTest` runs on the JVM, the Android host (`testAndroidHostTest`) and the iOS simulator (`iosSimulatorArm64Test`) under `./gradlew check`.

Kover coverage, regenerated with `rm -rf build/reports/kover && ./gradlew koverXmlReportCore koverXmlReportPresentation -q` and read from `build/reports/kover/*.xml`:

| Gate | Task | Line coverage | Threshold |
|---|---|---|---|
| Domain + application, one aggregate | `koverVerifyCore` | 97.58% (2256 covered, 56 missed) | 90% |
| Presentation | `koverVerifyPresentation` | 97.96% (3947 covered, 82 missed) | 90% |

Kover rules cannot filter by package, so there are two custom variants, `core` and `presentation` (ADR-007). `koverVerify` alone checks nothing meaningful. Infrastructure, `testing` and `umbrella` are outside the gates.

## Quality gates

`./gradlew check lint` runs all of these; `shared.yml` runs it on every pull request.

| Gate | Tool | What it checks |
|---|---|---|
| Format | Spotless with ktlint | `.kt` and `.kts` files, config in `.editorconfig` |
| Static analysis | detekt | all shared sources, config in `config/detekt/detekt.yml`, zero issues |
| Architecture | Konsist | see above |
| Coverage | Kover | `koverVerifyCore`, `koverVerifyPresentation` |
| Tests | kotlin.test, Kotest, Turbine, coroutines-test | `allTests`: JVM, Android host and iOS simulator |
| Android Lint | AGP | `lint` |
| Swift | SwiftLint `--strict`, swift-format `--strict`, Swift Testing, XCUITest | `ios.yml` and `./scripts/ios-test.sh` |

Each gate was shown to fail on a deliberate violation before it was trusted.

## How iOS consumes the shared core

1. `./scripts/build-kit.sh [debug|release]` runs Gradle to build `PaperclipKit.xcframework` and copies it to `iosApp/Frameworks/` (git-ignored). SKIE turns Kotlin `Flow` into Swift `AsyncSequence` and sealed types into Swift enums.
2. `iosApp/Packages/PaperclipKit` is a local Swift package with a binary target on that XCFramework.
3. `iosApp/project.yml` is the source of truth for the Xcode project; `xcodegen generate` creates `iosApp.xcodeproj` (git-ignored). Swift 6, complete strict concurrency, iOS 17, iPhone only.
4. A SwiftUI view renders state from a `*Model` that mirrors a Kotlin state holder and forwards intents. It holds no rules. Today that is `FirstRunView` and `FirstRunModel`; `PaperclipApp` creates the graph with `PaperclipCore.shared.createGraph(allowInsecureLocal:)` (true under `#if DEBUG`).

The framework exports `presentation`, `application` and `domain`; `shared/testing` is not exported, so `PaperclipCore.createDemoGraph()` is the only demo entry.

### Swift-facing rules

The Swift interop review (acceptance criterion 6, in the spec's implementation notes) found these rules, several enforced by Konsist:

- No generics, `Result`, or Kotlin default arguments on the presentation and umbrella surface; Swift passes every argument.
- No function or property named `new…`, `alloc…`, `copy…`, `mutableCopy…`, `init…` (Kotlin/Native would rename it, for example `newFoo` to `doNewFoo`); factory methods are `create…` (ADR-009).
- UI text is keys (enums); never prose.
- A state holder is `state` (an `AsyncSequence` through SKIE, and `.value` for the current state), `onIntent(intent:)` and `close()`. Intents are `data object` (`FactoriesIntentRefresh.shared`) or `data class` (`InboxIntentFilterSelected(kind:)`).
- Create and use a holder on the main actor; never pass one to another actor. The holders load on their own dispatcher, so a test waits for a settled state instead of asserting a loading phase.
- Nullable numbers arrive boxed (`KotlinInt?`, `KotlinLong?`): read `.intValue` or `.int64Value`. A `StateFlow<Boolean>` value is a `KotlinBoolean` (`.boolValue`).
- Sealed hierarchies get an `onEnum(of:)` function for exhaustive `switch`.

## CI

| Workflow | Runs on | Does |
|---|---|---|
| `shared.yml` | ubuntu | `./gradlew check lint --continue` |
| `ios.yml` | macOS | `iosSimulatorArm64Test`, XCFramework, SwiftLint, swift-format, Xcode unit and UI tests |
| `contract.yml` | ubuntu | starts the pinned Paperclip in Docker and checks it reports `authenticated`; there are no contract tests yet; nightly also checks `latest` |
| `security.yml` | ubuntu | gitleaks on the full history |

The files are `.github/workflows/{shared,ios,contract,security}.yml` (AGENTS.md names them `pafm-*.yml`; see "Known limitations"). Dependabot (`.github/dependabot.yml`) proposes weekly grouped updates for Gradle dependencies and GitHub Actions. Dependabot alerts and dependency review need the repository's Dependency graph switched on in GitHub settings, which is not done yet. The four workflows have not run on this branch because no pull request exists for it; the local equivalents are green.

## The Paperclip test server

`docker/paperclip-test/compose.yml` runs `ghcr.io/paperclipai/paperclip:<paperclip-server>` with `PAPERCLIP_DEPLOYMENT_MODE=authenticated`, exposure `private`, no LLM keys, on host port 3199. `scripts/paperclip-test-server.sh` reads the version from the catalog, starts it, waits for health and checks the mode. The app refuses `local_trusted` servers, so a developer's usual local Paperclip cannot be used for contract tests (ADR-008).

## Known server facts (Paperclip 2026.1023.0)

- Board keys are rejected on the live-events WebSocket (`/api/companies/{id}/events/ws` accepts agent keys and browser sessions), so the app polls (ADR-004).
- `/api/health` returns `deploymentMode` and `deploymentExposure` to anyone; the server `version` is only in the response to signed-in callers. The `!db` branch omits the exposure and the 503 branch omits the mode, so the probe fails closed.
- The board API token is returned when a pairing challenge is created, before the person approves; the app holds it in memory and stores it only on approval (ADR-012). The company role comes from `GET /api/cli-auth/me` `memberships[].membershipRole`; `viewer` is enforced by the server (ADR-011).
- A fresh authenticated server reports `bootstrapStatus: bootstrap_pending` (no users yet). Phase 1b contract tests must complete the bootstrap invite to create the first board user before they can pair.
- `inlineResolvable` is true for eight source kinds but the app has inline actions only for approvals and hard-stop budget alerts; `subject.href` is a relative path and can be null; `POST /decisions/{id}/decide` runs the effects inside the request; decision inputs belong to the decision; there is no single-incident budget endpoint; approving or rejecting an already resolved approval to the other status is a 422, not a 409. Evidence by file and line is in [`research.md`](../specs/archive/261101-phase-1a-shared-contracts/research.md).

## Known limitations and gaps

Behaviour:

1. **No adapters.** `createGraph` and `createDemoGraph` run on fakes. `LiveEvents` will have no real adapter even in Phase 1b (board keys fail on the WebSocket).
2. **Budget history.** A budget incident that has left the server's active list cannot be told apart as resolved or dismissed, because the overview lists only open incidents (ADR-029, research B1). The screen shows `NoLongerActive`; `KeptPaused` and `Raised` come from the app's own resolve response.
3. **Demo fakes.** They ignore the company id, so Side Project Co shows the Acme Labs data. The demo Run viewer's events and log are the same for every run.
4. **Decision details.** `GET /decisions/{id}` has neither `decideBy` nor the agent name; the Decision screen receives them as hints from the Inbox card (ADR-018). Opened from anywhere else it shows neither, and no Snooze (ADR-025).
5. **Priority filter.** "Critical and high" is filtered on the device, so a page can hold fewer rows than the page size (ADR-022).

Model gaps (the design mocks show these; the model has no field, so the demo state does not):

- An agent's next heartbeat time (not in the server's agent list).
- The network label such as "Tailscale" (derivable from the `.ts.net` host; a UI concern).
- The Approval screen carries `reportsToAgentId`, not the name; a name join is deferred.
- Settings "Each factory" has status and expiry but not the default company name; Manage factories has no company count; Edit factory shows the days left but not the key tail (the key id is in Settings, Paired devices and keys).

Swift surface (deferred, recorded in the spec's implementation notes): 21 nullable numbers arrive boxed; every holder exposes a public initialiser, so Swift could build one outside the graph (the graph is the supported path); a data class `copy` is exported as `doCopy`.

Process:

- The infrastructure Konsist rule passes without checking anything while the infrastructure modules have no sources.
- ADR-018 says decision polling runs "on the Clock"; `PollDecisionExecution` uses coroutine `delay` (up to 10 reads, 1 s apart) like the rest of the policy (ADR-027).
- AGENTS.md still names the Android test source set `androidUnitTest` (the AGP library plugin calls it `androidHostTest`, ADR-005), lists `koverVerify` (ADR-007 explains why `koverVerifyCore koverVerifyPresentation` replace it) and the workflow names `pafm-*.yml`, and describes a monorepo-root `.github`; in this repository the root is the project directory and the files are `shared.yml`, `ios.yml`, `contract.yml`, `security.yml`.
- Slice 1a-1 application and presentation code was written in the same pass as its tests (domain was strictly test first); recorded as a process deviation in the spec's implementation notes.
