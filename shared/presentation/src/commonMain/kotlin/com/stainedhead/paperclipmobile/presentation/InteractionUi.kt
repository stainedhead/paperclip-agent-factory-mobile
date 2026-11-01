package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.Verdict

enum class InteractionKindUi {
    SuggestTasks,
    AskUserQuestions,
    RequestConfirmation,
    RequestCheckboxConfirmation,
    RequestItemVerdicts,
    Unsupported,
}

enum class InteractionStatusUi {
    Pending,
    Accepted,
    Rejected,
    Answered,
    Cancelled,
    Expired,
    Failed,
    Unknown,
}

internal fun InteractionStatus.toUi() = InteractionStatusUi.valueOf(name)

/** Why a card cannot be answered; null when it can. */
enum class InteractionReadOnlyUi {
    NotPending,
    NotForYou,
    Blocked,
    ViewerRole,
    Offline,
    Unsupported,
}

/** What the main button of a card does. */
enum class InteractionPrimaryUi {
    Accept,
    Respond,
    SubmitVerdicts,
}

enum class VerdictUi {
    Approve,
    Reject,
    Defer,
}

internal fun VerdictUi.toDomain() = Verdict.valueOf(name)

internal fun Verdict.toUi() = VerdictUi.valueOf(name)

data class ChoiceUi(
    val id: String,
    val label: String,
    val description: String?,
    val selected: Boolean,
)

data class QuestionUi(
    val id: String,
    val prompt: String,
    val helpText: String?,
    val multi: Boolean,
    val required: Boolean,
    val allowOther: Boolean,
    val options: List<ChoiceUi>,
    val otherText: String,
)

data class VerdictItemUi(
    val id: String,
    val label: String,
    val description: String?,
    val verdict: VerdictUi?,
    val reason: String,
)

/**
 * An interaction card, ready to draw (PRD FR-063). [submitIssue] and [rejectIssue] are the live validation of the main
 * and reject buttons; [submitIssueTargetId] names the question or item that needs fixing.
 */
data class InteractionUi(
    val id: String,
    val kind: InteractionKindUi,
    val status: InteractionStatusUi,
    val title: String?,
    val prompt: String?,
    val detailsMarkdown: String?,
    val canAnswer: Boolean,
    val readOnlyReason: InteractionReadOnlyUi?,
    val primary: InteractionPrimaryUi?,
    val hasReject: Boolean,
    val rejectRequiresReason: Boolean,
    val rejectReason: String,
    val rejectIssue: InvalidKey?,
    val submitIssue: InvalidKey?,
    val submitIssueTargetId: String?,
    val tasks: List<ChoiceUi>,
    val options: List<ChoiceUi>,
    val questions: List<QuestionUi>,
    val items: List<VerdictItemUi>,
    val enabledVerdicts: List<VerdictUi>,
    /** For an unsupported kind: where to open the issue in the web app. */
    val openInWebUrl: String?,
)
