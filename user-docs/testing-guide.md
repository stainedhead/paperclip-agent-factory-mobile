# Testing guide

How test-driven development, fakes, contract suites, Konsist architecture rules and Kover coverage gates fit together in this repository, and how to add a use case or a screen's state holder end to end. The rules are in [AGENTS.md](../AGENTS.md); the structure is in [technical-details.md](../docs/technical-details.md).

Last revised in the Phase 2i review-fix round (branch `feat/phase-2i-iphone-ui`); not re-checked line by line against the code. Paths below are relative to the repository root; `…` stands for `com/stainedhead/paperclipmobile`.

## The loop

Red, green, refactor is the only accepted workflow. Write the test, run it, see it fail for the right reason, write the minimum code, then clean up with the tests green. A bug fix starts with a failing regression test.

```bash
./gradlew :shared:application:jvmTest --tests '*DecideDecisionTest*'   # the fast loop: one module, JVM, one class
./gradlew spotlessApply                                                  # before every commit
./gradlew check lint                                                     # before every commit: everything below
```

`check` also runs `allTests`, so every `commonTest` test runs on the JVM, the Android host and the iOS simulator (Kotlin/Native). Two Kotlin/Native rules to remember: test names must not contain commas, and a build with any warning fails.

## How the pieces fit

| Piece | Where | What it guards |
|---|---|---|
| Unit tests | `shared/<module>/src/commonTest` (kotlin.test, Kotest `shouldBe`, Turbine, coroutines-test) | Behaviour of one layer, against fakes |
| Fakes | `shared/testing/src/commonMain/kotlin/…/testing/fakes` | A complete, behaviourally honest stand-in for each of the 23 ports; also what demo mode runs on |
| Contract suites | `shared/testing/src/commonMain/kotlin/…/testing/contract` | One suite for each port a real adapter could break (21 of 23; `Connectivity` and `AppLifecycle` are `StateFlow`s and exempt, ADR-036). The same assertions run against the fake now and against the real adapter in Phase 1b |
| Fake behaviour tests | `shared/testing/src/commonTest` | The fakes themselves (26 contract subclasses, one per suite, plus behaviour tests) |
| Demo graph tests | `shared/umbrella/src/commonTest` | Every screen's state holder, driven through `createDemoGraph()`, shows the design mocks' facts |
| Konsist rules | `shared/umbrella/src/konsistTest` | Layering, conventions, one fake and suite per port, device-auth set, requirement traceability |
| Kover gates | root `build.gradle.kts` | At least 90% line coverage: `koverVerifyCore` (domain + application) and `koverVerifyPresentation` |
| Swift tests | `iosApp/Tests/iosAppTests`, `iosAppUITests` | The Kotlin surface is usable from Swift 6; UI renders state |

Rules of thumb:

- **Test through the layer's own tools.** Domain: pure functions and tables. Application: a use case with fakes (`Env` in `shared/application/src/commonTest/…/application/Env.kt` builds every fake and shared collaborator; use `Env()` and pick what you need). Presentation: a holder with `StandardTestDispatcher(testScheduler)`, `advanceUntilIdle()`, and `Env` in `shared/presentation/src/commonTest`. Both `Env`s accept a clock tied to virtual time (`{ testScheduler.currentTime }`).
- **UI renders state; behaviour is tested in shared.** A SwiftUI or Compose test checks that state renders, never a rule.
- **No MockK in `commonTest`.** Prefer hand-written fakes; use Mokkery only when a fake would be more code than the test.
- **Assert zero calls.** Fakes count calls (`gateway.count("decide")`). A viewer, a missing note or a cancelled biometric prompt must leave the count at 0; a timeout must leave it at 1 (never retried).
- **Time only through `Clock`.** Use `FakeClock(elapsedMillis = { testScheduler.currentTime })` so `delay` and `now()` agree.
- **Secrets in fixtures** are obviously fake (`pcp_board_TESTONLY…`).
- **Tag tests with the requirement id** in the name (for example `FR-043 a plain option is decided once…`). `RequirementsTraceabilityTest` fails if a Phase 1a id is in no `@Test` function name of a `commonTest` source (a comment does not count).

## Add a use case, end to end

