package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The decision to show. [originAgentNameHint] and [decideByHint] are what the Inbox card knew when the person tapped
 * it: the decision endpoint has neither (ADR-018). Pass null for no hint. [dismissalKey] is the card's snooze key.
 */
data class DecisionTarget(
    val company: CompanyTarget,
    val decisionId: String,
    val originAgentNameHint: String?,
    val decideByHint: String?,
    /** The `dismissalKey` of the Inbox card the screen was opened from; null hides Snooze (ADR-025). */
    val dismissalKey: String?,
)

/** State holder for Decision detail (screen 2). */
class DecisionStateHolder(
    private val useCases: DecisionUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    private val target: DecisionTarget,
) {
    private val scope = childScope(scope)
    private val companyScope = CompanyScope(InstanceId(target.company.instanceId), CompanyId(target.company.companyId))
    private val id = DecisionId(target.decisionId)
    private val model = MutableStateFlow(DecisionModel(snoozeKey = target.dismissalKey))
    private val mutableState = MutableStateFlow(DecisionState())
    val state: StateFlow<DecisionState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(DecisionIntent.Refresh) },
            {
                it == Invalidation.AttentionFeed
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online ->
                DecisionRenderer.render(model, online, useCases.snoozePresets().map { it.toUi() })
            }.collect { mutableState.value = it }
        }
        load()
    }

    fun onIntent(intent: DecisionIntent) {
        when (intent) {
            is DecisionIntent.OptionSelected -> {
                model.update { it.withSelection(intent.optionId) }
            }

            is DecisionIntent.InputChanged -> {
                model.update {
                    it.copy(
                        inputValues =
                            it.inputValues + (intent.inputId to intent.value),
                    )
                }
            }

            DecisionIntent.ConfirmTapped -> {
                confirm()
            }

            DecisionIntent.DismissTapped -> {
                dismiss()
            }

            is DecisionIntent.SnoozeRequested -> {
                snooze(intent.preset)
            }

            DecisionIntent.Refresh -> {
                load()
            }

            DecisionIntent.MessageDismissed -> {
                model.update { it.copy(feedback = null) }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun load() {
        if (!connectivity.isOnline.value || model.value.loading) return
        model.update { it.copy(loading = true, error = null) }
        scope.launch {
            val context = ensureContext()
            if (context != null) {
                when (val result = useCases.load(companyScope, id)) {
                    is DomainResult.Success -> show(result.value)
                    is DomainResult.Failure -> model.update { it.copy(error = result.error.toErrorUi()) }
                }
            }
            model.update { it.copy(loading = false) }
        }
    }

    private suspend fun ensureContext() =
        model.value.context ?: when (val result = useCases.resolveContext(companyScope)) {
            is DomainResult.Success -> {
                result.value.also { context -> model.update { it.copy(context = context) } }
            }

            is DomainResult.Failure -> {
                model.update { it.copy(error = result.error.toErrorUi()) }
                null
            }
        }

    private fun confirm() {
        val current = model.value
        val decision = current.decision ?: return
        val optionId = current.selectedOptionId ?: return
        if (!current.mayAct(Action.DecideOption, connectivity.isOnline.value) || !decision.isOpen) return
        act { context -> useCases.decide(context, decision.id, optionId, current.inputValues) }
    }

    private fun dismiss() {
        val decision = model.value.decision ?: return
        if (!model.value.mayAct(Action.DismissDecision, connectivity.isOnline.value) || !decision.isOpen) return
        act { context -> useCases.dismiss(context, decision, null) }
    }

    private fun snooze(preset: SnoozePresetUi) {
        val key = target.dismissalKey ?: return
        val current = model.value
        if (!current.mayAct(Action.SnoozeItem, connectivity.isOnline.value)) return
        if (current.decision?.isOpen != true || current.snoozed) return
        model.update { it.copy(submitting = true, feedback = null) }
        scope.launch {
            val context = ensureContext()
            val result = context?.let { useCases.snooze(it, key, preset.toDomain()) }
            model.update {
                it.copy(
                    submitting = false,
                    snoozed = result is MutationResult.Done,
                    feedback = result?.toFeedback(),
                )
            }
        }
    }

    private fun act(
        action: suspend (com.stainedhead.paperclipmobile.domain.CompanyContext) -> MutationResult<Decision>,
    ) {
        model.update { it.copy(submitting = true, feedback = null) }
        scope.launch {
            val context = ensureContext()
            val result = if (context == null) null else action(context)
            model.update { it.copy(submitting = false, feedback = result?.toFeedback()) }
            when (result) {
                is MutationResult.Done -> show(result.value)
                is MutationResult.Reconciled -> show(result.current)
                else -> Unit
            }
        }
    }

    private suspend fun show(decision: Decision) {
        model.update { it.withDecision(decision, target, clock.now()) }
        if (model.value.decision?.isExecutionPending == true) poll()
    }

    private suspend fun poll() {
        model.update { it.copy(polling = true) }
        val result = useCases.poll(companyScope, id)
        model.update { it.copy(polling = false) }
        when (result) {
            is DomainResult.Success -> model.update { it.withDecision(result.value, target, clock.now()) }
            is DomainResult.Failure -> model.update { it.copy(error = result.error.toErrorUi()) }
        }
    }
}
