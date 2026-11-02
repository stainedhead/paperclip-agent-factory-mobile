# Usage examples

Working snippets for the shared core. Each Kotlin snippet was compiled and run as a temporary test in `shared/umbrella/src/commonTest` against commit `26f4e1c` (Kotlin 2.4.21, JVM target, 8 tests assembled from the blocks below, all passing); the temporary files were then deleted. To try one, paste it into a test class in that source set, add the imports shown, and run `./gradlew :shared:umbrella:jvmTest --tests '*YourTest*'`. The Swift snippets are verbatim excerpts of tests that pass under `./scripts/ios-test.sh`; they are cited by file.

Build errors to expect: warnings are errors in this repository (for example a redundant cast), and test names must not contain commas on Kotlin/Native.

## 1. Create a graph

```kotlin
val demo = PaperclipCore.createDemoGraph()                          // fakes seeded with the design mocks' data
val blank = PaperclipCore.createGraph(allowInsecureLocal = true)    // empty fakes; http://localhost allowed (debug builds)
```

Neither contacts a server. Both are `com.stainedhead.paperclipmobile.umbrella.PaperclipGraph`s.

## 2. Drive a state holder and collect its state (Kotlin)

State holders run on `Dispatchers.Default`, not on the test dispatcher. Under `runTest`, collect on `Dispatchers.Default` with a real-time timeout, or the test will hang or flake.

```kotlin
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(10.seconds) { first(predicate) } }

class InboxExample {
    @Test
    fun driveInbox() =
        runTest {
            val graph = PaperclipCore.createDemoGraph()
            val inbox = graph.attention.createInboxStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)

            val loaded = inbox.state.await { it.phase == InboxPhase.Content }
            loaded.badgeCount shouldBe 3          // the server's deskBadgeCount
            loaded.totalCount shouldBe 9
            // groups: DecideNow 3, ThisWeek 2, Whenever 4

            inbox.onIntent(InboxIntent.FilterSelected(SourceKindUi.Decision))
            inbox.state.await { it.selectedFilter == SourceKindUi.Decision }
                .sections.flatMap { it.items }.map { it.kind } shouldBe List(3) { SourceKindUi.Decision }

            val first = loaded.sections.flatMap { it.items }.first()
            inbox.onIntent(InboxIntent.DismissTapped(first.id))
            inbox.state.await { it.undo != null }.undo?.itemId shouldBe first.id   // five-second undo offer

            inbox.close()                                                           // always, when the screen goes away
        }
}
```

## 3. The same from Swift (SKIE)

Verbatim from `iosApp/Tests/iosAppTests/DemoGraphInteropTests.swift`, case `aStateFlowIsAnAsyncSequence` (a `StateFlow` is an `AsyncSequence`; `.value` gives the current state without waiting):

```swift
@Test func aStateFlowIsAnAsyncSequence() async {
    let factory = graph.factory.createFactoryHomeStateHolder(instanceId: home, companyId: acme)
    let reader = Task { @MainActor in
        for await state in factory.state where state.phase == .content {
            return true
        }
        return false
    }
    let timeout = Task { @MainActor in
        try? await Task.sleep(for: .seconds(10))
        reader.cancel()
    }
    #expect(await reader.value)
    timeout.cancel()
    factory.close()
}
```

Sending an intent and reading boxed numbers, from `DemoAttentionInteropTests` and `DemoFactoriesInteropTests` in the same file:

```swift
inbox.onIntent(intent: InboxIntentFilterSelected(kind: SourceKindUi.decision))
#expect(await eventually { inbox.state.value.selectedFilter == .decision })
// ...
#expect(rows[0].expiry.daysLeft?.intValue == 23)     // nullable numbers arrive boxed
```

A SwiftUI model that mirrors a holder (the shipped one): `iosApp/Sources/App/FirstRunModel.swift`.

## 4. Add a factory on fakes

Through the first-run state holder (the address rules and the server check run in the shared core):

```kotlin
import com.stainedhead.paperclipmobile.presentation.AddressIssue
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.ProbeStatus

val graph = PaperclipCore.createGraph(allowInsecureLocal = false)
val firstRun = graph.createFirstRunStateHolder()

firstRun.onIntent(FirstRunIntent.AddressChanged("http://paperclip.example.com"))
firstRun.state.await { it.issue != null }.issue shouldBe AddressIssue.MustUseHttps

firstRun.onIntent(FirstRunIntent.AddressChanged("HTTPS://Paperclip.Example.com/"))
firstRun.state.await { it.canContinue }.normalizedAddress shouldBe "https://paperclip.example.com"

firstRun.onIntent(FirstRunIntent.NameChanged("Home factory"))
firstRun.onIntent(FirstRunIntent.ProbeRequested)               // the fake health gateway says: authenticated, private, ok
firstRun.state.await { it.probe == ProbeStatus.Accepted }
firstRun.onIntent(FirstRunIntent.ContinueRequested)            // saves the factory, moves on to pairing
firstRun.state.await { it.step == FirstRunStep.Pair }
firstRun.close()
```