Example: a mutation `ArchiveThing`. Do each step test first.

1. **Domain.** If it is a new action, add it to `Action` (`shared/domain/src/commonMain/kotlin/…/domain/Action.kt`) with its scope (`Company` or `Local`), decide its role in `Permissions` (owner-only actions go in `ownerOnly`) and whether it is destructive (`DeviceAuthPolicy.destructive`). Test: `PermissionsTest` rows. Add any entity, value object or port method it needs. A new port method needs a `Fake` change and a contract-suite change in the same commit.
2. **Application test (red).** In `shared/application/src/commonTest/…/application/<package>/`, using `Env`. Write the cases in the fixed order of every mutation (ADR-019):
   - role not allowed: `MutationResult.Denied`, `count(...) == 0`, `env.biometrics.calls == 0`;
   - invalid input: `Invalid(...)`, zero calls, no prompt;
   - device authentication (if destructive): cancelled, failed, locked out, unavailable each give `NotAuthorized(result)` and zero gateway calls;
   - success: `Done`, exactly one gateway call;
   - `Timeout` and `Conflict`: one gateway call, then one re-read, result `Reconciled(error, current)`; any other failure: `Failed(error)`, no re-read.
3. **Application code (green).** One class, one `suspend operator fun invoke(context: CompanyContext, …): MutationResult<T>` (Konsist enforces both). Take the gateway port, and `DeviceAuthGuard` only if destructive; reads wrap the call in `retryingRead`. Use `failed(error) { gateway.get(...) }` for the re-read. If it takes `DeviceAuthGuard`, add it to the expected set in `DeviceAuthRulesTest` in the same commit and say why.
4. **Wire it.** Register it as `single { ArchiveThing(get(), get()) }` in the `useCaseModule` (`shared/umbrella/src/commonMain/kotlin/…/umbrella/Modules.kt`, `SliceTwoModules.kt` or `SliceThreeModules.kt`) and pass it to the state holder's use-case bundle.
5. **Gate it.** `./gradlew :shared:application:jvmTest`, then `./gradlew check lint` (Konsist, detekt and the 90% core gate must stay green and coverage must not drop).

Reads follow the same pattern without steps for permission and device authentication; return `DomainResult<T>`.

## Add a screen's state holder, end to end

1. **State, intents, use-case bundle.** In `shared/presentation/src/commonMain/kotlin/…/presentation/`: a `data class XState` with defaults (phase, data, `isOffline`, `actionsDisabledReason`, `updatedAtEpochMillis`, `error`, and `refreshIntervalSeconds` if it refreshes), a `sealed interface XIntent`, and a `data class XUseCases(...)`. Text is keys (enums), never prose; no generics, `Result`, or default arguments on anything Swift sees; no function or property named `new`, `alloc`, `copy`, `mutableCopy` or `init` prefixes.
2. **Test first.** In `shared/presentation/src/commonTest`, with `Env` and a `CompanyTarget`, as `FactoryHomeStateHolderTest` does: load, content, failure per `DomainError`, `ErrorKey` mapping, viewer role, offline (`env.connectivity.setOnline(false)`: actions disabled with a reason, last data kept), a second tap while busy is ignored, timeout is sent once.
3. **The holder.** A class `XStateHolder(useCases, connectivity, clock, scope: CoroutineScope, target: CompanyTarget)` exposing `val state: StateFlow<XState>`, `fun onIntent(intent: XIntent)` and `fun close()` (cancel the child scope). It imports use cases and domain, never a gateway. If it refreshes, expose `val refreshTarget: RefreshTarget = ScreenRefreshTarget(state, { it.refreshIntervalSeconds }, { onIntent(XIntent.Refresh) }, { invalidation -> … })`.
4. **Wire and expose it.** In the umbrella's `presentationModule`, add `factory { params -> XStateHolder(XUseCases(get(), …), get(), get(), params.get(), params.get<CompanyTarget>()) }`, and a `createXStateHolder(instanceId, companyId, …)` function on `PaperclipGraph` or one of its screen groups, which calls `koin.get { parametersOf(newScreenScope(), CompanyTarget(...)) }`.
5. **Demo data.** Seed the fakes the screen reads in `shared/testing/.../demo` and `DemoModules.kt`, and add a case to `DemoGraphTest.kt` (or `DemoGraphDeviceTest.kt`) asserting the mock's facts, plus a Swift case in `iosApp/Tests/iosAppTests/DemoGraphInteropTests.swift`.
6. **Gate it.** `./gradlew spotlessApply && ./gradlew check lint`, then `./scripts/build-kit.sh debug`, `swiftlint --strict iosApp`, `xcrun swift-format lint --recursive --strict iosApp/Sources iosApp/Tests iosApp/Packages/PaperclipKit/Package.swift`, `./scripts/ios-test.sh`.

