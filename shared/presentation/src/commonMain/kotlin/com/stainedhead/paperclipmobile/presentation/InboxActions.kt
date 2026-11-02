package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.attention.UndoDismiss
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.InlineAction
import com.stainedhead.paperclipmobile.domain.InlineActionPolicy
import com.stainedhead.paperclipmobile.domain.port.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Inline approval and budget actions on a card (PRD FR-033, FR-051). An intent is honoured only if the card offers that
 * action to this role, the same list the renderer shows ([InlineActionPolicy]); a stale or forged intent is ignored.
 */
internal class InboxApprovalActions(
    private val session: InboxSession,
) {
    private fun item(id: String) = session.model.value.find(id)

    /** The card, if it offers [action] to the person's role. */
    private fun offering(
        itemId: String,
        action: InlineAction,
    ): AttentionItem? {
        val role =
            session.model.value.context
                ?.role ?: return null
        return item(itemId)?.takeIf { action in InlineActionPolicy.actionsFor(it, role) }
    }

    fun approve(itemId: String) {
        val item = offering(itemId, InlineAction.Approve) ?: return
        val status = statusOf(item) ?: return
        session.mutate({ context -> session.useCases.approve(context, ApprovalId(item.subject.id), status, null) })
    }

    fun askForRejectNote(itemId: String) {
        val item = offering(itemId, InlineAction.Reject) ?: return
        if (statusOf(item) == null || session.model.value.mutating || !session.connectivity.isOnline.value) return
        session.model.update { it.copy(rejectPrompt = RejectPromptUi(itemId), feedback = null) }
    }

    fun cancelRejectNote() {
        session.model.update { it.copy(rejectPrompt = null) }
    }

    fun reject(note: String) {
        val itemId =
            session.model.value.rejectPrompt
                ?.itemId ?: return
        val item = offering(itemId, InlineAction.Reject) ?: return
        val status = statusOf(item) ?: return
        session.mutate({ context ->
            session.useCases.reject(context, ApprovalId(item.subject.id), status, note)
        }) { result ->
            val finished = result is MutationResult.Done || result is MutationResult.Reconciled
            if (finished) session.model.update { it.copy(rejectPrompt = null) }
        }
    }

    fun keepPaused(itemId: String) {
        val incident = offering(itemId, InlineAction.KeepPaused)?.budgetIncident() ?: return
        session.mutate({ context -> session.useCases.resolveBudget(context, incident, BudgetResolution.KeepPaused) })
    }

    /** Null when the card carries no approval status: nothing is guessed (F-B-4). */
    private fun statusOf(item: AttentionItem) = item.subject.status?.let { ApprovalStatus.fromServer(it) }
}

/** Snooze, dismiss, undo and decide-by on a card (PRD FR-034). */
internal class InboxTriageActions(
    private val session: InboxSession,
    private val clock: Clock,
) {
    private var removed: AttentionItem? = null
    private var removedAt: Instant? = null
    private var undoTimer: Job? = null

    fun snooze(
        itemId: String,
        preset: SnoozePresetUi,
    ) {
        val item = session.model.value.find(itemId) ?: return
        session.mutate(
            { context -> session.useCases.snooze(context, item.dismissalKey, preset.toDomain()) },
        ) { removedIfDone(it, item) }
    }

    fun dismiss(itemId: String) {
        val item = session.model.value.find(itemId) ?: return
        session.mutate({ context -> session.useCases.dismiss(context, item) }) { removedIfDone(it, item) }
    }

    fun undo() {
        val item = removed ?: return
        val since = removedAt ?: return
        session.mutate({ context -> session.useCases.undo(context, item, since) }) { result ->
            undoTimer?.cancel()
            removed = null
            session.model.update {
                it.copy(undo = null, hidden = if (result is MutationResult.Done) it.hidden - item.id else it.hidden)
            }
        }
    }

    fun setDecideBy(
        itemId: String,
        kind: DecideByKindUi,
        isoDate: String?,
    ) {
        val item = session.model.value.find(itemId) ?: return
        val decideBy = decideByOf(kind, isoDate) ?: return
        session.mutate({ context -> session.useCases.setDecideBy(context, item, decideBy) })
    }

    private fun decideByOf(
        kind: DecideByKindUi,
        isoDate: String?,
    ): DecideBy? =
        when (kind) {
            DecideByKindUi.Today -> DecideBy.Today
            DecideByKindUi.ThisWeek -> DecideBy.ThisWeek
            DecideByKindUi.Whenever -> DecideBy.Whenever
            DecideByKindUi.None -> DecideBy.None
            DecideByKindUi.OnDate -> DecideBy.parse(isoDate).takeIf { it is DecideBy.OnDate }
        }

    private fun removedIfDone(
        result: MutationResult<*>,
        item: AttentionItem,
    ) {
        if (result !is MutationResult.Done) return
        val now = clock.now()
        removed = item
        removedAt = now
        val expires = now + UndoDismiss.UNDO_WINDOW
        session.model.update {
            it.copy(
                hidden = it.hidden + item.id,
                undo = UndoUi(item.id, expires.toEpochMilliseconds()),
            )
        }
        undoTimer?.cancel()
        undoTimer =
            session.scope.launch {
                delay(UndoDismiss.UNDO_WINDOW)
                session.model.update { if (it.undo?.itemId == item.id) it.copy(undo = null) else it }
            }
    }
}
