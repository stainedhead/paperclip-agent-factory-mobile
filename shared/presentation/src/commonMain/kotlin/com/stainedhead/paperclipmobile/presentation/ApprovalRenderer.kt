package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalActions
import com.stainedhead.paperclipmobile.domain.ApprovalDetail
import com.stainedhead.paperclipmobile.domain.ApprovalPayload
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.Role
import kotlin.time.Instant

/** Everything the Approval screen knows, before it is rendered into [ApprovalState]. */
internal data class ApprovalModel(
    val context: CompanyContext? = null,
    val detail: ApprovalDetail? = null,
    val loadedAt: Instant? = null,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val notePrompt: NotePromptKind? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
) {
    fun withApproval(approval: Approval): ApprovalModel = copy(detail = detail?.copy(approval = approval), error = null)

    fun mayAct(
        action: Action,
        online: Boolean,
    ): Boolean {
        val role = context?.role ?: return false
        return !submitting && online && Permissions.allows(role, action)
    }
}

/** Holds the Approval screen's display rules (PRD FR-050, FR-051, FR-044). */
internal object ApprovalRenderer {
    fun render(
        model: ApprovalModel,
        online: Boolean,
    ): ApprovalState {
        val detail = model.detail
        val approval = detail?.approval
        val role = model.context?.role
        val actionable = online && !model.submitting && role != null
        return ApprovalState(
            phase =
                if (detail !=
                    null
                ) {
                    DetailPhase.Content
                } else if (model.loading) {
                    DetailPhase.Loading
                } else {
                    DetailPhase.Failed
                },
            type = approval?.type?.let(::type),
            status = approval?.status?.toUi(),
            payload = approval?.payload?.let(::payload),
            requestedByAgentName = approval?.requestedByAgentName,
            decisionNote = approval?.decisionNote,
            decidedAtEpochMillis = approval?.decidedAt?.toEpochMilliseconds(),
            issues = detail?.issues.orEmpty().map { LinkedIssueUi(it.id, it.identifier, it.title, it.status) },
            comments =
                detail?.comments.orEmpty().map {
                    CommentUi(it.id, it.body, it.createdAt.toEpochMilliseconds(), it.authorAgentId != null)
                },
            actions =
                if (approval != null && role != null && actionable) {
                    ApprovalActions
                        .availableFor(
                            approval.status,
                            role,
                        ).map { ApprovalActionUi.entries[it.ordinal] }
                        .sorted()
                } else {
                    emptyList()
                },
            rejectNeedsDeviceAuth = true,
            isResolved = approval != null && !approval.status.isResolvable,
            notePrompt = model.notePrompt,
            isSubmitting = model.submitting,
            isOffline = !online,
            actionsDisabledReason = disabledReason(online, role),
            role = role?.toUi(),
            updatedAtEpochMillis = model.loadedAt?.toEpochMilliseconds(),
            error = model.error,
            feedback = model.feedback,
        )
    }

    private fun disabledReason(
        online: Boolean,
        role: Role?,
    ) = when {
        !online -> ActionsDisabledReason.Offline
        role == Role.Viewer -> ActionsDisabledReason.ViewerRole
        else -> null
    }

    private fun type(type: ApprovalType) =
        when (type) {
            ApprovalType.HireAgent -> ApprovalTypeUi.HireAgent
            ApprovalType.ApproveCeoStrategy -> ApprovalTypeUi.ApproveCeoStrategy
            ApprovalType.BudgetOverrideRequired -> ApprovalTypeUi.BudgetOverrideRequired
            ApprovalType.RequestBoardApproval -> ApprovalTypeUi.RequestBoardApproval
            is ApprovalType.Unsupported -> ApprovalTypeUi.Unsupported
        }

    private fun payload(payload: ApprovalPayload): ApprovalPayloadUi =
        when (payload) {
            is ApprovalPayload.HireAgent -> {
                ApprovalPayloadUi.HireAgent(
                    payload.name,
                    payload.role,
                    payload.title,
                    payload.adapterType,
                    payload.reportsToAgentId,
                    payload.monthlyBudget?.toUi(),
                    payload.skills,
                    payload.capabilities,
                )
            }

            is ApprovalPayload.CeoStrategy -> {
                ApprovalPayloadUi.CeoStrategy(payload.title, payload.plan)
            }

            is ApprovalPayload.BudgetOverride -> {
                ApprovalPayloadUi.BudgetOverride(
                    payload.scopeName,
                    payload.windowKind,
                    payload.limit?.toUi(),
                    payload.observed?.toUi(),
                    payload.guidance,
                )
            }

            is ApprovalPayload.BoardRequest -> {
                ApprovalPayloadUi.BoardRequest(
                    payload.title,
                    payload.summary,
                    payload.recommendedAction,
                    payload.nextActionOnApproval,
                    payload.risks,
                    payload.proposedComment,
                )
            }

            is ApprovalPayload.Unsupported -> {
                ApprovalPayloadUi.Unsupported(payload.rawType)
            }
        }
}

internal fun ApprovalStatus.toUi() = ApprovalStatusUi.entries[ordinal]
