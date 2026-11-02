package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

enum class OptionStyle {
    Default,
    Primary,
    Destructive,
    ;

    companion object {
        fun fromServer(raw: String?): OptionStyle =
            when (raw) {
                "primary" -> Primary
                "destructive" -> Destructive
                else -> Default
            }
    }
}

enum class EffectStaleness {
    Strict,
    Lenient,
}

/** What choosing an option will do (PRD FR-040). Unknown effect types are kept so they can still be summarised. */
sealed interface DecisionEffect {
    val targetIssueId: String
    val staleness: EffectStaleness

    data class CommentOnIssue(
        override val targetIssueId: String,
        override val staleness: EffectStaleness,
        val bodyMarkdown: String,
    ) : DecisionEffect

    data class CreateIssue(
        override val targetIssueId: String,
        override val staleness: EffectStaleness,
        val title: String,
    ) : DecisionEffect

    data class UpdateIssueStatus(
        override val targetIssueId: String,
        override val staleness: EffectStaleness,
        val status: String,
    ) : DecisionEffect

    data class AssignIssue(
        override val targetIssueId: String,
        override val staleness: EffectStaleness,
        val assigneeName: String?,
    ) : DecisionEffect

    /** Always strict on the server; always destructive here. */
    data class CancelIssueTree(
        override val targetIssueId: String,
        val reasonComment: String,
    ) : DecisionEffect {
        override val staleness: EffectStaleness get() = EffectStaleness.Strict
    }

    data class ResolveBlocker(
        override val targetIssueId: String,
        override val staleness: EffectStaleness,
        val blockerCount: Int,
    ) : DecisionEffect

    data class Unsupported(
        val raw: String,
        override val targetIssueId: String,
    ) : DecisionEffect {
        override val staleness: EffectStaleness get() = EffectStaleness.Strict
    }
}

data class DecisionOption(
    val id: String,
    val label: String,
    val description: String?,
    val style: OptionStyle,
    val effects: List<DecisionEffect>,
) {
    /** Destructive style OR a `cancel_issue_tree` effect (PRD FR-041). Needs device authentication. */
    val isDestructive: Boolean get() =
        style == OptionStyle.Destructive ||
            effects.any { it is DecisionEffect.CancelIssueTree }
}

/** A field the decider fills in. Attached to the decision, not to an option (research Q8). */
data class DecisionInput(
    val id: String,
    val label: String,
    val placeholder: String? = null,
    val required: Boolean = false,
    val maxLength: Int? = null,
)

sealed interface InputIssue {
    val inputId: String

    data class Required(
        override val inputId: String,
    ) : InputIssue

    data class TooLong(
        override val inputId: String,
    ) : InputIssue
}

enum class DecisionStatus {
    Open,
    Decided,
    Expired,
    Cancelled,
    Unknown,
    ;

    companion object {
        fun fromServer(raw: String): DecisionStatus =
            when (raw) {
                "open" -> Open
                "decided" -> Decided
                "expired" -> Expired
                "cancelled" -> Cancelled
                else -> Unknown
            }
    }
}

enum class ExecutionStatus {
    Running,
    Succeeded,
    Partial,
    Failed,
    Unknown,
    ;

    companion object {
        fun fromServer(raw: String?): ExecutionStatus? =
            when (raw) {
                null -> null
                "running" -> Running
                "succeeded" -> Succeeded
                "partial" -> Partial
                "failed" -> Failed
                else -> Unknown
            }
    }
}

enum class EffectStatus {
    Claimed,
    Executed,
    Skipped,
    Failed,
    Unknown,
    ;

    companion object {
        fun fromServer(raw: String): EffectStatus =
            when (raw) {
                "claimed" -> Claimed
                "executed" -> Executed
                "skipped" -> Skipped
                "failed" -> Failed
                else -> Unknown
            }
    }
}

/** One effect's result. [reason] is the server's code, for example `target_changed`. */
data class EffectResult(
    val index: Int,
    val effectType: String,
    val status: EffectStatus,
    val reason: String? = null,
)

/** What happened after a decision was made (research Q4). */
data class DecisionOutcome(
    val executionStatus: ExecutionStatus?,
    val results: List<EffectResult>,
) {
    val isTerminal: Boolean get() = executionStatus != null && executionStatus != ExecutionStatus.Running
}

/** A decision an agent is waiting on (PRD FR-040). [decideBy] and [originAgentName] are hints (ADR-018). */
data class Decision(
    val id: DecisionId,
    val title: String,
    val body: String,
    val status: DecisionStatus,
    val options: List<DecisionOption>,
    val inputs: List<DecisionInput>,
    val createdAt: Instant,
    val originAgentId: String? = null,
    val originAgentName: String? = null,
    val originIssueId: String? = null,
    val decideBy: DecideBy = DecideBy.None,
    val expiresAt: Instant? = null,
    val chosenOptionId: String? = null,
    val inputValues: Map<String, String> = emptyMap(),
    val dismissed: Boolean = false,
    val outcome: DecisionOutcome? = null,
) {
    val isOpen: Boolean get() = status == DecisionStatus.Open

    /** No longer open: decided (here or elsewhere), expired or cancelled. The screen shows this state, no options. */
    val isFinal: Boolean get() = !isOpen

    /** Decided but the server is still running the effects. */
    val isExecutionPending: Boolean
        get() = status == DecisionStatus.Decided && outcome != null && !outcome.isTerminal

    /** The first problem with [values], or null. Mirrors the server's checks (`decisions.ts:703-704`). */
    fun validateInputs(values: Map<String, String>): InputIssue? =
        inputs.firstNotNullOfOrNull { input ->
            val value = values[input.id].orEmpty()
            when {
                input.required && value.isBlank() -> InputIssue.Required(input.id)
                input.maxLength != null && value.length > input.maxLength -> InputIssue.TooLong(input.id)
                else -> null
            }
        }
}
