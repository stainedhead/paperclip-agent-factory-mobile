package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository

/** Where a factory stands after its companies were read from the server (F-1, ADR-150). */
sealed interface ActiveCompanyOutcome {
    /** [companyId] is the company the tabs show. */
    data class Ready(
        val companyId: CompanyId,
    ) : ActiveCompanyOutcome

    /** The key opens no company: nothing exists yet, or this person is a member of none. */
    data object NoCompany : ActiveCompanyOutcome

    /** [count] companies exist and none was ever chosen on this phone; the person chooses, the app never guesses. */
    data class ChooseCompany(
        val count: Int,
    ) : ActiveCompanyOutcome

    /** The companies could not be read. */
    data class Failed(
        val error: DomainError,
    ) : ActiveCompanyOutcome
}

/**
 * Reads the factory's companies and settles which one is active, so a factory with no company reads as a state and not
 * as a screen that waits forever. An active company that still exists is kept. With none (or one that is gone) and
 * exactly one company on the server, that one is stored. With several it does not pick (INTENT principle 2): the person
 * chooses on Factories. With none the outcome is [ActiveCompanyOutcome.NoCompany].
 */
class ResolveActiveCompany(
    private val repository: InstanceRepository,
    private val loadAccess: LoadInstanceAccess,
    private val switchCompany: SwitchCompany,
) {
    suspend operator fun invoke(instanceId: InstanceId): ActiveCompanyOutcome {
        val instance = repository.get(instanceId) ?: return ActiveCompanyOutcome.Failed(DomainError.NotFound)
        val companies =
            when (val access = loadAccess(instanceId)) {
                is DomainResult.Failure -> return ActiveCompanyOutcome.Failed(access.error)
                is DomainResult.Success -> access.value.companies.map { it.id }
            }
        val active = instance.activeCompanyId
        return when {
            companies.isEmpty() -> ActiveCompanyOutcome.NoCompany
            active != null && active in companies -> ActiveCompanyOutcome.Ready(active)
            companies.size == 1 -> adopt(instanceId, companies.single())
            else -> ActiveCompanyOutcome.ChooseCompany(companies.size)
        }
    }

    private suspend fun adopt(
        instanceId: InstanceId,
        company: CompanyId,
    ): ActiveCompanyOutcome =
        when (switchCompany(instanceId, company)) {
            SwitchResult.Switched -> ActiveCompanyOutcome.Ready(company)
            SwitchResult.NotFound -> ActiveCompanyOutcome.Failed(DomainError.NotFound)
        }
}
