package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.android.diagnostics.DiagnosticsRecorder
import com.stainedhead.paperclipmobile.android.diagnostics.PairingOutcome
import com.stainedhead.paperclipmobile.android.diagnostics.toKeyErrorClass
import com.stainedhead.paperclipmobile.android.diagnostics.toPairingOutcome
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/** What a session is given besides its graph: whether the Inbox refreshes by itself, and where diagnostics go. */
data class SessionOptions(
    val autoRefresh: Boolean = true,
    val diagnostics: DiagnosticsRecorder = DiagnosticsRecorder(UNKNOWN_VERSION),
    /** How the Inbox holder of a factory and company is made; tests give a holder whose work they control. */
    val inboxFactory: ((PaperclipGraph, String, String) -> ScreenHolder<InboxState, InboxIntent>)? = null,
)

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
    private val options: SessionOptions = SessionOptions(),
) {
    /** What the app has seen of itself, for the Diagnostics screen; the session feeds it, screens read it. */
    val diagnostics: DiagnosticsRecorder = options.diagnostics

    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

    // Outlives [scope]: a holder waiting for its work to end must still be closed after the session is.
    private val retiring = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val deferred = CopyOnWriteArrayList<ScreenHolder<InboxState, InboxIntent>>()
    private val lock = Any()
    private var refreshSession: RefreshSession? = null
    private var closed = false

    /** Whether this session refreshes screens by itself; a screen that starts a refresh honours it too. */
    val autoRefresh = options.autoRefresh
    private val pairingCancels = CopyOnWriteArrayList<() -> Unit>()

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
        recordDiagnostics()
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

    /** Feeds the diagnostics from the shared states: the last unlock result, key error and pairing outcome. */
    private fun recordDiagnostics() {
        app?.let { holder ->
            scope.launch {
                holder.state
                    .map { it.unlockIssue }
                    .distinctUntilChanged()
                    .filterNotNull()
                    .collect(diagnostics::recordBiometric)
            }
        }
        firstRun?.let { holder ->
            scope.launch {
                holder.state
                    .map { state -> state.pairingOutcome() }
                    .distinctUntilChanged()
                    .filterNotNull()
                    .collect(diagnostics::recordPairing)
            }
            scope.launch {
                holder.state
                    .map { it.error?.key?.toKeyErrorClass() }
                    .distinctUntilChanged()
                    .filterNotNull()
                    .collect(diagnostics::recordKeyError)
            }
        }
    }

    private fun FirstRunState.pairingOutcome(): PairingOutcome? =
        if (step == FirstRunStep.Secure ||
            step == FirstRunStep.Done
        ) {
            PairingOutcome.Approved
        } else {
            pairing.toPairingOutcome()
        }

    internal fun followSelection(
        instance: String?,
        company: String?,
    ) {
        synchronized(lock) {
            if (closed) return
            stopInbox()
            val opened = graph
            if (opened == null || instance == null || company == null) return
            val holder =
                options.inboxFactory?.invoke(opened, instance, company)
                    ?: opened.attention.createInboxStateHolder(instance, company).screenHolder()
            if (autoRefresh) holder.refreshTarget?.let { refreshSession = opened.refresh.start(it) }
            mutableInbox.value = holder
        }
    }

    private fun stopInbox() {
        refreshSession?.stop()
        refreshSession = null
        val holder = mutableInbox.value
        mutableInbox.value = null
        if (holder != null) retire(holder)
    }

    /**
     * An approve or reject in flight is not cancelled by a selection change or by the session closing: the holder is
     * closed after the outcome and the read that reconciles it are recorded (ADR-117, R-007).
     */
    private fun retire(holder: ScreenHolder<InboxState, InboxIntent>) {
        if (!holder.isWorking) {
            holder.close()
            return
        }
        deferred += holder
        retiring.launch {
            holder.awaitIdle()
            holder.close()
            deferred -= holder
        }
    }

    /**
     * A screen that can wait for the approval page (the edit screen's re-pair) registers how to cancel its wait; the
     * returned function removes it again. The first-run flow is cancelled by [cancelPairingAttempts] too.
     */
    fun onPairingCancelled(cancel: () -> Unit): () -> Unit {
        pairingCancels += cancel
        return { pairingCancels -= cancel }
    }

    /** The approval page could not be shown: every pairing that waits for it ends as cancelled, none keeps waiting. */
    fun cancelPairingAttempts() {
        firstRun?.send(FirstRunIntent.PairCancelled)
        pairingCancels.forEach { it() }
    }

    /** How many holders are still waiting for their work to finish before they close (R-007). */
    internal val pendingCloses: Int get() = deferred.size

    /** The graph is going away: nothing can finish any more, so every deferred close happens now (R-007). */
    fun abandonDeferred() {
        retiring.cancel()
        deferred.toList().forEach { it.close() }
        deferred.clear()
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

private const val UNKNOWN_VERSION = "0.0.0"
