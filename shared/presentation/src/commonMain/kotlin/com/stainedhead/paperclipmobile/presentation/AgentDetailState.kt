package com.stainedhead.paperclipmobile.presentation

data class AgentDetailState(
    val phase: DetailPhase = DetailPhase.Loading,
    val agent: AgentRowUi? = null,
    val reportsToName: String? = null,
    val currentRun: RunRowUi? = null,
    val currentIssue: IssueRefUi? = null,
    val recentRuns: List<RunRowUi> = emptyList(),
    val monthSpend: MoneyUi? = null,
    val monthlyBudget: MoneyUi? = null,
    val budgetPercentUsed: Int = 0,
    val actions: List<AgentActionUi> = emptyList(),
    /** Owner only (PRD FR-071). */
    val canSetBudget: Boolean = false,
    val confirmPause: Boolean = false,
    val isBusy: Boolean = false,
    val role: RoleUi? = null,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    /** Why Set budget is disabled when the other actions are not: an operator is not an owner. */
    val budgetDisabledReason: ActionsDisabledReason? = null,
    val updatedAtEpochMillis: Long? = null,
    /** 5 seconds while the agent has a run in progress, otherwise 15 (PRD FR-090). */
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

sealed interface AgentDetailIntent {
    data object Refresh : AgentDetailIntent

    data object PauseTapped : AgentDetailIntent

    data object PauseConfirmed : AgentDetailIntent

    data object PauseCancelled : AgentDetailIntent

    data object ResumeTapped : AgentDetailIntent

    data object RunNowTapped : AgentDetailIntent

    /** The new monthly budget in cents. */
    data class BudgetSubmitted(
        val minorUnits: Long,
    ) : AgentDetailIntent

    data object MessageDismissed : AgentDetailIntent
}