Or at use-case level. `AddInstance` needs a probe result taken for exactly that address (a result for another address is refused with `ProbeRequired`), the factory starts signed out, and the same rules refuse a duplicate name and a `local_trusted` server:

```kotlin
import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.application.instance.AddInstance
import com.stainedhead.paperclipmobile.application.instance.AddInstanceResult
import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.application.instance.ProbeResult
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository

fun probed(address: String, mode: DeploymentMode = DeploymentMode.Authenticated) =
    ProbeResult.Accepted(
        (BaseUri.parse(address) as BaseUriResult.Valid).uri,                       // the address the probe was taken for
        FakeHealthGateway.healthy(mode = mode),                                    // authenticated, private, ok
    )

val add = AddInstance(FakeInstanceRepository(), FakeClock(), FakeIdGenerator("factory-"), ValidateInstanceAddress(false))

val added = add("Home factory", "https://paperclip.example.com/", probed("https://paperclip.example.com"))
added.shouldBeInstanceOf<AddInstanceResult.Added>()
added.instance.baseUri.toString() shouldBe "https://paperclip.example.com"       // trailing slash removed
added.instance.status                                                            // InstanceStatus.SignedOut

add("home factory", "https://other.example.com", probed("https://other.example.com")) shouldBe
    AddInstanceResult.InvalidName(InstanceNameIssue.Taken)                       // names are unique ignoring case
add("Other", "https://other.example.com", probed("https://other.example.com", DeploymentMode.LocalTrusted)) shouldBe
    AddInstanceResult.Refused(ProbeRefusal.LocalTrusted)                         // local_trusted is always refused
add("Third", "https://third.example.com", probed("https://other.example.com")) shouldBe
    AddInstanceResult.ProbeRequired                                              // the probe was for another address
```

## 5. Decide a decision: device authentication cancelled versus approved

The graph hides its fakes (`PaperclipGraph.resolve` is internal), so to script the biometric prompt build the use case from fakes. `AttentionFixtures.decision()` has three options; `"cancel"` is destructive. `DecideDecision` takes the decision's id and reads the decision itself before asking the device, so the gateway fake must hold it (it does by default); a decision that is no longer open comes back as `Reconciled` with no call.

```kotlin
import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.decision.DecideDecision
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator

@Test
fun decideWithDeviceAuth() =
    runTest {
        val gateway = FakeDecisionGateway()
        val biometrics = FakeBiometrics(BiometricResult.Cancelled)
        val decide = DecideDecision(gateway, DeviceAuthGuard(biometrics), FakeIdGenerator("key-"))
        val context = CompanyContext(CompanyScope(InstanceId("home"), CompanyId("acme")), Role.Operator)
        val decision = AttentionFixtures.decision()                          // the fake's store holds it; the use case reads it by id

        // Cancelled: the use case stops before the gateway.
        decide(context, decision.id, "cancel", emptyMap()) shouldBe MutationResult.NotAuthorized(BiometricResult.Cancelled)
        gateway.count("decide") shouldBe 0

        // Approved: exactly one gateway call, carrying an idempotency key.
        biometrics.result = BiometricResult.Approved
        val done = decide(context, decision.id, "cancel", emptyMap())
        done.shouldBeInstanceOf<MutationResult.Done<*>>()
        gateway.count("decide") shouldBe 1
        biometrics.calls shouldBe 2                  // one prompt per attempt

        // A viewer is denied before anything else, with no prompt and no call.
        val viewer = CompanyContext(context.scope, Role.Viewer)
        decide(viewer, decision.id, "ship", emptyMap()) shouldBe MutationResult.Denied
    }
```

In the app this is what `DecisionStateHolder` does for `ConfirmTapped`; the `DeviceAuthIssue` (`Cancelled`, `Failed`, `LockedOut`, `Unavailable`) reaches the screen state.

## 6. Start and stop the refresh policy

Through the graph (the path an app takes), using a holder's `refreshTarget`:

