package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.budget.LoadBudgetIncident
import com.stainedhead.paperclipmobile.application.budget.ResolveBudgetIncident

enum class BudgetThresholdUi {
    Hard,
    Soft,
}

enum class BudgetChoice {
    KeepPaused,
    RaiseAndResume,
}

/** How a budget incident ended, once it is no longer open. */
enum class BudgetOutcomeUi {
    KeptPaused,
    Raised,

    /** Gone from the server's active list: resolved here or elsewhere. */
    NoLongerActive,
}

data class BudgetIncidentState(
    val phase: DetailPhase = DetailPhase.Loading,
    val scopeName: String = "",
    val scopeType: String = "",
    val threshold: BudgetThresholdUi? = null,
    val observed: MoneyUi? = null,
    val limit: MoneyUi? = null,
    /** Whole percent of the limit already used, rounded down. */
    val observedPercent: Int = 0,
    val outcome: BudgetOutcomeUi? = null,
    val choice: BudgetChoice? = null,
    val amountMinorUnits: Long? = null,
    /** Set while the typed amount cannot be accepted (PRD FR-052). */
    val amountIssue: InvalidKey? = null,
    val canKeepPaused: Boolean = false,
    /** Owner only (PRD "Users and Roles"). */
    val canRaise: Boolean = false,
    /** Why Raise is disabled when the rest of the sheet is not: an operator is not an owner. */
    val raiseDisabledReason: ActionsDisabledReason? = null,
    val canSubmit: Boolean = false,
    val submitNeedsDeviceAuth: Boolean = false,
    val isSubmitting: Boolean = false,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val role: RoleUi? = null,
    val updatedAtEpochMillis: Long? = null,
    /** 15 seconds while the incident is open, 0 once it is resolved (PRD FR-090). */
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
) {
    /** True while a change is in flight; the screen must not be torn down yet. */
    val isWorking: Boolean get() = isSubmitting
}

sealed interface BudgetIntent {
    data class ChoiceSelected(
        val choice: BudgetChoice,
    ) : BudgetIntent

    /**
     * The amount field's text as typed. The app passes only the locale's decimal separator and the currency's fraction
     * digits; shared code reads and validates it (A4).
     */
    data class AmountTyped(
        val text: String,
        val decimalSeparator: String,
        val fractionDigits: Int,
    ) : BudgetIntent

    data object SubmitTapped : BudgetIntent

    data object Refresh : BudgetIntent

    data object MessageDismissed : BudgetIntent
}

data class BudgetUseCases(
    val resolveContext: ResolveCompanyContext,
    val load: LoadBudgetIncident,
    val resolve: ResolveBudgetIncident,
)
