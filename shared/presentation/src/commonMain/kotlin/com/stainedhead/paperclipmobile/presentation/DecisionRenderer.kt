package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionEffect
import com.stainedhead.paperclipmobile.domain.DecisionOption
import com.stainedhead.paperclipmobile.domain.DecisionOutcome
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.EffectStaleness
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.Role
import kotlin.time.Instant

/** Everything the Decision screen knows, before it is rendered into [DecisionState]. */
internal data class DecisionModel(
    val context: CompanyContext? = null,
    val decision: Decision? = null,
    val selectedOptionId: String? = null,
    val inputValues: Map<String, String> = emptyMap(),
    val loadedAt: Instant? = null,
    val loading: Boolean = false,
    val polling: Boolean = false,
    val submitting: Boolean = false,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
) {
    fun withSelection(optionId: String): DecisionModel =
        if (decision?.options?.any { it.id == optionId } == true) {
            copy(selectedOptionId = optionId, feedback = null)
        } else {
            this
        }

    /** Shows [decision], filling the agent name and decide-by from the Inbox card's hints when it has none. */
    fun withDecision(
        decision: Decision,
        hints: DecisionTarget,
        now: Instant,
    ): DecisionModel {
        val hinted =
            decision.copy(
                originAgentName = decision.originAgentName ?: hints.originAgentNameHint,
                decideBy = decision.decideBy.takeIf { it != DecideBy.None } ?: DecideBy.parse(hints.decideByHint),
            )
        return copy(
            decision = hinted,
            loadedAt = now,
            selectedOptionId = selectedOptionId.takeIf { hinted.isOpen },
            error = null,
        )
    }

    fun mayAct(
        action: Action,
        online: Boolean,
    ): Boolean {
        val role = context?.role ?: return false
        return !submitting && online && Permissions.allows(role, action)
    }
}

private const val EXCERPT_LENGTH = 120

/** Holds the Decision screen's display rules (PRD FR-040 to FR-044). */
internal object DecisionRenderer {
    fun render(
        model: DecisionModel,
        online: Boolean,
    ): DecisionState {
        val decision = model.decision
        val role = model.context?.role
        val open = decision?.takeIf { it.isOpen }
        val selected = open?.options?.firstOrNull { it.id == model.selectedOptionId }
        val allowed = !model.submitting && online && role != null && role != Role.Viewer
        return DecisionState(
            phase = phase(decision != null, model.loading),
            title = decision?.title.orEmpty(),
            body = decision?.body.orEmpty(),
            originAgentName = decision?.originAgentName,
            decideBy = (decision?.decideBy ?: DecideBy.None).toUi(),
            expiresAtEpochMillis = decision?.expiresAt?.toEpochMilliseconds(),
            options = open?.options.orEmpty().map(::option),
            inputs = open?.inputs.orEmpty().map { input(it, model.inputValues[it.id].orEmpty()) },
            selectedOptionId = selected?.id,
            canConfirm = selected != null && allowed && Permissions.allows(role, Action.DecideOption),
            confirmNeedsDeviceAuth = selected?.isDestructive == true,
            canDismiss = open != null && allowed,
            finalState = decision?.takeIf { it.isFinal }?.let(::finalState),
            execution = decision?.outcome?.let { execution(it, model.polling) },
            isSubmitting = model.submitting,
            isOffline = !online,
            actionsDisabledReason = disabledReason(online, role),
            role = role?.toUi(),
            updatedAtEpochMillis = model.loadedAt?.toEpochMilliseconds(),
            error = model.error,
            feedback = model.feedback,
        )
    }

    private fun phase(
        hasContent: Boolean,
        loading: Boolean,
    ) = when {
        hasContent -> DetailPhase.Content
        loading -> DetailPhase.Loading
        else -> DetailPhase.Failed
    }

    private fun disabledReason(
        online: Boolean,
        role: Role?,
    ) = when {
        !online -> ActionsDisabledReason.Offline
        role == Role.Viewer -> ActionsDisabledReason.ViewerRole
        else -> null
    }

    private fun option(option: DecisionOption) =
        OptionUi(
            id = option.id,
            label = option.label,
            description = option.description,
            isDestructive = option.isDestructive,
            needsDeviceAuth = option.isDestructive,
            effects = option.effects.map(::effect),
        )

    private fun input(
        input: com.stainedhead.paperclipmobile.domain.DecisionInput,
        value: String,
    ) = InputUi(input.id, input.label, input.placeholder, input.required, input.maxLength, value)

    fun effect(effect: DecisionEffect): EffectSummaryUi {
        val strict = effect.staleness == EffectStaleness.Strict
        val (kind, detail) =
            when (effect) {
                is DecisionEffect.CommentOnIssue -> {
                    EffectKindUi.CommentOnIssue to effect.bodyMarkdown.take(EXCERPT_LENGTH)
                }

                is DecisionEffect.CreateIssue -> {
                    EffectKindUi.CreateIssue to effect.title
                }

                is DecisionEffect.UpdateIssueStatus -> {
                    EffectKindUi.UpdateIssueStatus to effect.status
                }

                is DecisionEffect.AssignIssue -> {
                    EffectKindUi.AssignIssue to effect.assigneeName
                }

                is DecisionEffect.CancelIssueTree -> {
                    EffectKindUi.CancelIssueTree to effect.reasonComment.take(EXCERPT_LENGTH)
                }

                is DecisionEffect.ResolveBlocker -> {
                    EffectKindUi.ResolveBlocker to effect.blockerCount.toString()
                }

                is DecisionEffect.Unsupported -> {
                    EffectKindUi.Unsupported to effect.raw
                }
            }
        return EffectSummaryUi(kind, effect.targetIssueId, detail, strict)
    }

    private fun finalState(decision: Decision): DecisionFinalUi {
        val chosen = decision.options.firstOrNull { it.id == decision.chosenOptionId }?.label
        val kind =
            when (decision.status) {
                DecisionStatus.Decided -> {
                    if (decision.dismissed) DecisionFinalKind.Dismissed else DecisionFinalKind.Decided
                }

                DecisionStatus.Expired -> {
                    DecisionFinalKind.Expired
                }

                DecisionStatus.Cancelled -> {
                    DecisionFinalKind.Cancelled
                }

                else -> {
                    DecisionFinalKind.Unknown
                }
            }
        return DecisionFinalUi(kind, chosen)
    }

    private fun execution(
        outcome: DecisionOutcome,
        polling: Boolean,
    ) = ExecutionUi(
        status = ExecutionStatusUi.entries[(outcome.executionStatus ?: ExecutionStatus.Running).ordinal],
        results = outcome.results.map { EffectResultUi(it.index, it.effectType, it.status.toUi(), it.reason) },
        isPolling = polling,
    )
}

private fun com.stainedhead.paperclipmobile.domain.EffectStatus.toUi() = EffectStatusUi.entries[ordinal]
