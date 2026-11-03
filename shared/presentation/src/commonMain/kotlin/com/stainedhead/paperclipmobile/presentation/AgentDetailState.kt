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
    /** The budget typed so far, in minor units; null while empty or invalid. */
    val budgetAmountMinorUnits: Long? = null,
    val budgetAmountIssue: InvalidKey? = null,
    /** True when the typed budget can be saved now: valid, positive, allowed and nothing else in flight. */
    val canSaveBudget: Boolean = false,
    /** Counts budgets saved; the form closes when it changes, and stays (with its text) when a save fails. */
    val budgetSavedCount: Int = 0,
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

    /** The budget field's text as typed; shared code reads and validates it (A4). */
    data class BudgetAmountTyped(
        val text: String,
        val decimalSeparator: String,
        val fractionDigits: Int,
    ) : AgentDetailIntent

    /** Sends the amount last typed. */
    data object BudgetSubmitted : AgentDetailIntent

    data object MessageDismissed : AgentDetailIntent
}
