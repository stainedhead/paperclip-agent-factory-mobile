package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.attention.AvailableSnoozePresets
import com.stainedhead.paperclipmobile.application.attention.SnoozeItem
import com.stainedhead.paperclipmobile.application.decision.DecideDecision
import com.stainedhead.paperclipmobile.application.decision.DismissDecision
import com.stainedhead.paperclipmobile.application.decision.LoadDecision
import com.stainedhead.paperclipmobile.application.decision.PollDecisionExecution

/** The phases shared by the detail screens (decision, approval, budget incident). */
enum class DetailPhase {
    Loading,
    Content,
    Failed,
}

enum class EffectKindUi {
    CommentOnIssue,
    CreateIssue,
    UpdateIssueStatus,
    AssignIssue,
    CancelIssueTree,
    ResolveBlocker,
    Unsupported,
}

/**
 * What one effect will do, as a key and parameters (PRD FR-040). [detail] is the comment excerpt, issue title, status,
 * assignee name, blocker count or raw type; [isStrict] means the effect is skipped if its target changed.
 */
data class EffectSummaryUi(
    val kind: EffectKindUi,
    val targetIssueId: String,
    val detail: String?,
    val isStrict: Boolean,
)

data class OptionUi(
    val id: String,
    val label: String,
    val summary: String?,
    val isDestructive: Boolean,
    val needsDeviceAuth: Boolean,
    val effects: List<EffectSummaryUi>,
)

data class InputUi(
    val id: String,
    val label: String,
    val placeholder: String?,
    val isRequired: Boolean,
    val maxLength: Int?,
    val value: String,
)

enum class EffectStatusUi {
    Claimed,
    Executed,
    Skipped,
    Failed,
    Unknown,
}

/** [reason] is the server's code, for example `target_changed`. */
data class EffectResultUi(
    val index: Int,
    val effectType: String,
    val status: EffectStatusUi,
    val reason: String?,
)

enum class ExecutionStatusUi {
    Running,
    Succeeded,
    Partial,
    Failed,
    Unknown,
}

data class ExecutionUi(
    val status: ExecutionStatusUi,
    val results: List<EffectResultUi>,
    val isPolling: Boolean,
)

enum class DecisionFinalKind {
    Decided,
    Dismissed,
    Expired,
    Cancelled,
    Unknown,
}

/** Shown instead of options once a decision is no longer open (PRD FR-044). */
data class DecisionFinalUi(
    val kind: DecisionFinalKind,
    val chosenOptionLabel: String?,
)

data class DecisionState(
    val phase: DetailPhase = DetailPhase.Loading,
    val title: String = "",
    val body: String = "",
    val originAgentName: String? = null,
    val decideBy: DecideByUi = DecideByUi(DecideByKindUi.None, null),
    val expiresAtEpochMillis: Long? = null,
    val options: List<OptionUi> = emptyList(),
    val inputs: List<InputUi> = emptyList(),
    val selectedOptionId: String? = null,
    val canConfirm: Boolean = false,
    val confirmNeedsDeviceAuth: Boolean = false,
    val canDismiss: Boolean = false,
    /** Snooze in the top bar: only when opened from an Inbox card (ADR-025), by someone who may act. */
    val canSnooze: Boolean = false,
    val snoozeOptions: List<SnoozePresetUi> = emptyList(),
    /** The card was snoozed; the screen should close. */
    val isSnoozed: Boolean = false,
    val finalState: DecisionFinalUi? = null,
    val execution: ExecutionUi? = null,
    val isSubmitting: Boolean = false,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val role: RoleUi? = null,
    val updatedAtEpochMillis: Long? = null,
    /** 15 seconds while the decision is open, 0 once it is final (PRD FR-090). */
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

sealed interface DecisionIntent {
    data class OptionSelected(
        val optionId: String,
    ) : DecisionIntent

    data class InputChanged(
        val inputId: String,
        val value: String,
    ) : DecisionIntent

    data object ConfirmTapped : DecisionIntent

    data object DismissTapped : DecisionIntent

    data class SnoozeRequested(
        val preset: SnoozePresetUi,
    ) : DecisionIntent

    data object Refresh : DecisionIntent

    data object MessageDismissed : DecisionIntent
}

data class DecisionUseCases(
    val resolveContext: ResolveCompanyContext,
    val load: LoadDecision,
    val decide: DecideDecision,
    val dismiss: DismissDecision,
    val poll: PollDecisionExecution,
    val snooze: SnoozeItem,
    val snoozePresets: AvailableSnoozePresets,
)
