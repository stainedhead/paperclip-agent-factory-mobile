package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

enum class ApprovalStatus {
    Pending,
    RevisionRequested,
    Approved,
    Rejected,
    Cancelled,
    Unknown,
    ;

    /** The server lets approve and reject run from these two states (research, approvals). */
    val isResolvable: Boolean get() = this == Pending || this == RevisionRequested

    companion object {
        fun fromServer(raw: String): ApprovalStatus =
            when (raw) {
                "pending" -> Pending
                "revision_requested" -> RevisionRequested
                "approved" -> Approved
                "rejected" -> Rejected
                "cancelled" -> Cancelled
                else -> Unknown
            }
    }
}

/** What the payload of each approval type shows (PRD FR-050). Every field may be missing: the server redacts. */
sealed interface ApprovalPayload {
    data class HireAgent(
        val name: String?,
        val role: String?,
        val title: String?,
        val adapterType: String?,
        val reportsToAgentId: String?,
        val monthlyBudget: Money?,
        val skills: List<String>,
        val capabilities: String?,
    ) : ApprovalPayload

    data class CeoStrategy(
        val title: String?,
        val plan: String?,
    ) : ApprovalPayload

    data class BudgetOverride(
        val scopeName: String?,
        val windowKind: String?,
        val limit: Money?,
        val observed: Money?,
        val guidance: String?,
    ) : ApprovalPayload

    data class BoardRequest(
        val title: String?,
        val summary: String?,
        val recommendedAction: String?,
        val nextActionOnApproval: String?,
        val risks: List<String>,
        val proposedComment: String?,
    ) : ApprovalPayload

    data class Unsupported(
        val rawType: String,
    ) : ApprovalPayload
}

data class Approval(
    val id: ApprovalId,
    val type: ApprovalType,
    val status: ApprovalStatus,
    val payload: ApprovalPayload,
    val createdAt: Instant,
    val requestedByAgentId: String? = null,
    val requestedByAgentName: String? = null,
    val decisionNote: String? = null,
    val decidedAt: Instant? = null,
)

data class ApprovalComment(
    val id: String,
    val body: String,
    val createdAt: Instant,
    val authorAgentId: String? = null,
    val authorUserId: String? = null,
)

data class LinkedIssue(
    val id: String,
    val identifier: String?,
    val title: String,
    val status: String?,
)

/** Everything screen 3 shows. */
data class ApprovalDetail(
    val approval: Approval,
    val issues: List<LinkedIssue>,
    val comments: List<ApprovalComment>,
    /** Agent id to name, for the agents the payload names (the hire's manager). Empty when they could not be read. */
    val agentNames: Map<String, String> = emptyMap(),
)

enum class ApprovalAction {
    Approve,
    Reject,
    RequestRevision,
    Comment,
}

object ApprovalActions {
    /** What the screen offers for [status] and [role] (PRD FR-051). Request revision only from pending. */
    fun availableFor(
        status: ApprovalStatus,
        role: Role,
    ): Set<ApprovalAction> =
        buildSet {
            if (status.isResolvable && Permissions.allows(role, Action.ApproveApproval)) add(ApprovalAction.Approve)
            if (status.isResolvable && Permissions.allows(role, Action.RejectApproval)) add(ApprovalAction.Reject)
            if (status == ApprovalStatus.Pending && Permissions.allows(role, Action.RequestApprovalRevision)) {
                add(ApprovalAction.RequestRevision)
            }
            if (Permissions.allows(role, Action.CommentOnApproval)) add(ApprovalAction.Comment)
        }
}
