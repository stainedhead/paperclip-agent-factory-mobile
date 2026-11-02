package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.presentation.AppStateHolder
import com.stainedhead.paperclipmobile.presentation.ApprovalStateHolder
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentStateHolder
import com.stainedhead.paperclipmobile.presentation.CompanyTarget
import com.stainedhead.paperclipmobile.presentation.DecisionStateHolder
import com.stainedhead.paperclipmobile.presentation.DecisionTarget
import com.stainedhead.paperclipmobile.presentation.EditInstanceStateHolder
import com.stainedhead.paperclipmobile.presentation.FactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.FirstRunStateHolder
import com.stainedhead.paperclipmobile.presentation.InboxStateHolder
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesStateHolder
import com.stainedhead.paperclipmobile.presentation.SettingsStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The composition root. Platform apps create one graph and ask it for screen state holders.
 *
 * Until the Phase 1b adapters exist the graph runs on the shared fakes (demo mode), so every screen can be built
 * without a server. Each `create...StateHolder` call returns a new holder that the screen owns; call `close()` on it
 * when the screen goes away.
 */
@OptIn(ExperimentalAtomicApi::class)
class PaperclipGraph internal constructor(
    private val koin: Koin,
    private val resources: AutoCloseable? = null,
    /** What the person must be told about this launch; the app shows it once (review1b F-B-2, ADR-078). */
    val startupNotice: StartupNotice = StartupNotice(),
) {
    private val closed = AtomicInt(0)

    /** The Inbox, Decision, Approval and Budget incident screens (slice 1a-2). */
    val attention: AttentionScreens = AttentionScreens(koin)

    /** The Work list and Issue screens (slice 1a-3). */
    val work: WorkScreens = WorkScreens(koin)

    /** The Agents tab and Agent detail (slice 1a-3). */
    val agents: AgentScreens = AgentScreens(koin)

    /** The Run viewer (slice 1a-3). */
    val runs: RunScreens = RunScreens(koin)

    /** Factory home (slice 1a-3). */
    val factory: FactoryScreens = FactoryScreens(koin)

    /** Starts the refresh policy for a visible screen (slice 1a-4). */
    val refresh: RefreshScreens = RefreshScreens(koin)

    /** Tell the graph when the app goes to the background or comes back: `setForeground`, one call (slice 1a-4). */
    val lifecycle: AppLifecycleBridge = koin.get()

    internal inline fun <reified T : Any> resolve(): T = koin.get()

    /** A gateway as the adapter (or fake) provides it, before the signed-out decorator. For tests of the wiring. */
    internal inline fun <reified T : Any> resolveRaw(): T = koin.get(RawPort)

    fun createFirstRunStateHolder(): FirstRunStateHolder = koin.get { parametersOf(newScope()) }

    fun createAppStateHolder(): AppStateHolder =
        koin.get<AppStateHolder> { parametersOf(newScope()) }.also { lifecycle.attach(it) }

    fun createFactoriesStateHolder(): FactoriesStateHolder = koin.get { parametersOf(newScope()) }

    fun createManageFactoriesStateHolder(): ManageFactoriesStateHolder = koin.get { parametersOf(newScope()) }

    fun createSettingsStateHolder(): SettingsStateHolder = koin.get { parametersOf(newScope()) }

    /** Pass `null` to add a factory, or the id of the factory to edit. */
    fun createEditInstanceStateHolder(instanceId: String?): EditInstanceStateHolder =
        koin.get { parametersOf(instanceId, newScope()) }

    private fun newScope() = newScreenScope()

    /**
     * Lets go of what the graph owns, the dependency container first and then, for a live graph, the HTTP client,
     * the database and the network monitor, each once, however often this is called. Close the screens' state
     * holders first. Nothing may be used afterwards.
     */
    fun close() {
        if (!closed.compareAndSet(0, 1)) return
        // Reverse of creation: the container that uses the adapters goes first, then what the adapters stand on.
        try {
            koin.close()
        } finally {
            resources?.close()
        }
    }
}

internal fun newScreenScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

object PaperclipCore {
    /**
     * The graph on the real adapters: the platform's Keychain or Keystore, its biometrics, network monitor and
     * database, and the Paperclip REST API (AD-040). [pairingBrowser] opens the approval page; the app supplies it
     * because a system browser session needs a window. An empty database yields no factories and the first-run flow
     * (AD-041). Close the graph with `close()` when the process is done with it.
     *
     * @param allowInsecureLocal true only in debug builds; lets `http://localhost` and `http://10.0.2.2` be used.
     */
    fun createLiveGraph(
        allowInsecureLocal: Boolean,
        pairingBrowser: PairingBrowser,
    ): PaperclipGraph = createLiveGraph(allowInsecureLocal, pairingBrowser, defaultLivePlatform())

    /** The graph on fakes seeded with the demo data set that matches the design mocks (demo mode, PRD FR-097). */
    fun createDemoGraph(): PaperclipGraph =
        PaperclipGraph(
            koinApplication { modules(demoPortsModule(), useCaseModule(false), presentationModule()) }.koin,
        )

    /**
     * The graph a platform app starts with. **Until Phase 1b it is backed by fakes, not by a server** (ADR-037): an
     * empty in-memory `SecretStore`, a `Biometrics` that always approves, a `Clock` frozen at 2026-11-01T12:00Z that
     * never advances (so the idle lock, the undo window, key expiry and "Decide now" grouping cannot be exercised
     * through it), and gateways that answer from memory. Phase 1b replaces these bindings with real adapters behind the
     * same ports; the signature stays. Do not ship a build that reaches this function with the fakes still bound.
     *
     * @param allowInsecureLocal true only in debug builds; lets `http://localhost` and `http://10.0.2.2` be added.
     */
    fun createGraph(allowInsecureLocal: Boolean): PaperclipGraph {
        // An isolated Koin application, not the global one, so graphs never share state (and tests stay independent).
        val application =
            koinApplication {
                modules(portsModule(), useCaseModule(allowInsecureLocal), presentationModule())
            }
        return PaperclipGraph(application.koin)
    }
}
