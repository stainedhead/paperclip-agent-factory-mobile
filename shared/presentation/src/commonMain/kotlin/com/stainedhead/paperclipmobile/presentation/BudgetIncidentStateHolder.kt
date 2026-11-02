package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.Money
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

/** State holder for the Budget incident sheet (screen 12): keep paused, or raise the budget and resume (PRD FR-052). */
class BudgetIncidentStateHolder(
    private val useCases: BudgetUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
    incidentId: String,
) {
    private val scope = childScope(scope)
    private val companyScope = CompanyScope(InstanceId(target.instanceId), CompanyId(target.companyId))
    private val id = BudgetIncidentId(incidentId)
    private val model = MutableStateFlow(BudgetModel())
    private val mutableState = MutableStateFlow(BudgetIncidentState())
    val state: StateFlow<BudgetIncidentState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(BudgetIntent.Refresh) },
            {
                it == Invalidation.AttentionFeed
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> BudgetIncidentRenderer.render(model, online) }
                .collect { mutableState.value = it }
        }
        load()
    }

    fun onIntent(intent: BudgetIntent) {
        when (intent) {
            is BudgetIntent.ChoiceSelected -> {
                choose(intent.choice)
            }

            is BudgetIntent.AmountChanged -> {
                model.update {
                    it.copy(
                        amount =
                            intent.minorUnits?.takeIf { cents ->
                                cents >=
                                    0
                            },
                        feedback = null,
                    )
                }
            }

            BudgetIntent.SubmitTapped -> {
                submit()
            }

            BudgetIntent.Refresh -> {
                load()
            }

            BudgetIntent.MessageDismissed -> {
                model.update { it.copy(feedback = null) }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun load() {
        if (!connectivity.isOnline.value) return
        if (!model.claim({ it.loading }) { it.copy(loading = true, error = null) }) return
        val started = model.value.version
        scope.launch {
            if (ensureContext() != null) {
                when (val result = useCases.load(companyScope, id)) {
                    is DomainResult.Success -> {
                        model.update {
                            if (it.version != started) {
                                it
                            } else {
                                it.copy(
                                    incident = result.value,
                                    outcome = BudgetIncidentRenderer.outcomeOf(result.value.status),
                                    loadedAt = clock.now(),
                                )
                            }
                        }
                    }

                    is DomainResult.Failure -> {
                        if (model.value.version == started) noLongerActiveOrError(result.error)
                    }
                }
            }
            model.update { it.copy(loading = false) }
        }
    }

    /** A resolved incident leaves the server's active list, so `NotFound` must not erase what the person just did. */
    private suspend fun noLongerActiveOrError(error: DomainError) {
        if (error == DomainError.NotFound) {
            model.update {
                val kept = it.outcome?.takeIf { outcome -> outcome != BudgetOutcomeUi.NoLongerActive }
                it.copy(outcome = kept ?: BudgetOutcomeUi.NoLongerActive, loadedAt = clock.now())
            }
        } else {
            model.update { it.copy(error = error.toErrorUi()) }
            if (error == DomainError.Forbidden) {
                model.refreshContext(useCases.resolveContext, companyScope) { copy(context = it) }
            }
        }
    }

    private suspend fun ensureContext(): CompanyContext? =
        model.value.context ?: when (val result = useCases.resolveContext(companyScope)) {
            is DomainResult.Success -> {
                result.value.also { context -> model.update { it.copy(context = context) } }
            }

            is DomainResult.Failure -> {
                model.update { it.copy(error = result.error.toErrorUi()) }
                null
            }
        }

    private fun choose(choice: BudgetChoice) {
        val state = BudgetIncidentRenderer.render(model.value, connectivity.isOnline.value)
        val allowed = if (choice == BudgetChoice.KeepPaused) state.canKeepPaused else state.canRaise
        if (allowed) model.update { it.copy(choice = choice, feedback = null) }
    }

    private fun submit() {
        val current = model.value
        val incident = current.incident ?: return
        val resolution = resolutionOf(current, incident) ?: return
        if (!BudgetIncidentRenderer.render(current, connectivity.isOnline.value).canSubmit) return
        if (!model.claim({ it.submitting }) { it.copy(submitting = true, feedback = null) }) return
        scope.launch {
            val context = ensureContext()
            val result = context?.let { useCases.resolve(it, incident, resolution) }
            model.update { it.copy(submitting = false, feedback = result?.toFeedback(), version = it.version + 1) }
            if (result?.wasForbidden() ==
                true
            ) {
                model.refreshContext(useCases.resolveContext, companyScope) { copy(context = it) }
            }
            when (result) {
                is MutationResult.Done -> show(result.value)
                is MutationResult.Reconciled -> show(result.current)
                else -> Unit
            }
        }
    }

    private fun resolutionOf(
        current: BudgetModel,
        incident: BudgetIncident,
    ): BudgetResolution? =
        when (current.choice) {
            BudgetChoice.KeepPaused -> {
                BudgetResolution.KeepPaused
            }

            BudgetChoice.RaiseAndResume -> {
                current.amount?.let {
                    BudgetResolution.RaiseAndResume(Money(it, incident.observed.currency))
                }
            }

            null -> {
                null
            }
        }

    private fun show(incident: BudgetIncident) {
        model.update {
            it.copy(
                incident = incident,
                outcome = BudgetIncidentRenderer.outcomeOf(incident.status),
                loadedAt = clock.now(),
                error = null,
            )
        }
    }
}
