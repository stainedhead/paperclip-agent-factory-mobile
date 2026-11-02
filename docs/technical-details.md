# Technical details

How the code is organised and built, as it exists on the branch `feat/phase-1a-shared-contracts`. The rules behind it are in [AGENTS.md](../AGENTS.md); the reasons for the main choices are in the [decision record](./architectural-decision-record.md). The behaviour of each screen is in [product-details.md](./product-details.md). For a hands-on start see [user-docs/getting-started.md](../user-docs/getting-started.md).

## State of the build

| Area | State |
|---|---|
| Shared core: domain, application, presentation, testing fakes, umbrella | Built (Phase 1a). Runs entirely on fakes. |
| Real adapters (`shared/infrastructure/*`) | `api` has the HTTP client (text and ranged byte paths), `ErrorMapper`, and the health, auth, attention, decision, approval, budget, dashboard, agent, run, issue and interaction gateways (Phase 1b-1, 1b-4a, 1b-4b); `persistence` has the SQLDelight database and the three SQL adapters (Phase 1b-2); `securestore` and `platform` have the device services (Phase 1b-3); `realtime` is a stub. |
| Live graph (`PaperclipCore.createLiveGraph`) | Built (Phase 1b-5a): the real adapters behind the same graph surface, on iOS device services; the iPhone app switches to it in Phase 2i. Android is refused until its app phase. |
| iPhone app | One screen: the first-run address check. Every other screen has a tested state holder and no UI. |
| Android app | Not started. There is no `androidApp/` directory. The shared modules do build an Android library target. |
| Push relay | Not started. |

No Paperclip server is contacted by either `PaperclipCore.createGraph` or `PaperclipCore.createDemoGraph`; both are wired to the fakes in `shared/testing`. `PaperclipCore.createLiveGraph` is the one that talks to servers (see "The live graph").

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
| `shared/application` | Use cases in `instance`, `auth`, `settings`, `attention`, `decision`, `approval`, `budget`, `issue`, `interaction`, `agent`, `run`, `dashboard`, `refresh`; shared helpers `DeviceAuthGuard`, `MutationResult`, `retryingRead` and `RetryBudget`, `ResolveCompanyContext`, `ValidateInstanceAddress`, `InstanceLocks`, `SignedOutMarker` and the `SignedOutAware<X>Gateway` decorators. 71 `operator fun invoke` declarations. Depends on domain only. | Built |
| `shared/presentation` | 16 state holders (one per screen plus `AppStateHolder`), their `State` and `Intent` types, UI models, renderers. Depends on application and domain. | Built |
| `shared/testing` | 23 fakes (one per port), 26 contract suites for the 21 ports a real adapter could break (`Connectivity` and `AppLifecycle` are exempt, ADR-036), fixtures (`TestData`, `AttentionFixtures`, `WorkFixtures`, `AgentFixtures`) and the demo data set (`testing/demo`). Depends on domain. Ships inside the XCFramework, so it uses no test framework. | Built |
| `shared/umbrella` | `PaperclipCore`, `PaperclipGraph`, the Koin modules (adapters bound under `RawPort`, use cases get the decorated bindings), `AppLifecycleBridge`, `RefreshScreens`, the XCFramework `PaperclipKit` and SKIE setup; the Konsist rules (`src/konsistTest`). | Built |
| `shared/infrastructure/api` | `PaperclipHttpClient` (Ktor; Darwin on iOS, OkHttp on Android and JVM), `ErrorMapper`, `HealthGatewayAdapter`, `AuthGatewayAdapter`, internal `dto` package. Tests run the shared contract suites through a MockEngine-backed `StubPaperclipServer`. | Health and auth |
| `shared/infrastructure/persistence` | SQLDelight 2.4.1: tables `instance_config`, `app_settings`, `attention_cache`; `SqlInstanceRepository`, `SqlAppSettingsStore`, `SqlAttentionCache`, `PaperclipPersistence`; `openJvmPersistence`, `openIosPersistence`, `openAndroidPersistence`. No secret is stored. See "Persistence" below. | Built (1b-2) |
| `shared/infrastructure/securestore` | `KeychainSecretStore` (iOS, Security.framework behind an internal `KeychainBackend`), `UnavailableSecretStore` (Android, JVM). See "Device services" below. | Built (1b-3) |
| `shared/infrastructure/platform` | `SystemClock`, `UuidIdGenerator`, `SystemDeviceTimeZone`, `LocalAuthenticationBiometrics`, `NwConnectivity` (iOS), `UnavailableBiometrics`, `OptimisticConnectivity`. | Built (1b-3) |
| `shared/infrastructure/realtime` | Only a `build.gradle.kts`. | Stub |

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
| `PaperclipCore.createGraph(allowInsecureLocal)` | A graph on empty fakes: no factories, a `FakeClock` at 2026-11-01T12:00Z that does not advance, `FakeIdGenerator("factory-")`, a `FakeBiometrics` that **approves every prompt**, an in-memory `FakeSecretStore`, a `FakeHealthGateway` that answers "authenticated, private, ok". `allowInsecureLocal` is true only in debug builds and lets `http://localhost` and `http://10.0.2.2` through address validation. The iPhone app calls this. It is **not** a live graph: see ADR-037. |
| `PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser)` | The graph on the real adapters (Phase 1b-5a, ADR-067 to ADR-070). See "The live graph". |
| `PaperclipCore.createDemoGraph()` | The same wiring on fakes seeded with the demo data set of the design mocks (ADR-026). Clock frozen at Wednesday 2026-11-04T12:00Z. |

Both use an isolated Koin application, so graphs never share state. Neither can exercise the idle lock, the undo window, key expiry or "Decide now" grouping, because their clocks never advance (tests use an advanceable `FakeClock` below the graph). The "live" graph on real adapters is Phase 1b: the seam is the module that binds the ports (`portsModule()`; `demoPortsModule()` for the demo), and a build must not be released while `createGraph` still binds the fakes (ADR-037). The demo graph, the fakes and the fake demo keys ship inside the framework in every build (ADR-035).

