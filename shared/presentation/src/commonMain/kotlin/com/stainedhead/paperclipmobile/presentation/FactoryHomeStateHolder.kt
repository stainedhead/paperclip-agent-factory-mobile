package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.DomainResult
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

/** State holder for Factory home (screen 5): tiles, runs in progress, rolling spend (PRD FR-078, FR-095). */
class FactoryHomeStateHolder(
    private val useCases: FactoryHomeUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
) {
    private val scope = childScope(scope)
    private val companyScope = target.toScope()
    private val context = ContextSource(useCases.resolveContext, companyScope)
    private val model = MutableStateFlow(FactoryHomeState())
    private val mutableState = MutableStateFlow(FactoryHomeState())
    val state: StateFlow<FactoryHomeState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(FactoryHomeIntent.Refresh) },
            {
                true
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> model.copy(isOffline = !online) }
                .collect { mutableState.value = it }
        }
        refresh()
    }

    fun onIntent(intent: FactoryHomeIntent) {
        when (intent) {
            FactoryHomeIntent.Refresh -> refresh()
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun refresh() {
        if (!connectivity.isOnline.value) {
            model.update { it.copy(phase = if (it.tiles == null) DetailPhase.Failed else it.phase) }
            return
        }
        if (model.value.isRefreshing) return
        model.update { it.copy(isRefreshing = true, error = null) }
        scope.launch {
            val allowed = context.get()
            if (allowed is DomainResult.Failure) {
                fail(allowed.error.toErrorUi())
            } else {
                when (val home = useCases.load(companyScope)) {
                    is DomainResult.Failure -> fail(home.error.toErrorUi())
                    is DomainResult.Success -> show(home.value.summary, home.value.liveRuns.map { it.toRow() })
                }
            }
            model.update { it.copy(isRefreshing = false) }
        }
    }

    private fun fail(error: ErrorUi) {
        model.update { it.copy(phase = if (it.tiles == null) DetailPhase.Failed else it.phase, error = error) }
    }

    private suspend fun show(
        summary: DashboardSummary,
        runs: List<RunRowUi>,
    ) {
        val spend = useCases.spend(companyScope)
        model.update {
            it.copy(
                phase = DetailPhase.Content,
                tiles = summary.toTiles(),
                liveRuns = runs,
                spend = (spend as? DomainResult.Success)?.value?.map { window -> window.toUi() } ?: it.spend,
                spendUnavailable = spend is DomainResult.Failure,
                updatedAtEpochMillis = clock.now().toEpochMilliseconds(),
            )
        }
    }
}

private fun DashboardSummary.toTiles() =
    FactoryTilesUi(
        agentsActive = agents.active,
        agentsRunning = agents.running,
        agentsPaused = agents.paused,
        agentsError = agents.error,
        tasksOpen = tasks.open,
        tasksInProgress = tasks.inProgress,
        tasksBlocked = tasks.blocked,
        monthSpend = monthSpend.toUi(),
        monthBudget = monthBudget.toUi(),
        hasBudget = hasBudget,
        monthPercentUsed = monthPercentUsed,
        pendingApprovals = pendingApprovals,
        budgetIncidents = activeBudgetIncidents,
    )
