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
            Action.RemoveFactory,
        )

    fun requires(action: Action): Boolean = action in destructive
}