## Add a port with its fake and contract suite

See the compiled walkthrough in [usage-examples.md](./usage-examples.md#7-write-a-new-fake-and-a-contract-suite-for-a-port). Summary of the Konsist expectations:

- the interface is in `shared/domain/.../domain/port` (this is what the rules scan);
- a class named exactly `Fake<PortName>` exists in `shared/testing`;
- a Kotlin file in a directory named `contract` under `shared/testing/src/commonMain` mentions `[PortName]` (KDoc link) in its suite;
- the contract is a plain abstract class with `abstract fun create(): Port` and `suspend fun runAll()` using `check`, because `shared/testing` ships in the XCFramework and must not depend on kotlin-test;
- the fake passes it through a thin subclass with one `@Test fun … = runTest { runAll() }`; add the subclass to `FakeContractTest.kt` or `FakeGatewayContractTest.kt`;
- contracts use only port calls and handles the subclass guarantees (never a fake's `calls` or `store`), and cite the research note they encode;
- when you add behaviour to a real adapter, add it to the fake and to the contract (ADR-028).

## Konsist rules: what they enforce, how to extend

The architecture tests are listed in [technical-details.md](../docs/technical-details.md#layering-rules-and-how-they-are-enforced). They live in `shared/umbrella/src/konsistTest/kotlin/…/architecture/` and run with `./gradlew :shared:umbrella:konsistTest`. Every rule scans real files and, where it can, asserts that it found some, so it does not pass by scanning nothing. The infrastructure rules scan the real adapter modules and each carries a planted-violation test (a temporary source tree with a `suspend fun`, a `const val` or a fake import) that proves the rule can fail. A new rule needs a negative case: a fixture or a deliberate break that makes it fail (the contract-per-port, `Secret.reveal`, traceability and CI pinning rules each have one). Write the rule, watch it fail, then fix the code. Never silence a rule inline; if a rule is wrong, change it in its own commit and explain why.

## Coverage gates

| Gate | Command | Measures |
|---|---|---|
| Core | `./gradlew koverVerifyCore` | one aggregate over `domain` and `application` |
| Presentation | `./gradlew koverVerifyPresentation` | `presentation` |

Both need 90% line coverage and both run in `check`. Do not reduce coverage when adding code. To see numbers: `rm -rf build/reports/kover && ./gradlew koverXmlReportCore koverXmlReportPresentation -q` and read `build/reports/kover/*.xml` (numbers for this branch: `docs/technical-details.md`). `testing`, `umbrella` and (later) infrastructure are outside the gates.

## Contract tests against a real server

`scripts/paperclip-test-server.sh up` starts the pinned Paperclip (authenticated mode, `http://localhost:3199`) in Docker, and `contract.yml` does the same in CI and then runs the live contract test. The adapters also run the shared port contract suites through a MockEngine stub server in `commonTest`. The live test is separate (it needs a server): after `up`, create the bootstrap invite and run it with

```bash
export PAPERCLIP_CONTRACT_BOOTSTRAP_INVITE="$(./scripts/paperclip-test-server.sh invite)"
PAPERCLIP_CONTRACT_I_KNOW_THIS_MUTATES=true PAPERCLIP_CONTRACT_BASE_URL=http://localhost:3199 ./gradlew :shared:infrastructure:api:contractTest
```

It signs up a test user, pairs through the real challenge flow, seeds a company, issues, approvals and an agent, exercises every gateway it can, and prints which operations were verified live and which only through MockEngine (`shared/infrastructure/api/build/reports/contract/live-operations.txt`). Paperclip's version pin is `paperclip-server` in the version catalog.
