package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.Permissions
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

/** State holder for Approval detail (screen 3): payload per type, linked issues, comments and the FR-051 actions. */
class ApprovalStateHolder(
    private val useCases: ApprovalUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
    approvalId: String,
) {
    private val scope = childScope(scope)
    private val companyScope = CompanyScope(InstanceId(target.instanceId), CompanyId(target.companyId))
    private val id = ApprovalId(approvalId)
    private val model = MutableStateFlow(ApprovalModel())
    private val mutableState = MutableStateFlow(ApprovalState())
    val state: StateFlow<ApprovalState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(ApprovalIntent.Refresh) },
            {
                it == Invalidation.AttentionFeed
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> ApprovalRenderer.render(model, online) }
                .collect { mutableState.value = it }
        }
        load()
    }

    fun onIntent(intent: ApprovalIntent) {
        when (intent) {
            ApprovalIntent.ApproveTapped -> {
                approve()
            }

            ApprovalIntent.RejectTapped -> {
                askForNote(NotePromptKind.Reject, Action.RejectApproval)
            }

            ApprovalIntent.RequestRevisionTapped -> {
                askForNote(
                    NotePromptKind.RequestRevision,
                    Action.RequestApprovalRevision,
                )
            }

            is ApprovalIntent.NoteConfirmed -> {
                confirmNote(intent.note)
            }

            ApprovalIntent.NoteCancelled -> {
                model.update { it.copy(notePrompt = null) }
            }

            is ApprovalIntent.CommentSubmitted -> {
                comment(intent.body)
            }

            ApprovalIntent.Refresh -> {
                load()
            }

            ApprovalIntent.MessageDismissed -> {
                model.update { it.copy(feedback = null) }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun load() {
        if (!connectivity.isOnline.value) return
        if (!model.claim({ it.loading }) { it.copy(loading = true, error = null) }) return
        val started = model.value.version
        scope.launch {
            if (ensureContext() != null) {
                when (val result = useCases.load(companyScope, id)) {
                    is DomainResult.Success -> {
                        model.update {
                            if (it.version != started) {
                                it
                            } else {
                                it.copy(detail = result.value, loadedAt = clock.now(), error = null)
                            }
                        }
                    }

                    is DomainResult.Failure -> {
                        model.update { it.copy(error = result.error.toErrorUi()) }
                    }
                }
            }
            model.update { it.copy(loading = false) }
        }
    }

    private suspend fun ensureContext(): CompanyContext? =
        model.value.context ?: when (val result = useCases.resolveContext(companyScope)) {
            is DomainResult.Success -> {
                result.value.also { context -> model.update { it.copy(context = context) } }
            }

            is DomainResult.Failure -> {
                model.update { it.copy(error = result.error.toErrorUi()) }
                null
            }
        }

    private fun approve() {
        val status =
            model.value.detail
                ?.approval
                ?.status ?: return
        act(Action.ApproveApproval) { context -> useCases.approve(context, id, status, null) }
    }

    private fun askForNote(
        kind: NotePromptKind,
        action: Action,
    ) {
        val current = model.value
        val role = current.context?.role ?: return
        if (current.detail == null || current.submitting || !connectivity.isOnline.value) return
        if (!Permissions.allows(role, action)) return
        model.update { it.copy(notePrompt = kind, feedback = null) }
    }

    private fun confirmNote(note: String) {
        val current = model.value
        val status = current.detail?.approval?.status ?: return
        val prompt = current.notePrompt ?: return
        when (prompt) {
            NotePromptKind.Reject -> {
                act(
                    Action.RejectApproval,
                ) { context -> useCases.reject(context, id, status, note) }
            }

            NotePromptKind.RequestRevision -> {
                act(
                    Action.RequestApprovalRevision,
                ) { context -> useCases.requestRevision(context, id, status, note) }
            }
        }
    }

    private fun comment(body: String) {
        if (!model.value.mayAct(Action.CommentOnApproval, connectivity.isOnline.value)) return
        runMutation({ context -> useCases.comment(context, id, body) }) { result ->
            when (result) {
                is MutationResult.Done -> model.update { it.withComment(result.value) }
                is MutationResult.Reconciled -> model.update { it.withComment(result.current) }
                is MutationResult.Unknown -> load()
                else -> Unit
            }
        }
    }

    private fun act(
        action: Action,
        call: suspend (CompanyContext) -> MutationResult<Approval>,
    ) {
        if (!model.value.mayAct(action, connectivity.isOnline.value) || model.value.detail == null) return
        runMutation(call) { result ->
            when (result) {
                is MutationResult.Done -> model.update { it.withApproval(result.value).copy(notePrompt = null) }
                is MutationResult.Reconciled -> model.update { it.withApproval(result.current).copy(notePrompt = null) }
                else -> Unit
            }
        }
    }

    private fun <T> runMutation(
        call: suspend (CompanyContext) -> MutationResult<T>,
        onResult: (MutationResult<T>) -> Unit,
    ) {
        if (!model.claim({ it.submitting }) { it.copy(submitting = true, feedback = null) }) return
        scope.launch {
            val context = ensureContext()
            val result = context?.let { call(it) }
            model.update { it.copy(submitting = false, feedback = result?.toFeedback(), version = it.version + 1) }
            if (result?.wasForbidden() ==
                true
            ) {
                model.refreshContext(useCases.resolveContext, companyScope) { copy(context = it) }
            }
            result?.let(onResult)
        }
    }
}
