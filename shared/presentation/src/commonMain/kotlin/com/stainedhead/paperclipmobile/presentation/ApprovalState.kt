package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.approval.ApproveApproval
import com.stainedhead.paperclipmobile.application.approval.CommentOnApproval
import com.stainedhead.paperclipmobile.application.approval.LoadApproval
import com.stainedhead.paperclipmobile.application.approval.RejectApproval
import com.stainedhead.paperclipmobile.application.approval.RequestRevision

enum class ApprovalTypeUi {
    HireAgent,
    ApproveCeoStrategy,
    BudgetOverrideRequired,
    RequestBoardApproval,
    Unsupported,
}

enum class ApprovalStatusUi {
    Pending,
    RevisionRequested,
    Approved,
    Rejected,
    Cancelled,
    Unknown,
}

/** The payload of each approval type as the screen shows it (PRD FR-050). Every field may be missing. */
sealed interface ApprovalPayloadUi {
    data class HireAgent(
        val name: String?,
        val role: String?,
        val title: String?,
        val adapterType: String?,
        val reportsToAgentId: String?,
        /** The manager's name when it could be read; show it instead of the id. */
        val reportsToName: String?,
        val monthlyBudget: MoneyUi?,
        val skills: List<String>,
        val capabilities: String?,
    ) : ApprovalPayloadUi

    data class CeoStrategy(
        val title: String?,
        val plan: String?,
    ) : ApprovalPayloadUi

    data class BudgetOverride(
        val scopeName: String?,
        val windowKind: String?,
        val limit: MoneyUi?,
        val observed: MoneyUi?,
        val guidance: String?,
    ) : ApprovalPayloadUi

    data class BoardRequest(
        val title: String?,
        val summary: String?,
        val recommendedAction: String?,
        val nextActionOnApproval: String?,
        val risks: List<String>,
        val proposedComment: String?,
    ) : ApprovalPayloadUi

    data class Unsupported(
        val rawType: String,
    ) : ApprovalPayloadUi
}

data class LinkedIssueUi(
    val id: String,
    val identifier: String?,
    val title: String,
    val status: String?,
)

data class CommentUi(
    val id: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val isFromAgent: Boolean,
)

enum class ApprovalActionUi {
    Approve,
    Reject,
    RequestRevision,
    Comment,
}

/** Which note the person is being asked for. */
enum class NotePromptKind {
    Reject,
    RequestRevision,
}

data class ApprovalState(
    val phase: DetailPhase = DetailPhase.Loading,
    val type: ApprovalTypeUi? = null,
    val status: ApprovalStatusUi? = null,
    val payload: ApprovalPayloadUi? = null,
    val requestedByAgentName: String? = null,
    val decisionNote: String? = null,
    val decidedAtEpochMillis: Long? = null,
    val issues: List<LinkedIssueUi> = emptyList(),
    val comments: List<CommentUi> = emptyList(),
    /** Counts the person's own comments that reached the thread; the comment box clears when it changes (A5). */
    val commentPostedCount: Int = 0,
    /** What can be tapped now: by status, role and connectivity (PRD FR-051). */
    val actions: List<ApprovalActionUi> = emptyList(),
    val rejectNeedsDeviceAuth: Boolean = false,
    /** True once the approval is approved, rejected or cancelled (PRD FR-044). */
    val isResolved: Boolean = false,
    val notePrompt: NotePromptKind? = null,
    val isSubmitting: Boolean = false,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val role: RoleUi? = null,
    val updatedAtEpochMillis: Long? = null,
    /** 15 seconds while the approval can still change, 0 once it is resolved (PRD FR-090). */
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
) {
    /** True while a change is in flight; the screen must not be torn down yet. */
    val isWorking: Boolean get() = isSubmitting
}

sealed interface ApprovalIntent {
    data object ApproveTapped : ApprovalIntent

    data object RejectTapped : ApprovalIntent

    data object RequestRevisionTapped : ApprovalIntent

    /** Confirms the open note prompt with the person's note. */
    data class NoteConfirmed(
        val note: String,
    ) : ApprovalIntent

    data object NoteCancelled : ApprovalIntent

    data class CommentSubmitted(
        val body: String,
    ) : ApprovalIntent

    data object Refresh : ApprovalIntent

    data object MessageDismissed : ApprovalIntent
}

data class ApprovalUseCases(
    val resolveContext: ResolveCompanyContext,
    val load: LoadApproval,
    val approve: ApproveApproval,
    val reject: RejectApproval,
    val requestRevision: RequestRevision,
    val comment: CommentOnApproval,
)
