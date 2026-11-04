package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.application.refresh.RefreshSession
import com.stainedhead.paperclipmobile.presentation.AppIntent
import com.stainedhead.paperclipmobile.presentation.AppState
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.FirstRunState
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.presentation.StartupNotice
import com.stainedhead.paperclipmobile.umbrella.PaperclipGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything one open graph owns for the life of the app shell: the app holder (lock, selected factory and company),
 * the first-run holder, and the Inbox holder that feeds the tab badge, which follows the selected factory and company.
 * When the graph could not be opened [graph] is null and so is every holder: nothing runs and nothing is faked
 * (ADR-084, ADR-120); [notice] says why. [close] stops the Inbox refresh and closes every holder, once.
 */
class GraphSession internal constructor(
    val id: Int,
    val mode: GraphMode,
    val graph: PaperclipGraph?,
    val notice: StartupNotice?,
    parent: CoroutineScope,
    autoRefresh: Boolean = true,
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val lock = Any()
    private var refreshSession: RefreshSession? = null
    private var closed = false
    private val autoRefresh = autoRefresh

    val app: ScreenHolder<AppState, AppIntent>? = graph?.createAppStateHolder()?.screenHolder()
    val firstRun: ScreenHolder<FirstRunState, FirstRunIntent>? = graph?.createFirstRunStateHolder()?.screenHolder()

    private val mutableInbox = MutableStateFlow<ScreenHolder<InboxState, InboxIntent>?>(null)
    private val mutableFirstRunInProgress = MutableStateFlow(false)

    /** The Inbox of the selected factory and company; null when there is none yet. */
    val inbox: StateFlow<ScreenHolder<InboxState, InboxIntent>?> = mutableInbox

    /**
     * The first-run flow has saved the factory but is not finished: it keeps the screen until Done (also when the
     * person steps back to the address), so adding the factory does not swap the flow for the tab shell half way.
     */
    val firstRunInProgress: StateFlow<Boolean> = mutableFirstRunInProgress

    /** The server's count of what needs the person (`deskBadgeCount`); the shell never computes it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val inboxBadge: StateFlow<Int> =
        mutableInbox
            .flatMapLatest { holder -> holder?.state?.map { it.badgeCount } ?: flowOf(0) }
            .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        app?.let { holder ->
            scope.launch {
                holder.state
                    .map { it.selectedInstanceId to it.selectedCompanyId }
                    .distinctUntilChanged()
                    .collect { (instance, company) -> followSelection(instance, company) }
            }
        }
        firstRun?.let { holder ->
            scope.launch {
                holder.state.map { it.step }.distinctUntilChanged().collect { step ->
                    when (step) {
                        FirstRunStep.Pair, FirstRunStep.Secure -> mutableFirstRunInProgress.value = true
                        FirstRunStep.Done -> mutableFirstRunInProgress.value = false
                        FirstRunStep.Address -> Unit
                    }
                }
            }
        }
    }

    private fun followSelection(
        instance: String?,
        company: String?,
    ) {
        synchronized(lock) {
            if (closed) return
            stopInbox()
            val opened = graph
            if (opened == null || instance == null || company == null) return
            val holder = opened.attention.createInboxStateHolder(instance, company)
            if (autoRefresh) refreshSession = opened.refresh.start(holder.refreshTarget)
            mutableInbox.value = holder.screenHolder()
        }
    }

    private fun stopInbox() {
        refreshSession?.stop()
        refreshSession = null
        mutableInbox.value?.close()
        mutableInbox.value = null
    }

    /** Stops refreshing and closes every holder this session owns, once. The graph itself is closed by the owner. */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            stopInbox()
        }
        scope.cancel()
        app?.close()
        firstRun?.close()
    }
}
