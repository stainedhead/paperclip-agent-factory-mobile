package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

/** The eleven source kinds of the pinned server plus [Unsupported] for kinds from a newer one (research Q2). */
sealed interface AttentionSourceKind {
    val serverName: String

    data object Approval : AttentionSourceKind {
        override val serverName = "approval"
    }

    data object Decision : AttentionSourceKind {
        override val serverName = "decision"
    }

    data object IssueThreadInteraction : AttentionSourceKind {
        override val serverName = "issue_thread_interaction"
    }

    data object JoinRequest : AttentionSourceKind {
        override val serverName = "join_request"
    }

    data object RecoveryAction : AttentionSourceKind {
        override val serverName = "recovery_action"
    }

    data object ProductivityReview : AttentionSourceKind {
        override val serverName = "productivity_review"
    }

    data object BlockerAttention : AttentionSourceKind {
        override val serverName = "blocker_attention"
    }

    data object Review : AttentionSourceKind {
        override val serverName = "review"
    }

    data object FailedRun : AttentionSourceKind {
        override val serverName = "failed_run"
    }

    data object BudgetAlert : AttentionSourceKind {
        override val serverName = "budget_alert"
    }

    data object AgentErrorAlert : AttentionSourceKind {
        override val serverName = "agent_error_alert"
    }

    data class Unsupported(
        val raw: String,
    ) : AttentionSourceKind {
        override val serverName: String get() = raw
    }

    companion object {
        val known: List<AttentionSourceKind> =
            listOf(
                Approval,
                Decision,
                IssueThreadInteraction,
                JoinRequest,
                RecoveryAction,
                ProductivityReview,
                BlockerAttention,
                Review,
                FailedRun,
                BudgetAlert,
                AgentErrorAlert,
            )

        fun fromServer(raw: String): AttentionSourceKind =
            known.firstOrNull { it.serverName == raw } ?: Unsupported(raw)
    }
}

enum class SubjectKind {
    Approval,
    Decision,
    Issue,
    Interaction,
    JoinRequest,
    RecoveryAction,
    Run,
    BudgetIncident,
    Agent,
    Unknown,
}

/** What an item is about. [href] is a path relative to the web app root, or null (research Q2). */
data class AttentionSubject(
    val kind: SubjectKind,
    val id: String,
    val title: String? = null,
    val identifier: String? = null,
    val status: String? = null,
    val href: String? = null,
    val issueId: String? = null,
    val approvalType: ApprovalType? = null,
    val interactionKind: InteractionKind? = null,
    val budget: BudgetAlertInfo? = null,
)

data class DecisionVerb(
    val id: String,
    val label: String,
    val description: String? = null,
)

enum class DismissalKind {
    Dismiss,
    Snooze,
}

/** A person's dismissal or snooze of one item, as the server evaluated it at `generatedAt`. */
data class Dismissal(
    val kind: DismissalKind,
    val dismissedAt: Instant,
    val snoozedUntil: Instant?,
    val isActive: Boolean,
)

/** One card in the Inbox, in the server's shape (data dictionary). Ids are unique inside one factory (research Q7). */
data class AttentionItem(
    val id: String,
    val sourceKind: AttentionSourceKind,
    val subject: AttentionSubject,
    val whyNow: String,
    val severity: Severity,
    val dismissalKey: String,
    val activityAt: Instant,
    val createdAt: Instant,
    val decideBy: DecideBy = DecideBy.None,
    val decisionVerbs: List<DecisionVerb> = emptyList(),
    val inlineResolvable: Boolean = false,
    val dismissal: Dismissal? = null,
    val snoozedUntil: Instant? = null,
    val originAgentName: String? = null,
    val relatedIssue: AttentionSubject? = null,
    val detail: AttentionDetail? = null,
    val resolverAudience: ResolverAudience? = null,
)

/** A page of the feed. The counts are company-wide and computed before paging (research, feed request). */
data class AttentionFeed(
    val generatedAt: Instant,
    val totalCount: Int,
    val deskBadgeCount: Int,
    val nextCursor: String?,
    val countsBySourceKind: Map<AttentionSourceKind, Int>,
    val items: List<AttentionItem>,
)