| Member of `PaperclipGraph` | Creates / does |
|---|---|
| `createFirstRunStateHolder()`, `createAppStateHolder()` (also attaches the holder to `lifecycle`), `createFactoriesStateHolder()`, `createManageFactoriesStateHolder()`, `createSettingsStateHolder()`, `createEditInstanceStateHolder(instanceId: String?)` | Setup screens (15, app shell, 8, 16, 13, 9). `null` adds a factory. |
| `attention` | `createInboxStateHolder`, `createDecisionStateHolder(…, hints: DecisionHints)`, `createApprovalStateHolder`, `createBudgetIncidentStateHolder` (screens 1, 2, 3, 12) |
| `work` | `createWorkListStateHolder`, `createIssueStateHolder` (10, 4) |
| `agents` | `createAgentsStateHolder`, `createAgentDetailStateHolder` (6, 11) |
| `runs` | `createRunViewerStateHolder` (7) |
| `factory` | `createFactoryHomeStateHolder` (5) |
| `refresh` | `start(target: RefreshTarget): RefreshSession`; call `stop()` when the screen goes away |
| `lifecycle` | `AppLifecycleBridge`: `setForeground(Boolean)` (one call per scene change: stops or resumes refreshing and reports `WentToBackground` / `CameToForeground` to the app lock of the holder from `createAppStateHolder()`), the same calls spelled `enterBackground()` / `enterForeground()`, and `isForeground` |

Each `create...StateHolder` call returns a new holder with its own `CoroutineScope` on `Dispatchers.Default`; the screen owns it and calls `close()` when it goes away. Holders expose `state: StateFlow<XState>` and `onIntent(XIntent)`; ten of them also expose `refreshTarget`.

`PaperclipGraph.resolve<T>()` is `internal`: only code inside `shared/umbrella` can reach the fakes behind the graph (for example the `FakeLiveEvents`). To script a fake from outside, build the use cases and fakes yourself (see [usage-examples.md](../user-docs/usage-examples.md)).

## The live graph (Phase 1b-5a)

`createLiveGraph` builds the same `PaperclipGraph` surface as the demo graph (`attention`, `work`, `agents`, `runs`, `factory`, `refresh`, `lifecycle` and the `create...StateHolder` calls) on the real adapters. It reuses `useCaseModule` and `presentationModule` unchanged; only the ports module differs.

- **Seam.** `LivePlatform` (internal, `umbrella/LivePlatform.kt`) is what the graph cannot create itself: `Clock`, `IdGenerator`, `DeviceTimeZone`, `SecretStore`, `Biometrics`, `Connectivity`, the `PaperclipPersistence` and the HTTP client factory, plus `release()`. It is internal because it carries database and Ktor types that must not reach the Swift header. `PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser)` uses `defaultLivePlatform()` (`expect`/`actual`); an internal overload takes an explicit platform (tests, and `LiveIosGraph`). ADR-067.
- **Wiring** (`LiveModules.kt`): the nine company gateway adapters are bound under `RawPort` and wrapped by the same `SignedOutAware` decorators as the demo graph (`guardedGateways()`); `HealthGatewayAdapter` and `AuthGatewayAdapter` share one `PaperclipHttpClient`; `InstanceRepository`, `AppSettingsStore` and `AttentionCache` come from `PaperclipPersistence`. `LiveEvents` is "polling only" (the pinned server refuses board keys on the WebSocket), so screens refresh on the timer and on coming back online or to the foreground (ADR-068). No file named `Live*.kt` may import `shared.testing` or call `portsModule`, `demoPortsModule`, `sliceThreePorts` or `signalPorts` (`LiveWiringRulesTest`, with a self-test); `LiveGraphTest` resolves ports from a live graph and checks none is a fake.
- **First launch (AD-041).** Nothing is seeded. An empty database yields an empty factory list and the first-run flow.
- **Destinations.** Adapters read the factory's address, pins and key from the repository for every call (`StoredCredentials`), so an address saved by Edit factory is the next call's destination. `allowInsecureLocal` is enforced in the client as well as in address validation: a stored `http` address of a release graph fails with `CertificateInvalid` before any request.
- **Lifecycle.** `PaperclipGraph.close()` is idempotent and releases, in order, the HTTP client (and the engine under it; Ktor does not close an engine it was handed, so `PaperclipHttpClient.close()` does), the database, then the platform (the iOS network monitor). Close the screens' state holders first.
- **Platforms (ADR-069).** iOS: `IosLivePlatform` = `KeychainSecretStore`, `LocalAuthenticationBiometrics`, `NwConnectivity`, `SystemClock`, `UuidIdGenerator`, `SystemDeviceTimeZone`, and `openIosPersistence` (default `Application Support/PaperclipData`, excluded from backups, file protection). `LiveIosGraph.createLiveGraph(allowInsecureLocal, pairingBrowser, IosLiveSettings(keychainService, dataDirectory, unlockReason, confirmReason))` chooses the folder, Keychain service and localized prompt texts; the two-argument `PaperclipCore.createLiveGraph` uses the app service, the default folder and English texts. JVM: `UnavailableSecretStore`, `UnavailableBiometrics`, `OptimisticConnectivity`, in-memory database: pairing and destructive actions fail closed. Android: `createLiveGraph` throws `IllegalStateException` until the Android app phase brings a `Context` and the Keystore.
- **Pairing browser (ADR-070).** The port is a Kotlin suspend interface and `Secret.reveal()` is hidden from Swift, so the app passes `PairingBrowsers.shared.fromCallback { url, finished in ... }` (`CallbackPairingBrowser` in `infrastructure/api`, the only reveal point). The callback gets the approval address as text, opens it in `ASWebAuthenticationSession`, and calls `finished()` when the page is dismissed.
- **How the iPhone app switches.** In `PaperclipApp`, replace `PaperclipCore.shared.createGraph(allowInsecureLocal:)` with `PaperclipCore.shared.createLiveGraph(allowInsecureLocal:pairingBrowser:)` (or `LiveIosGraph` for its own folder and localized prompts), keep one graph for the whole process (`close()` is for tests and for tearing a graph down), and keep `createDemoGraph()` for the explicit demo option.
- **Tests.** JVM (`shared/umbrella/src/jvmTest`): `LiveGraphTest`, `LiveFlowsTest`, `LiveRefreshTest`, `LiveDefaultsTest` drive state holders against `StubPaperclipServer` (its source files are copied into the test compilation by the `sharedStubSources` task, ADR-067) on a real SQLite file. Android host: `LiveAndroidDefaultsTest`. iOS simulator: `LiveIosGraphTest` (database folder, backup exclusion, first run). Swift Testing in the app host: `LiveGraphInteropTests` (every state holder; a real health probe through URLSession to a loopback server, `local_trusted` refused). The Darwin engine and the Keychain are never driven by a pairing flow in a test; the real server is never contacted (CI container only).
- **Size.** Linking the adapters into `PaperclipKit` grew the debug framework binaries from 25.9 MB (simulator) and 25.7 MB (device) to 32.8 MB and 32.6 MB (+6.9 MB, +27%); the umbrella binaries link `-lsqlite3`.

