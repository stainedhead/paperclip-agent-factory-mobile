package com.stainedhead.paperclipmobile.domain

/** Actions a card can offer without opening a detail screen (PRD FR-033). */
enum class InlineAction {
    Approve,
    Reject,
    KeepPaused,
    RaiseAndResume,
}

enum class DestinationKind {
    Decision,
    Approval,
    BudgetIncident,
    Interaction,
    Issue,
    Run,
    Agent,
    WebOnly,
}

/** Where tapping a card goes. [webPath] is relative to the web app root and may be null (research Q2). */
data class Destination(
    val kind: DestinationKind,
    val id: String,
    val issueId: String? = null,
    val webPath: String? = null,
)

/**
 * What a card offers: the server's `inlineResolvable` flag AND an action this app supports inline AND the role
 * (ADR-019).
 */
object InlineActionPolicy {
    fun actionsFor(
        item: AttentionItem,
        role: Role,
    ): List<InlineAction> {
        if (!item.inlineResolvable) return emptyList()
        return when (item.sourceKind) {
            AttentionSourceKind.Approval -> {
                listOfNotNull(
                    InlineAction.Approve.takeIf { Permissions.allows(role, Action.ApproveApproval) },
                    InlineAction.Reject.takeIf { Permissions.allows(role, Action.RejectApproval) },
                )
            }

            AttentionSourceKind.BudgetAlert -> {
                if (item.subject.budget?.threshold == BudgetThreshold.Hard) {
                    listOfNotNull(
                        InlineAction.KeepPaused.takeIf { Permissions.allows(role, Action.KeepBudgetPaused) },
                        InlineAction.RaiseAndResume.takeIf { Permissions.allows(role, Action.RaiseBudgetAndResume) },
                    )
                } else {
                    emptyList()
                }
            }

            else -> {
                emptyList()
            }
        }
    }

    fun destinationOf(item: AttentionItem): Destination {
        val subject = item.subject
        val kind =
            when (item.sourceKind) {
                AttentionSourceKind.Approval -> DestinationKind.Approval
                AttentionSourceKind.Decision -> DestinationKind.Decision
                AttentionSourceKind.IssueThreadInteraction -> DestinationKind.Interaction
                AttentionSourceKind.BudgetAlert -> DestinationKind.BudgetIncident
                AttentionSourceKind.FailedRun -> DestinationKind.Run
                AttentionSourceKind.AgentErrorAlert -> DestinationKind.Agent
                AttentionSourceKind.Review, AttentionSourceKind.BlockerAttention -> DestinationKind.Issue
                else -> DestinationKind.WebOnly
            }
        return Destination(kind, subject.id, subject.issueId, subject.href)
    }

    /** True when the person cannot respond: a viewer, or a card addressed to another user (research Q3). */
    fun isReadOnly(
        item: AttentionItem,
        role: Role,
        userId: String?,
    ): Boolean =
        !Permissions.allows(role, Action.RespondToInteraction) ||
            item.resolverAudience?.allowsUser(userId) == false
}
