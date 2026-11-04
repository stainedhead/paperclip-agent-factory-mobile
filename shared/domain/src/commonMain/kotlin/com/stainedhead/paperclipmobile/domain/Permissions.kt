package com.stainedhead.paperclipmobile.domain

/**
 * The single Role x Action table (PRD "Users and Roles"). The server stays the authority; this decides what the UI
 * offers.
 */
object Permissions {
    private val ownerOnly = setOf(Action.RaiseBudgetAndResume, Action.SetAgentBudget, Action.RaiseAgentBudget)

    fun allows(
        role: Role,
        action: Action,
    ): Boolean =
        when {
            action.scope == ActionScope.Local -> true
            role == Role.Owner -> true
            role == Role.Operator -> action !in ownerOnly
            else -> false
        }
}

/** Which actions need device authentication. There is no setting that changes this (PRD FR-014). */
object DeviceAuthPolicy {
    private val destructive =
        setOf(
            Action.PauseAgent,
            Action.CancelRun,
            Action.RejectApproval,
            Action.DecideDestructiveOption,
            Action.RaiseBudgetAndResume,
            Action.RaiseAgentBudget,
            Action.RemoveFactory,
        )

    fun requires(action: Action): Boolean = action in destructive

    /**
     * Which action setting an agent's monthly budget to [new] counts as (DEC-1, R-004): more than the [current] amount
     * lets the agent spend more, and zero stops it in practice, so both are [Action.RaiseAgentBudget]; anything else is
     * a plain [Action.SetAgentBudget]. Both amounts are in the same currency.
     */
    fun budgetSetAction(
        current: Money,
        new: Money,
    ): Action = if (new.minorUnits == 0L || new > current) Action.RaiseAgentBudget else Action.SetAgentBudget
}