## Layering rules and how they are enforced

Konsist tests in `shared/umbrella/src/konsistTest` (task `:shared:umbrella:konsistTest`, part of `check`) fail the build on a violation. 37 tests in 10 files:

| File | Rule |
|---|---|
| `DependencyRulesTest` | Domain imports only the standard library, coroutines and datetime. Application imports domain only. Presentation imports application and domain only. Infrastructure modules import domain only and never each other. |
| `UseCaseBoundaryRulesTest` | Presentation never imports a gateway or `AttentionCache`. Domain ports named `*Gateway` are used only by application classes. Every mutation use case that needs a role takes a `CompanyContext`. |
| `ConventionRulesTest` | Every use case has exactly one public `operator fun invoke`. Every state holder exposes `state` and `onIntent`. Holders that refresh expose `refreshTarget`. Swift-facing modules have no function or property starting with `new`, `alloc`, `copy`, `mutableCopy` or `init`. |
| `PresentationSurfaceRulesTest` | No public presentation class, function or property declares type parameters; no public presentation signature mentions `Result` or `DomainResult`; no public presentation property is named `description` or `hash` (they collide with `NSObject` and are exported as `description_`). |
| `PortAndSecretRulesTest` | Every port has a `FakeX` in `shared/testing` and a contract suite (an abstract class whose `create()` returns the port) with a subclass that runs it; `Connectivity` and `AppLifecycle` are the two exempt ports and the rule checks they really have none. `Secret.reveal()` (called, passed or referenced) is allowed only in infrastructure, `shared/testing` and `Secret` itself; the check sees `with`, `run` and `::reveal`. |
| `DeviceAuthRulesTest` | Exactly these use cases take `DeviceAuthGuard`: `RemoveInstance`, `DecideDecision`, `RejectApproval`, `ResolveBudgetIncident`, `PauseAgent`, `CancelRun`. |
| `RequirementsTraceabilityTest` | Each of the 47 Phase 1a requirement ids appears in the backtick name of a `@Test` function in a `commonTest` source (a comment or string does not count). The exemption map is empty; five ids (FR-002, 008, 013, 016, 097) are listed as partial: tagged for the Phase 1a part, the rest is Phase 1b or 1c (ADR-036). |
| `MutationMatrixRulesTest` | Every mutation use case has a row in `MutationMatrix.kt` (23 rows, one per company-scoped `Action`); the viewer, role and timeout matrices run over it. |
| `CiPinningRulesTest` | Every action in `.github/workflows` is pinned to a full commit SHA with its version in a comment; no workflow or Dockerfile uses a floating image tag. |
| `DetektConfigRulesTest` | Every deviation from the detekt defaults in `config/detekt/detekt.yml` has a reason comment above it. |

Each rule also asserts it can see the code it guards (the infrastructure rule via its fixture), so an empty scan cannot pass vacuously, and the infrastructure rule scans every `src/<set>Main` source set (commonMain, iosMain, androidMain, jvmMain) of every module and fails when a Kotlin file sits outside them. Today the five infrastructure modules are stubs, so on the real tree it checks nothing yet; a fixture test with a module that has only `iosMain` and `androidMain` files proves it reports a violation there (F-D-7).

## Mutation use cases: the fixed order

Every mutation takes a `CompanyContext(scope, role, userId)` and runs in this order (ADR-019, ADR-021):

1. **Permission.** `Permissions.allows(role, action)`; otherwise `MutationResult.Denied`. A viewer never reaches a gateway. Device-local actions (add, edit, remove, sign out, revoke key, settings) are open to every role.
2. **Validation.** Required notes and bodies, input values, status preconditions, snooze time in the future, interaction answers. Failure returns `Invalid(reason)`.
3. **Device authentication.** Only if `DeviceAuthPolicy.requires(action)`: pause agent, cancel run, reject approval, destructive decision option, raise-and-resume of a budget, remove factory. `DeviceAuthGuard` calls the `Biometrics` port; anything but `Approved` returns `NotAuthorized(result)`. There is no setting that turns this off.
4. **One gateway call.** Never retried. A decision carries an idempotency key from `IdGenerator`. `DecideDecision` takes the `DecisionId` and re-reads the decision before step 3, so destructiveness and "still open" come from the server's copy, not the caller's (ADR-032). A decision that is no longer open returns `Reconciled(Conflict, current)` with no gateway call.
5. **One re-read** only after `Timeout` or `Conflict`: the result is `Reconciled(error, current)` with the entity as the server has it now. If the re-read also fails the original error is returned as `Failed`. Run now (`InvokeHeartbeat`) and the issue and approval comments read the live runs or the comments *before* the call and compare after a timeout: `Reconciled` only for a new run or the person's own new comment, otherwise `MutationResult.Unknown(error)` ("the call may or may not have happened", ADR-032). The screen reloads and shows `FeedbackKind.Unknown`; it never claims success or "skipped".

