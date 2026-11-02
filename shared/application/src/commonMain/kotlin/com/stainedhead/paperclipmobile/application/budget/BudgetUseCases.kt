package com.stainedhead.paperclipmobile.application.budget

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.failed
import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway

/** Loads an active budget incident (PRD FR-052). `NotFound` means it is no longer active. Reads are retried. */
class LoadBudgetIncident(
    private val gateway: BudgetGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: BudgetIncidentId,
    ): DomainResult<BudgetIncident> = retryingRead { gateway.incident(scope, id) }
}

/**
 * Keeps a budget stop in place, or raises the budget and resumes (PRD FR-052). Order (ADR-019): permission (a raise is
 * owner only), status, the amount against the observed spend, device authentication for a raise, then ONE gateway
 * call. The server recomputes spend, so its 422 reason is surfaced as it is. Never retried.
 */
class ResolveBudgetIncident(
    private val gateway: BudgetGateway,
    private val deviceAuth: DeviceAuthGuard,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        incident: BudgetIncident,
        resolution: BudgetResolution,
    ): MutationResult<BudgetIncident> {
        val action = actionFor(resolution)
        refusal(context, incident, resolution, action)?.let { return it }
        val auth = deviceAuth(action)
        if (auth != BiometricResult.Approved) return MutationResult.NotAuthorized(auth)
        return when (val result = gateway.resolve(context.scope, incident.id, resolution, null)) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { readAgain(context, incident) }
        }
    }

    private fun actionFor(resolution: BudgetResolution) =
        if (resolution is BudgetResolution.KeepPaused) Action.KeepBudgetPaused else Action.RaiseBudgetAndResume

    private fun refusal(
        context: CompanyContext,
        incident: BudgetIncident,
        resolution: BudgetResolution,
        action: Action,
    ): MutationResult<Nothing>? =
        when {
            !Permissions.allows(context.role, action) -> MutationResult.Denied
            !incident.isOpen -> MutationResult.Invalid(InvalidInput.WrongStatus)
            else -> incident.validate(resolution)?.let { MutationResult.Invalid(InvalidInput.Amount(it)) }
        }

    /** An incident that left the active list is no longer open; whether it was resolved or dismissed is unknown. */
    private suspend fun readAgain(
        context: CompanyContext,
        incident: BudgetIncident,
    ): DomainResult<BudgetIncident> =
        when (val read = gateway.incident(context.scope, incident.id)) {
            is DomainResult.Success -> {
                read
            }

            is DomainResult.Failure -> {
                if (read.error == DomainError.NotFound) {
                    DomainResult.Success(incident.copy(status = IncidentStatus.Unknown))
                } else {
                    read
                }
            }
        }
}
