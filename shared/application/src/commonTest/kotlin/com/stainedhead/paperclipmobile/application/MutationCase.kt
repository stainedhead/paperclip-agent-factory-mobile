package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Role

/**
 * One row per mutation use case (and per distinct [actions] set of one use case). The Konsist rule
 * `MutationMatrixRulesTest` fails when a class whose `invoke` takes a `CompanyContext` and returns a `MutationResult`
 * has no row (it looks for the quoted [useCase] name), and `MutationRoleMatrixTest` fails when a company-scoped
 * `Action` is in no row, so a new mutation cannot be forgotten.
 *
 * [method] is the gateway call the mutation makes, [calls] and [failures] are that gateway's counters and scripts.
 */
internal class MutationCase(
    val useCase: String,
    val actions: Set<Action>,
    val method: String,
    val calls: (Env) -> Map<String, Int>,
    val failures: (Env) -> MutableMap<String, DomainError>,
    val run: suspend (Env, Role) -> MutationResult<*>,
) {
    /** The roles that reach the gateway; the server stays the authority, this is the Role x Action table. */
    fun allowedRoles(): Set<Role> =
        if (actions.any { it in OWNER_ONLY }) setOf(Role.Owner) else setOf(Role.Owner, Role.Operator)

    override fun toString() = "$useCase$actions"

    private companion object {
        val OWNER_ONLY = setOf(Action.RaiseBudgetAndResume, Action.SetAgentBudget, Action.RaiseAgentBudget)
    }
}
