package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.testing.AttentionFixtures

/**
 * A budget endpoint with the server's rules (research Q5): only active incidents are readable (a resolved one is
 * `NotFound`), a raise must exceed [serverObserved] (which can be higher than the incident's own figure) or the call
 * is Rejected with the server's text, and keep paused dismisses. [failures] scripts errors by operation name.
 */
class FakeBudgetGateway(
    incidents: List<BudgetIncident> = listOf(AttentionFixtures.incident()),
) : BudgetGateway {
    private val store = incidents.associateBy { it.id }.toMutableMap()
    val calls = mutableMapOf<String, Int>()
    val failures = mutableMapOf<String, DomainError>()
    val resolutions = mutableListOf<BudgetResolution>()
    var serverObservedMinor: Long? = null

    /** Called with the incident's id once it is resolved or dismissed, so a demo feed can drop its card. */
    var onResolved: ((String) -> Unit)? = null

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun incident(
        scope: CompanyScope,
        id: BudgetIncidentId,
    ): DomainResult<BudgetIncident> {
        record("incident")?.let { return DomainResult.Failure(it) }
        val found = store[id]
        return if (found != null && found.isOpen) {
            DomainResult.Success(found)
        } else {
            DomainResult.Failure(DomainError.NotFound)
        }
    }

    override suspend fun resolve(
        scope: CompanyScope,
        id: BudgetIncidentId,
        resolution: BudgetResolution,
        note: String?,
    ): DomainResult<BudgetIncident> {
        resolutions += resolution
        record("resolve")?.let { return DomainResult.Failure(it) }
        val current = store[id]?.takeIf { it.isOpen } ?: return DomainResult.Failure(DomainError.NotFound)
        val observed = serverObservedMinor ?: current.observed.minorUnits
        if (resolution is BudgetResolution.RaiseAndResume && resolution.amount.minorUnits <= observed) {
            return DomainResult.Failure(DomainError.Rejected("New budget must exceed current observed spend"))
        }
        val raised = resolution is BudgetResolution.RaiseAndResume
        val status = if (raised) IncidentStatus.Resolved else IncidentStatus.Dismissed
        return DomainResult.Success(
            current.copy(status = status).also {
                store[id] = it
                onResolved?.invoke(id.value)
            },
        )
    }
}