`MutationResult` is `Done`, `Denied`, `Invalid`, `NotAuthorized`, `Failed`, `Reconciled` or `Unknown`. `Denied`, `Invalid` and `NotAuthorized` mean no gateway call happened. Offline gating is the state holders' job (the use cases have no `Connectivity`): offline, every action is disabled with `ActionsDisabledReason.Offline`.

Reads go through `retryingRead`: up to three retries with 500 ms, 1 s and 2 s backoff, only for `Unreachable`, `Timeout` and `ServerError`, so a persistent failure of one read makes four calls. A use case with several reads (`LoadApproval`, `LoadIssue`, `LoadInstanceAccess`) shares one `RetryBudget` of 3.5 s across all of them and stops retrying at once when `Connectivity` says offline. `CertificateInvalid` is never retried (ADR-034).

Configuration and credential changes of one factory (`UpdateInstance`, `KeyEnrollment`, the local half of sign out and remove, `SignedOutMarker`) run under a per-factory `InstanceLocks` mutex, never held across a server call (ADR-033). A key is validated against the host it was issued for before it is stored; a failed or cancelled enrollment leaves no orphan secret, and sign out deletes the local credentials even when cancelled (`NonCancellable`).

## Error taxonomy

| Layer | Type | Values |
|---|---|---|
| Domain | `DomainError` | `Unreachable`, `CertificateInvalid`, `Timeout`, `Unauthorized`, `Forbidden`, `NotFound`, `Conflict`, `Rejected(reason)`, `ServerError`, `RateLimited`, `Malformed`. Failures are values (`DomainResult.Failure`), never thrown. |
| Application | `MutationResult`, `RevokeKeyResult`, `InvalidInput` | see above; `RevokeKeyResult.CurrentKeyUnknown` means this phone does not know which server key is its own, so it will not revoke any (nothing is sent); `InvalidInput` is `NoteRequired`, `BodyRequired`, `WrongStatus`, `UnknownOption`, `SnoozeNotInFuture`, `UndoWindowClosed`, `TitleRequired`, `NotAddressedToYou`, `Answer`, `Input`, `Amount` |
| Application | `BaseUriError`, `ProbeRefusal`, `InstanceNameIssue` | address, server-mode and name problems when adding or editing a factory |
| Presentation | `ErrorKey` | `Unreachable`, `CertificateInvalid`, `Timeout`, `SignedOut` (from `Unauthorized`), `NoPermission` (from `Forbidden`), `NotFound`, `Conflict`, `Rejected` (with the server's reason as `detail`), `ServerError`, `RateLimited`, `Malformed`, and the sign-in keys `InvalidKey`, `StorageFailed`, `CurrentKeyUnknown` |
| Presentation | `DeviceAuthIssue`, `AddressIssue` (includes `InvalidPath`), `ActionsDisabledReason` (`Offline`, `ViewerRole`, `OwnerRole`) | |

A 401 from any company-scoped call marks the factory Signed out through `SignedOutAware<X>Gateway` decorators bound by the umbrella (ADR-031). **Phase 1b adapter obligations:** map HTTP 401 to `DomainError.Unauthorized` (so the decorators see it), HTTP 429 to `DomainError.RateLimited` (pairing tolerates it while waiting, ADR-034's budget applies to reads), and TLS or name-verification failures to `DomainError.CertificateInvalid` (plain connection failures stay `Unreachable`).

A 403 means the role may have changed: after a mutation *or a read* answers `Forbidden`, the holder resolves the role once more (no loop, the mutation is not repeated) and re-renders with the new role's actions; any other error leaves the role alone.

`BaseUri` (FR-003) also refuses a path with whitespace, control characters, a backslash, an empty or `.`/`..` segment (also percent-encoded) or a malformed `%` escape (`BaseUriError.InvalidPath`), and a host with an empty or over-long label, a leading or trailing hyphen, a trailing dot, non-ASCII characters (paste punycode), a non-canonical IPv4 literal or a malformed bracketed IPv6 literal (`InvalidHost`).

All text a screen shows is a key; the platform UI owns the wording and the localisation. Raw server JSON never crosses the adapter boundary.

## Refresh policy

`RefreshVisibleScreen(connectivity, lifecycle, liveEvents)(scope, target)` returns a `RefreshSession` (ADR-027). The platform passes a holder's `refreshTarget` to `graph.refresh.start` when the screen appears and calls `session.stop()` when it goes away.

- Active only when the phone is online **and** the app is in the foreground.
- While active, refreshes every `target.refreshIntervalSeconds`. The interval is part of each holder's state, so it can change: 15 s by default; 5 s on the Run viewer while the run is live and on Agent detail while the agent has a run; 0 (no timer) for a finished run, a final Decision, a resolved Approval and a closed Budget incident.
- Refreshes at once when the app returns to the foreground or the phone comes back online (not at start: the holder has just loaded).
- Refreshes when a `LiveEvent` maps, through `InvalidationRules`, to something the target says it is affected by (`agent.status`, `heartbeat.run.*`, `activity.logged`), at most once a second, newer events replacing older ones. There is no real `LiveEvents` adapter yet, so this path only runs with a fake.
- Timers use coroutine `delay`, not the `Clock` port, so tests run in virtual time.
- The state holders ignore a `Refresh` while one is in flight, so the policy cannot double-load.
- Nothing refreshes in the background or offline. The shell reports the background with one call, `graph.lifecycle.setForeground(Boolean)`, which also tells the app lock (`WentToBackground` / `CameToForeground`) of the holder made by `createAppStateHolder()`. A shell that never created one has no lock to tell.

The Inbox is cache-first (ADR-017): `LoadInbox` emits the cached first page (marked `isFromCache`), then the network result; the label "Updated" is the server's `generatedAt`. Only the first page is cached.

## Demo data and fakes

`shared/testing/fakes` has a `FakeX` for every port (23; `FakeAppLifecycle` lives next to `FakeLiveEvents`). The graph itself uses `AppLifecycleBridge` from the umbrella as its `AppLifecycle`. Fakes are behaviourally honest where the server's behaviour matters (cursor rules, the approval state machine, idempotency-key replay, per-address health, key state) and expose call counters (`count("decide")`), scripted failures (`failures["get"] = DomainError.Timeout`) and, for the platform fakes, a switch (`FakeBiometrics.result`, `FakeConnectivity.setOnline`).

`shared/testing/demo` (`DemoIds`, `DemoFactories`, `DemoInbox`, `DemoAttentionDetails`, `DemoWork`, `DemoAgents`) seeds the fakes of `createDemoGraph()` with the facts of the design mocks:

- Device "Alex's iPhone"; person Alex Rivera (Owner of Acme Labs, Operator of Side Project Co).
- Factories: Home factory `https://paperclip.tail1234.ts.net` (signed in with a fake `pcp_board_TESTONLY…` key, 23 days left), Cloud factory `https://paperclip.example.com` (signed out, expired key), Test factory `http://localhost:3100` (signed out, no key). Ids in `DemoIds`: `demo-home`, `demo-cloud`, `demo-test`, `company-acme`, `company-side`.
- Acme Labs: nine Inbox cards (badge 3, three Decide-now cards), six agents, PAP-1182 and PAP-1204 with runs and files, a $1,240 of $2,000 factory home.

The tests in `DemoGraphTest.kt` and `DemoGraphDeviceTest.kt` (umbrella) drive every screen through the graph and assert those facts; `DemoGraphInteropTests.swift` does the same from Swift.

## Contract suites

Every port except `Connectivity` and `AppLifecycle` (a `StateFlow` already guarantees what they would assert; ADR-036) has a contract suite in `shared/testing/contract` (26 suites): a plain abstract class with abstract hooks (`create()`, and scripting hooks such as `willAnswer`) and `suspend fun runAll()` that uses `check` (ADR-014). The fake passes it through a thin `@Test` subclass in `shared/testing/src/commonTest` (26 subclasses, one per suite, in `FakeContractTest.kt` and `FakeGatewayContractTest.kt`; the suites assert obligations a real adapter could break, ADR-036). Phase 1b adapters subclass the same suites in their own test source sets. Suites use only port calls and handles the subclass guarantees, never a fake's `calls` or `store` (ADR-028). The shared suites assume stub-shaped data (fixed ids, a closed issue, a scripted expiry), so the live `contractTest` does not reuse them wholesale; it has its own tests on seeded data (see the next section) and `scripts/contract-smoke.sh` stays the first check in `contract.yml`.

## Tests and coverage

Numbers from the last local run (2026-11-02, after the independent re-review fixes; JUnit XML under `shared/*/build/test-results`):

| Where | Tests (JVM) |
|---|---|
| `shared/domain` | 143 |
| `shared/application` | 303 |
| `shared/presentation` | 326 (322 on Android host and iOS simulator: `AtomicGuardsTest`'s 4 stress tests are JVM only) |
| `shared/testing` | 48 |
| `shared/umbrella` | 32 |
| Konsist (`konsistTest`) | 37 in 10 files |
| Swift (`iosApp/Tests`) | 33 Swift Testing cases in `iosAppTests` (counted from source) and 1 XCUITest |

`commonTest` runs on the JVM, the Android host (`testAndroidHostTest`) and the iOS simulator (`iosSimulatorArm64Test`) under `./gradlew check`.

Kover coverage, regenerated with `rm -rf build/reports/kover && ./gradlew koverXmlReportCore koverXmlReportPresentation -q` and read from `build/reports/kover/*.xml`:

| Gate | Task | Line coverage | Threshold |
|---|---|---|---|
| Domain + application, one aggregate | `koverVerifyCore` | 97.98% (2568 covered, 53 missed) | 90% |
| Presentation | `koverVerifyPresentation` | 98.02% (4217 covered, 85 missed) | 90% |

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

1. `./scripts/build-kit.sh [debug|release]` runs Gradle to build `PaperclipKit.xcframework`, checks that the output exists, copies it next to the old one and swaps it into `iosApp/Frameworks/` (git-ignored) only when the copy is complete, so a failed build never leaves you without a framework; it records `debug` or `release` in `PaperclipKit.configuration` beside it. `./scripts/ios-test.sh` stops with "run scripts/build-kit.sh first" when the framework is missing. `scripts/test-build-kit.sh` checks these failure modes without Gradle or Xcode. SKIE turns Kotlin `Flow` into Swift `AsyncSequence` and sealed types into Swift enums.
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
| `contract.yml` | ubuntu | starts the pinned Paperclip in Docker and runs `scripts/contract-smoke.sh` (the anonymous `/api/health` fields and the pairing challenge create and poll shapes); nightly and on manual dispatch the same script runs against `latest`, in a job with no `continue-on-error`, so API drift turns it red. After the smoke script the job creates a bootstrap invite in the container, runs `./gradlew :shared:infrastructure:api:contractTest` (the real adapters against the live server) and uploads its report. Drift detection on `/api/openapi.json` is not done: the pinned server source has no such route. |
| `security.yml` | ubuntu | gitleaks (built from `docker/gitleaks/Dockerfile`, v8.30.1 pinned by digest) on the full history |

The files are `.github/workflows/{shared,ios,contract,security}.yml`. AGENTS.md also lists an `android.yml`; it does not exist because there is no `androidApp/` yet. Every action is pinned to a full commit SHA with its version in a comment, and the gitleaks image to a digest (`CiPinningRulesTest`). Deferred: pinning the `brew install` tools and the Xcode version (brew cannot pin by hash), narrowing the gitleaks `TESTONLY` allowlist, and a Gradle dependency-vulnerability job. Dependabot (`.github/dependabot.yml`) proposes weekly grouped updates for Gradle dependencies, GitHub Actions and the gitleaks Dockerfile. Dependabot alerts and dependency review need the repository's Dependency graph switched on in GitHub settings, which is not done yet. The workflows run on pull requests (draft PR #5 for this branch); `contract.yml` only when the paths it lists change, and on a nightly schedule.

## The Paperclip test server

`docker/paperclip-test/compose.yml` runs `ghcr.io/paperclipai/paperclip:<paperclip-server>` with `PAPERCLIP_DEPLOYMENT_MODE=authenticated`, exposure `private`, no LLM keys, on host port 3199. `scripts/paperclip-test-server.sh` reads the version from the catalog, starts it, waits for health and checks the mode. The app refuses `local_trusted` servers, so a developer's usual local Paperclip cannot be used for contract tests (ADR-008).

## Known server facts (Paperclip 2026.1023.0)

- Board keys are rejected on the live-events WebSocket (`/api/companies/{id}/events/ws` accepts agent keys and browser sessions), so the app polls (ADR-004).
- `/api/health` returns `deploymentMode` and `deploymentExposure` to anyone; the server `version` is in the response to signed-in callers and in the 503 `unhealthy` response (`health.ts`), but not in the anonymous 200 response, so the app cannot show the server version before pairing (PRD FR-005). The `!db` branch omits the exposure and the 503 branch omits the mode, so the probe fails closed.
- The board API token is returned when a pairing challenge is created, before the person approves; the app holds it in memory and stores it only on approval (ADR-012). The company role comes from `GET /api/cli-auth/me` `memberships[].membershipRole`; `viewer` is enforced by the server (ADR-011).
- A fresh authenticated server reports `bootstrapStatus: bootstrap_pending` (no users yet). The contract test completes the bootstrap invite to create the first board user before it pairs (see the contract test section).
- `inlineResolvable` is true for eight source kinds but the app has inline actions only for approvals and hard-stop budget alerts; `subject.href` is a relative path and can be null; `POST /decisions/{id}/decide` runs the effects inside the request; decision inputs belong to the decision; there is no single-incident budget endpoint; approving or rejecting an already resolved approval to the other status is a 422, not a 409. Evidence by file and line is in [`research.md`](../specs/archive/261101-phase-1a-shared-contracts/research.md).

## Behaviour added by the Phase 1a review fixes

Decisions are in ADR-030 to ADR-037; the fix spec is `specs/archive/261101-phase-1a-shared-contracts-auto-review/`.

| Topic | Behaviour |
|---|---|
| Probe result bound to its address | `ProbeResult.Accepted/Refused/Failed` carry the `baseUri` they were taken for; `AddInstance` and `UpdateInstance` answer `ProbeRequired` unless an accepted probe of exactly that address is passed; holders drop a late result for another address (ADR-030). The same address may be added twice. |
| Credentials follow the host | A key is validated against the snapshot host before it is stored; an address change deletes the credentials of the old address; a per-factory lock serialises these (ADR-033). |
| Signed-out marking | Decorators over the nine company-scoped gateways mark Signed out on any `Unauthorized` (ADR-031). |
| Unknown outcomes | `MutationResult.Unknown`, never a guessed success (ADR-032). |
| Read retries | Shared 3.5 s budget, none when offline, none for `CertificateInvalid` (ADR-034). |
| Pairing wait | Poll interval clamped to 250 ms to 10 s and the whole wait to 15 minutes; `RateLimited` is tolerated while waiting. |
| Sign out | Local deletion always runs (cancel-safe); `SignOutEverywhere` continues past a failing factory and reports `signOutFailures`. |
| Concurrency | State holders claim their in-flight flags atomically; a late read never overwrites a finished action (version counters); the Decision screen has one execution poller. |
| Role gating | Every flag goes through `Permissions` and `DeviceAuthPolicy` (`RoleGating.kt`); a 403 on a read or a mutation re-resolves the role once. |
| CI | `contract-smoke.sh`, SHA-pinned actions and a digest-pinned scanner (see CI). |

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

Swift surface (deferred, with reasons, in the review fix spec): nullable numbers arrive boxed (29 `Int?` or `Long?` properties in the presentation sources at the last count; no cheap SKIE option); every holder and state exposes a public initialiser, so Swift could build one outside the graph (the graph is the supported path; intents must stay constructible from Swift); a data class `copy` is exported as `doCopy`; a few state types expose domain enums (`IdlePeriod`, `Exposure`, `ProbeRefusal`, `InstanceNameIssue`) instead of UI twins (no Swift screen consumes them yet); `ErrorUi.detail` carries the server's own reason string, which cannot be localised. The `description` collision is fixed (`summary`).

Process:

- The infrastructure Konsist rule scans every `src/<set>Main` of every module; it checks nothing on the real tree while the infrastructure modules have no sources, and a fixture test proves it reports a violation once they have.
- TDD evidence is self-reported: commit history cannot show a red run, so red evidence is recorded in the commit bodies and in the review fix spec's implementation notes.
- Slice 1a-1 application and presentation code was written in the same pass as its tests (domain was strictly test first); recorded as a process deviation in the spec's implementation notes.
- The first-run and edit probe and pair job fields are plain (non-atomic) fields written from the UI thread; a lost write is bounded by the address binding of the probe result (ADR-030).
- "Continue to pairing" in the iPhone shell is enabled for a valid address and does nothing; the shell is a walking skeleton and this is stated in the user docs.

## Persistence (Phase 1b-2)

- Schema: `src/commonMain/sqldelight/.../db/*.sq` and `1.sqm` (version 2). `1.db` and `2.db` are committed snapshots checked by `verifyCommonMainPaperclipDatabaseMigration` in `check`. A change to a `.sq` file needs a new `N.sqm`, a regenerated snapshot (`generateCommonMainPaperclipDatabaseSchema`) and a migration test.
- One database per app; the three SQL adapters share one serial dispatcher (ADR-043). `PaperclipPersistence.recreated` tells whether an unusable file was replaced by an empty one (ADR-042).
- Files: iOS `Application Support/PaperclipData/paperclip.db` (folder and files excluded from backup), Android `noBackupFilesDir/paperclip.db`, JVM a given file or memory.
- Tests: the existing `InstanceRepository`, `AppSettingsStore` and `AttentionCache` contract suites run against the SQL adapters on the JVM, the Android host test (JDBC driver) and the iOS simulator (native driver, temp file), plus tests for mapping, migration, recovery, concurrency, secrets and (iOS) backup exclusion. The Android driver factory is compile-checked only.


## Device services (Phase 1b-3)

- **Keychain** (`securestore`, iosMain): generic password items, service = a fixed id, account = `instance/{id}/{name}`, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, `kSecAttrSynchronizable = false` on every query. `requireUserPresence` stores behind `.biometryCurrentSet`, else `.userPresence`, else the write fails (never open). A protected write adds a `{account}#protected` marker so a vanished protected item reads as `Invalidated`. Read failures other than not found are `Denied`; writes `Stored`/`Failed`; refused deletes throw. `deleteNamespace` lists accounts by attributes only. ADR-051. `KeychainWriter` holds the write side, `KeychainBackend` is the seam tests script, `SecurityKeychain` is the real backend.
- **Testing**: the store's decisions and the shared `SecretStoreContract` run in `iosSimulatorArm64Test` on a scripted backend. The real Keychain needs entitlements, so `iosApp/Tests/iosAppTests/KeychainSecretStoreTests.swift` runs inside the app test host through `PlatformServices` (umbrella `iosMain`), which also runs the contract on the real Keychain. `scripts/ios-test.sh` signs ad hoc. ADR-050.
- **Biometrics**: `LocalAuthenticationBiometrics` over an internal `DeviceAuthenticator` (`LAContextAuthenticator`: new `LAContext` per prompt, `LAPolicyDeviceOwnerAuthentication`). ADR-052 has the code mapping; no passcode means Unavailable (fail closed).
- **Connectivity**: `NwConnectivity` over `PathSource` (`nw_path_monitor` on its own serial queue), online until the first path, `close()` cancels. The foreground flag is still the umbrella's `AppLifecycleBridge`, driven by the app.
- **Android and JVM**: `UnavailableSecretStore`, `UnavailableBiometrics` (never approve, never store), `OptimisticConnectivity`. System clock, UUID ids and the device zone are common code. ADR-053.
- Konsist (`InfrastructureDeviceRulesTest`): only domain/stdlib/platform imports, no database/file/user-defaults API in `securestore`, no `Secret` in `platform`, no printing, fixed public surface.

## Attention, decision, approval and budget gateways (Phase 1b-4a)

- **Shape.** `AttentionGatewayAdapter`, `DecisionGatewayAdapter`, `ApprovalGatewayAdapter`, `BudgetGatewayAdapter` in `shared/infrastructure/api`. Each takes the `PaperclipHttpClient`, the `InstanceRepository` and the `SecretStore`; `CompanyCalls` reads the factory's address, pins and key from the scope's instance for each call (the key is wiped after it), so a call can only reach the instance that owns the scope. The company id goes in the path for feed, dismissals, triage and budgets; decisions and approvals are addressed by id only and are checked against the scope's company (ADR-057).
- **DTOs** (`dto/`, all internal): every property optional or defaulted, `ignoreUnknownKeys`; polymorphic parts (details, effects, payloads, metadata) are `JsonObject` read by `JsonReading.kt` helpers (ADR-055). Mappers turn them into domain types or null, which the adapter reports as `Malformed`. Money is parsed from the number's literal and rounded up to whole cents (`ceilToCents`, ADR-054).
- **Reads send once; writes send once.** Retrying a read is the use case's job. A write that needs a precondition (resolve a budget incident, approve, decide, dismiss) does one preceding read of the same resource, then one POST (ADR-056, ADR-057). `decide` always carries its idempotency key.
- **Calls.** Feed `GET companies/{c}/attention?sort=decide&limit&cursor`; dismiss/snooze `POST companies/{c}/inbox-dismissals`; restore `DELETE companies/{c}/inbox-dismissals/{key}` (204); decide-by `PUT companies/{c}/decision-triage/{kind}/{id}` with `{"decideBy":null}` to clear; `GET decisions/{id}`, `POST decisions/{id}/decide|dismiss`; `GET approvals/{id}[/issues|/comments]`, `POST approvals/{id}/approve|reject|request-revision|comments`; `GET companies/{c}/budgets/overview`, `POST companies/{c}/budget-incidents/{id}/resolve`.
- **Tests.** `WorkWorld` (two factories, key roles owner of Acme and viewer of Globex) over `StubPaperclipServer`, which delegates company-scoped paths to per-domain stubs (`StubAttention`, `StubDecisions`, `StubApprovals`, `StubBudgets`) that hold state and follow the server rules read from source. The shared contract suites for the four ports run unchanged against the real adapters on JVM, Android host and iOS simulator. Fixtures (`FeedFixtures`, `DecisionFixtures`, `ApprovalFixtures`, `BudgetFixtures`) are written from server source and are not recorded; each states the server file and line it follows.

## Dashboard, agent, run, issue and interaction gateways (Phase 1b-4b)

- **Source of truth.** The pinned release's compiled dist (`@paperclipai/{server,shared,db}/dist`), not the newer dev tree 1b-4a used. ADR-060; the corrections are in `specs/261102-phase-1b-adapters/research.md`. The stub follows the server's quirks (cancel of an ended run answers 200, the log omits `nextOffset` on its last chunk, comments are newest first, create may be answered `deduplicated`), so the adapters' compensations are what make the shared suites pass.
- **Shape.** `DashboardGatewayAdapter`, `AgentGatewayAdapter`, `RunGatewayAdapter`, `IssueGatewayAdapter`, `InteractionGatewayAdapter` in `shared/infrastructure/api`, on the same `CompanyCalls`. `OwnedCalls.kt` (`requireIssue`, `provingOwnership`) and `ListReads.kt` (`allOrNone`, `ofCompany`) are the shared ownership helpers; `IssueRequests` and `InteractionRequests` build the request bodies key by key (explicit nulls matter to the server).
- **Ownership (ADR-061).** Agents, runs, issues, attachments and interaction answers have no company in the path. Non-uuid id: NotFound, no request. Single resource: the reply's `companyId` must be the scope's. Mutation: a read of the resource first, then one write. List of rows that name their company: checked per row; an empty one asks the issue. `issues/{id}/runs`: the issue is read first.
- **Calls.** Dashboard `GET companies/{c}/dashboard`, `.../costs/window-spend`. Agents `GET companies/{c}/agents` (no query), `GET agents/{id}`, `POST agents/{id}/pause|resume|heartbeat/invoke`, `PATCH agents/{id}/budgets`. Runs `GET heartbeat-runs/{id}`, `.../events?afterSeq&limit=500`, `.../log?offset&limitBytes=65536`, `POST .../cancel`, `GET issues/{id}/runs`, `GET companies/{c}/heartbeat-runs?agentId&limit=50`, `GET companies/{c}/live-runs`. Issues `GET companies/{c}/issues?view=compact&limit&offset[&status|attention|assigneeUserId=me|q|parentId]`, `GET issues/{id}`, `GET|POST issues/{id}/comments` (`order=asc`), `PATCH issues/{id}`, `POST companies/{c}/issues` (`allowDuplicate: true`), `GET issues/{id}/attachments`, and the adapter-only `GET attachments/{id}/content` with `Range`. Interactions `GET issues/{id}/interactions`, `POST .../interactions/{i}/accept|reject|respond|verdicts|skip`.
- **Runs.** The log offset is in bytes; the adapter computes the next offset on the last chunk (ADR-062). Cancel reads first and treats an ended run as a Conflict.
- **Attachments.** `IssueGatewayAdapter.attachmentContent` is public but not on a port (ADR-064); the client's `sendBytes` keeps the body as bytes.
- **Interactions.** Five kinds are answerable; the rest are `UnsupportedInteraction` and never sent anything (ADR-065).
- **Tests.** Stubs per domain extend `StubPaperclipServer` through `StubWork`: `StubDashboard`, `StubAgents`, `StubRuns` + `StubRunHandlers`, `StubIssues` + `StubIssueList` + `StubThread`, `StubInteractions`; fixtures `DashboardFixtures`, `AgentRowFixtures`, `RunFixtures`, `IssueRowFixtures`, `InteractionFixtures`, each naming the dist file it follows. The existing shared suites (`DashboardContract`, `AgentContract`, `RunReadContract`, `RunWriteContract`, `IssueReadContract`, `IssueWriteContract`, `InteractionContract`) run unchanged through thin `*GatewayContractTest` subclasses on JVM, Android host and iOS simulator. `CompanyScopedAuditTest` calls every method of every company gateway for two factories and against a foreign company.



## The live contract test (`contractTest`, AD-050, AD-051)

`shared/infrastructure/api` has a JVM-only `contract` compilation (`src/contractTest`, associated with `main`) and a Gradle task `contractTest`. It is **not** part of `check` or `allTests` (it needs a server); `check` only compiles it. It runs the production adapters (`HealthGatewayAdapter`, `AuthGatewayAdapter`, the company gateways) on the real OkHttp engine with a test-only in-memory `SecretStore`, and `allowInsecureLocal = true` for `http://localhost:3199` (`127.0.0.1` is not on the allow-list; the test rewrites it).

Run it locally against the pinned container:

```bash
./scripts/paperclip-test-server.sh up
export PAPERCLIP_CONTRACT_BOOTSTRAP_INVITE="$(./scripts/paperclip-test-server.sh invite)"
PAPERCLIP_CONTRACT_BASE_URL=http://localhost:3199 ./gradlew :shared:infrastructure:api:contractTest
./scripts/paperclip-test-server.sh down
```

Without `PAPERCLIP_CONTRACT_BASE_URL` the live tests are skipped with a message (CI sets `PAPERCLIP_CONTRACT_REQUIRED=true`, which makes a missing server a failure). What the run does: `ServerBootstrap` signs up the fixed test user and accepts the bootstrap invite (idempotent: an existing user signs in, an already bootstrapped server skips the invite); `Pairing` creates a challenge through the adapter, approves it with the session cookie and polls to approved; a company, issues, approvals and an agent are seeded with the paired key; the tests read through every gateway, mutate each seeded object once and compare the error shapes. At the end the run prints, and writes to `shared/infrastructure/api/build/reports/contract/live-operations.txt`, which port operations were verified live and which are covered only by MockEngine. Decisions, interaction answers and budget incidents cannot be created with a board key and stay MockEngine-only (research C7). ADR-071 to ADR-073 explain the choices.
