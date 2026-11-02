package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.LogTail
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class RunModel(
    val run: HeartbeatRun? = null,
    val events: List<RunEvent> = emptyList(),
    val tail: LogTail = LogTail.Empty,
    val logUnavailable: Boolean = false,
    val autoScroll: Boolean = true,
    val role: Role? = null,
    val loading: Boolean = false,
    val confirmCancel: Boolean = false,
    val cancelling: Boolean = false,
    /** Counts finished actions. A read that started before one finished must not replace its result (F-B-7). */
    val version: Int = 0,
    val loadedAtMillis: Long? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

/** State holder for the Run viewer (screen 7): events, log tail, and cancel with confirmation (PRD FR-075, FR-090). */
class RunViewerStateHolder(
    private val useCases: RunViewerUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
    runId: String,
) {
    private val scope = childScope(scope)
    private val companyScope = target.toScope()
    private val id = RunId(runId)
    private val context = ContextSource(useCases.resolveContext, companyScope)
    private val model = MutableStateFlow(RunModel())
    private val mutableState = MutableStateFlow(RunViewerState())
    val state: StateFlow<RunViewerState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(RunViewerIntent.Refresh) },
            {
                it is Invalidation.Run && it.runId == runId
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> renderRun(model, online) }
                .collect { mutableState.value = it }
        }
        refresh()
    }

    fun onIntent(intent: RunViewerIntent) {
        when (intent) {
            RunViewerIntent.Refresh -> refresh()
            is RunViewerIntent.UserScrolled -> model.update { it.copy(autoScroll = intent.atBottom) }
            RunViewerIntent.JumpToLatest -> model.update { it.copy(autoScroll = true) }
            RunViewerIntent.CancelTapped -> askToCancel()
            RunViewerIntent.CancelConfirmed -> cancel()
            RunViewerIntent.CancelDismissed -> model.update { it.copy(confirmCancel = false) }
            RunViewerIntent.MessageDismissed -> model.update { it.copy(feedback = null) }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun refresh() {
        if (!connectivity.isOnline.value) return
        if (!model.claim({ it.loading }) { it.copy(loading = true, error = null) }) return
        val started = model.value.version
        scope.launch {
            val allowed = context.get()
            if (allowed is DomainResult.Failure) {
                model.update { it.copy(error = allowed.error.toErrorUi()) }
            } else {
                readRun(started)
            }
            model.update { it.copy(loading = false) }
        }
    }

    private suspend fun readRun(started: Int) {
        when (val result = useCases.load(companyScope, id, model.value.events)) {
            is DomainResult.Failure -> {
                model.update { it.copy(error = result.error.toErrorUi()) }
            }

            is DomainResult.Success -> {
                model.update {
                    if (it.version != started) {
                        it
                    } else {
                        it.copy(run = result.value.run, events = result.value.events, role = context.role)
                    }
                }
                readLog()
            }
        }
    }

    private suspend fun readLog() {
        when (val log = useCases.tail(companyScope, id, model.value.tail)) {
            is DomainResult.Failure -> {
                model.update { it.copy(logUnavailable = true, loadedAtMillis = now()) }
            }

            is DomainResult.Success -> {
                model.update {
                    it.copy(
                        tail = log.value,
                        logUnavailable = false,
                        loadedAtMillis = now(),
                    )
                }
            }
        }
    }

    private fun now() = clock.now().toEpochMilliseconds()

    private fun canCancelNow(): Boolean {
        val current = model.value
        return connectivity.isOnline.value && !current.cancelling && current.run?.canCancel == true &&
            mayAct(true, current.role, Action.CancelRun)
    }

    private fun askToCancel() {
        if (canCancelNow()) model.update { it.copy(confirmCancel = true, feedback = null) }
    }

    private fun cancel() {
        val run = model.value.run ?: return
        if (!model.value.confirmCancel || !canCancelNow()) return
        if (!model.claim(
                { it.cancelling },
            ) { it.copy(confirmCancel = false, cancelling = true, feedback = null) }
        ) {
            return
        }
        scope.launch {
            val result = mutate(context) { useCases.cancel(it, run) }
            model.update {
                it.copy(
                    cancelling = false,
                    role = result.role ?: it.role,
                    version = it.version + 1,
                    feedback = result.feedback,
                    run = result.value ?: it.run,
                )
            }
        }
    }
}

private fun renderRun(
    model: RunModel,
    online: Boolean,
): RunViewerState {
    val run = model.run
    val role = model.role
    val mayCancel = !model.cancelling && run?.canCancel == true && mayAct(online, role, Action.CancelRun)
    return RunViewerState(
        phase =
            when {
                run != null -> DetailPhase.Content
                model.loading -> DetailPhase.Loading
                else -> DetailPhase.Failed
            },
        run = run?.toRow(),
        events = model.events.map { it.toUi() },
        logText = model.tail.text,
        logDroppedStart = model.tail.droppedStart,
        logUnavailable = model.logUnavailable,
        autoScroll = model.autoScroll,
        isLive = run?.isLive == true,
        canCancel = mayCancel,
        confirmCancel = model.confirmCancel,
        isCancelling = model.cancelling,
        role = role?.toUi(),
        isOffline = !online,
        actionsDisabledReason = disabledReason(online, role, Action.CancelRun),
        updatedAtEpochMillis = model.loadedAtMillis,
        refreshIntervalSeconds =
            when {
                run == null -> SCREEN_REFRESH_SECONDS
                run.isLive -> RUNNING_REFRESH_SECONDS
                else -> 0
            },
        error = model.error,
        feedback = model.feedback,
    )
}
