package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Destination
import com.stainedhead.paperclipmobile.domain.DestinationKind
import com.stainedhead.paperclipmobile.domain.InboxGroup
import com.stainedhead.paperclipmobile.domain.InlineAction
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SnoozePreset

enum class SeverityUi {
    Critical,
    High,
    Medium,
    Low,
}

internal fun Severity.toUi(): SeverityUi =
    when (this) {
        Severity.Critical -> SeverityUi.Critical
        Severity.High -> SeverityUi.High
        Severity.Medium -> SeverityUi.Medium
        Severity.Low -> SeverityUi.Low
    }

/** The source kind of a card. [Unsupported] cards open the web app (PRD FR-033). */
enum class SourceKindUi {
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
    Unsupported,
}

internal fun AttentionSourceKind.toUi(): SourceKindUi =
    when (this) {
        AttentionSourceKind.Approval -> SourceKindUi.Approval
        AttentionSourceKind.Decision -> SourceKindUi.Decision
        AttentionSourceKind.IssueThreadInteraction -> SourceKindUi.IssueThreadInteraction
        AttentionSourceKind.JoinRequest -> SourceKindUi.JoinRequest
        AttentionSourceKind.RecoveryAction -> SourceKindUi.RecoveryAction
        AttentionSourceKind.ProductivityReview -> SourceKindUi.ProductivityReview
        AttentionSourceKind.BlockerAttention -> SourceKindUi.BlockerAttention
        AttentionSourceKind.Review -> SourceKindUi.Review
        AttentionSourceKind.FailedRun -> SourceKindUi.FailedRun
        AttentionSourceKind.BudgetAlert -> SourceKindUi.BudgetAlert
        AttentionSourceKind.AgentErrorAlert -> SourceKindUi.AgentErrorAlert
        is AttentionSourceKind.Unsupported -> SourceKindUi.Unsupported
    }

enum class InboxGroupUi {
    DecideNow,
    ThisWeek,
    Whenever,
}

internal fun InboxGroup.toUi(): InboxGroupUi =
    when (this) {
        InboxGroup.DecideNow -> InboxGroupUi.DecideNow
        InboxGroup.ThisWeek -> InboxGroupUi.ThisWeek
        InboxGroup.Whenever -> InboxGroupUi.Whenever
    }

enum class DecideByKindUi {
    Today,
    ThisWeek,
    Whenever,
    OnDate,
    None,
}

/** [isoDate] is `YYYY-MM-DD` and set only for [DecideByKindUi.OnDate]. */
data class DecideByUi(
    val kind: DecideByKindUi,
    val isoDate: String?,
)

internal fun DecideBy.toUi(): DecideByUi =
    when (this) {
        DecideBy.Today -> DecideByUi(DecideByKindUi.Today, null)
        DecideBy.ThisWeek -> DecideByUi(DecideByKindUi.ThisWeek, null)
        DecideBy.Whenever -> DecideByUi(DecideByKindUi.Whenever, null)
        is DecideBy.OnDate -> DecideByUi(DecideByKindUi.OnDate, date.toString())
        DecideBy.None -> DecideByUi(DecideByKindUi.None, null)
    }

enum class InlineActionUi {
    Approve,
    Reject,
    KeepPaused,

    /** Opens the budget incident sheet; the amount is entered there. */
    RaiseAndResume,
}

internal fun InlineAction.toUi(): InlineActionUi =
    when (this) {
        InlineAction.Approve -> InlineActionUi.Approve
        InlineAction.Reject -> InlineActionUi.Reject
        InlineAction.KeepPaused -> InlineActionUi.KeepPaused
        InlineAction.RaiseAndResume -> InlineActionUi.RaiseAndResume
    }

enum class OpenKindUi {
    Decision,
    Approval,
    BudgetIncident,
    Interaction,
    Issue,
    Run,
    Agent,
    WebOnly,
}

/** Where a card goes when tapped. [WebOnly] cards use the item's `webUrl`. */
data class OpenTargetUi(
    val kind: OpenKindUi,
    val id: String,
    val issueId: String?,
)

internal fun Destination.toUi() = OpenTargetUi(kind.toUi(), id, issueId)

enum class ResolverPolicyUi {
    Anyone,
    NotCreator,
    HumanOnly,
}

/** Who may resolve an interaction card, as facts for the platform UI to word (research Q3). */
data class AudienceUi(
    val policy: ResolverPolicyUi,
    val addresseeName: String?,
    val addresseeIsAnotherUser: Boolean,
    val createdByName: String?,
)

internal fun ResolverAudience.toUi(userId: String?) =
    AudienceUi(
        policy = effectivePolicy.toUi(),
        addresseeName = addresseeName,
        addresseeIsAnotherUser = !allowsUser(userId),
        createdByName = createdByAgentName,
    )