```kotlin
val graph = PaperclipCore.createDemoGraph()
val agents = graph.agents.createAgentsStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)
val session = graph.refresh.start(agents.refreshTarget)   // screen appeared

graph.lifecycle.setForeground(false)                       // the shell: scene went to the background, refreshing pauses
graph.lifecycle.setForeground(true)                        // and resumes with an immediate refresh

session.stop()                                             // screen went away
agents.close()
```

From Swift (verbatim, `DemoFlowAndRefreshInteropTests`):

```swift
let session = graph.refresh.start(target: agents.refreshTarget)
session.stop()
agents.close()
// ...
graph.lifecycle.setForeground(isForeground: false)
graph.lifecycle.setForeground(isForeground: true)
```

One call per scene change does both jobs: it pauses or resumes the refresh policy and, for the holder made by `graph.createAppStateHolder()`, tells the app lock (`WentToBackground` or `CameToForeground`). `enterBackground()` and `enterForeground()` are the same calls.

To see the timer itself, run the policy in virtual time with your own `RefreshTarget`. Refreshing every 15 s while active, stopping in the background, and refreshing at once on return:

```kotlin
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.application.refresh.RefreshVisibleScreen
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeLiveEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
@Test
fun refreshPolicyInVirtualTime() =
    runTest {
        val lifecycle = AppLifecycleBridge()                         // com.stainedhead.paperclipmobile.umbrella
        val policy = RefreshVisibleScreen(FakeConnectivity(), lifecycle, FakeLiveEvents())
        val target =
            object : RefreshTarget {
                var refreshes = 0
                override val refreshIntervalSeconds: Flow<Int> = MutableStateFlow(15)

                override fun refreshNow() { refreshes += 1 }

                override fun isAffectedBy(invalidation: Invalidation) = false
            }
        val session = policy(CoroutineScope(StandardTestDispatcher(testScheduler)), target)

        testScheduler.advanceTimeBy(46.seconds); testScheduler.runCurrent()
        target.refreshes shouldBe 3                                  // at 15 s, 30 s, 45 s; not at start

        lifecycle.setForeground(false)
        testScheduler.advanceTimeBy(60.seconds); testScheduler.runCurrent()
        target.refreshes shouldBe 3                                  // nothing in the background

        lifecycle.setForeground(true); testScheduler.runCurrent()
        target.refreshes shouldBe 4                                  // immediate refresh on return
        session.stop()
    }
```

## 7. Write a new fake and a contract suite for a port

The convention (ADR-014, ADR-028): the port is in domain, the fake in `shared/testing/fakes`, the contract suite in `shared/testing/contract` as a plain abstract class with `check` (no test framework: `shared/testing` ships inside the XCFramework), and a thin `@Test` subclass per implementation in that implementation's own test source set. A toy port, all four parts, compiled and run:

```kotlin
// 1. The port (real code: shared/domain/.../domain/port).
interface NoteStore {
    suspend fun put(id: String, text: String)

    suspend fun get(id: String): String?
}

// 2. The fake (shared/testing/.../fakes/FakeNoteStore.kt), with a call counter like the other fakes.
class FakeNoteStore : NoteStore {
    private val notes = mutableMapOf<String, String>()
    var puts = 0
        private set

    override suspend fun put(id: String, text: String) {
        puts += 1
        notes[id] = text
    }

    override suspend fun get(id: String): String? = notes[id]
}

// 3. The contract (shared/testing/.../contract/NoteStoreContract.kt): plain abstract class, plain `check`.
abstract class NoteStoreContract {
    abstract fun create(): NoteStore

    suspend fun runAll() {
        val store = create()
        check(store.get("a") == null) { "an unknown id has no note" }
        store.put("a", "first")
        store.put("a", "second")
        check(store.get("a") == "second") { "put replaces the note" }
    }
}

// 4. One thin subclass per implementation, in that implementation's own test source set.
class FakeNoteStoreContractTest : NoteStoreContract() {
    override fun create(): NoteStore = FakeNoteStore()

    @Test
    fun `the fake passes the note store contract`() = runTest { runAll() }
}
```

For a real port, the Konsist rules also require a class named `Fake<PortName>` in `shared/testing` and a file in `shared/testing/.../contract` that names the port in brackets in its KDoc (`[NoteStore]`). The helpers `mustSucceed`, `mustFailWith` and `mustBeRejected` in `ContractSupport.kt` are `internal` to `shared/testing`; use them in suites that live there. See [testing-guide.md](./testing-guide.md).
