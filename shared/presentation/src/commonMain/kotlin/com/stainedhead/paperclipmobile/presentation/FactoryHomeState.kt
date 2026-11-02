package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.dashboard.LoadDashboard
import com.stainedhead.paperclipmobile.application.dashboard.LoadWindowSpend

/** The tiles of Factory home (PRD FR-078). */
data class FactoryTilesUi(
    val agentsActive: Int,
    val agentsRunning: Int,
    val agentsPaused: Int,
    val agentsError: Int,
    val tasksOpen: Int,
    val tasksInProgress: Int,
    val tasksBlocked: Int,
    val monthSpend: MoneyUi,
    val monthBudget: MoneyUi,
    val hasBudget: Boolean,
    /** Whole percent of the month budget spent; 0 without a budget. */
    val monthPercentUsed: Int,
    val pendingApprovals: Int,
    val budgetIncidents: Int,
)

data class FactoryHomeState(
    val phase: DetailPhase = DetailPhase.Loading,
    val tiles: FactoryTilesUi? = null,
    val liveRuns: List<RunRowUi> = emptyList(),
    val spend: List<SpendUi> = emptyList(),
    val spendUnavailable: Boolean = false,
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val updatedAtEpochMillis: Long? = null,
    /** How often a visible screen should send [FactoryHomeIntent.Refresh] (PRD FR-090). */
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
)

sealed interface FactoryHomeIntent {
    data object Refresh : FactoryHomeIntent
}

data class FactoryHomeUseCases(
    val resolveContext: ResolveCompanyContext,
    val load: LoadDashboard,
    val spend: LoadWindowSpend,
)
