package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
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

/**
 * State holder for the Inbox (screen 1): the Attention feed grouped by decide-by, the server's badge, filter chips,
 * inline approval and budget actions, and snooze, dismiss and undo. Cache first, then refresh (PRD FR-030 to FR-035,
 * FR-095).
 */
class InboxStateHolder(
    private val useCases: InboxUseCases,
    private val connectivity: Connectivity,
    clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
) {
    private val scope = childScope(scope)
    private val model = MutableStateFlow(InboxModel())
    private val session =
        InboxSession(
            useCases,
            connectivity,
            this.scope,
            CompanyScope(InstanceId(target.instanceId), CompanyId(target.companyId)),
            model,
        )
    private val approvals = InboxApprovalActions(session)
    private val triage = InboxTriageActions(session, clock)
    private val renderer = InboxRenderer(useCases.snoozePresets, clock)
    private val mutableState = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = mutableState.asStateFlow()

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> renderer.render(model, online) }
                .collect { mutableState.value = it }
        }
        this.scope.launch { session.start() }
    }

    fun onIntent(intent: InboxIntent) {
        when (intent) {
            InboxIntent.Refresh -> scope.launch { session.refresh() }
            InboxIntent.LoadMore -> scope.launch { session.loadMore() }
            is InboxIntent.FilterSelected -> model.update { it.copy(filter = intent.kind) }
            InboxIntent.MessageDismissed -> model.update { it.copy(feedback = null) }
            else -> onAction(intent)
        }
    }

    private fun onAction(intent: InboxIntent) {
        when (intent) {
            is InboxIntent.ApproveTapped -> approvals.approve(intent.itemId)
            is InboxIntent.RejectTapped -> approvals.askForRejectNote(intent.itemId)
            is InboxIntent.RejectConfirmed -> approvals.reject(intent.note)
            InboxIntent.RejectCancelled -> approvals.cancelRejectNote()
            is InboxIntent.KeepPausedTapped -> approvals.keepPaused(intent.itemId)
            is InboxIntent.SnoozeRequested -> triage.snooze(intent.itemId, intent.preset)
            is InboxIntent.DismissTapped -> triage.dismiss(intent.itemId)
            InboxIntent.UndoTapped -> triage.undo()
            is InboxIntent.DecideBySet -> triage.setDecideBy(intent.itemId, intent.kind, intent.isoDate)
            else -> Unit
        }
    }

    fun close() {
        scope.cancel()
    }
}
