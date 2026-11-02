package com.stainedhead.paperclipmobile.umbrella

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

/**
 * The composition root. Platform apps create one graph and ask it for screen state holders.
 *
 * Until the Phase 1b adapters exist the graph runs on the shared fakes (demo mode), so every screen can be built
 * without a server. Each `create...StateHolder` call returns a new holder that the screen owns; call `close()` on it
 * when the screen goes away.
 */
class PaperclipGraph internal constructor(
    private val koin: Koin,
) {
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
}

internal fun newScreenScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

object PaperclipCore {
    /** The graph on fakes seeded with the demo data set that matches the design mocks (demo mode, PRD FR-097). */
    fun createDemoGraph(): PaperclipGraph =
        PaperclipGraph(
            koinApplication { modules(demoPortsModule(), useCaseModule(false), presentationModule()) }.koin,
        )

    /**
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
