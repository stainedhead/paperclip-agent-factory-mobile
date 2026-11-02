package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult

/**
 * Who the signed-in person is in one company and what role they have there (ADR-011). Screens call this once, then
 * pass the [CompanyContext] to every mutation use case. A company the key cannot open is `Forbidden`.
 */
class ResolveCompanyContext(
    private val loadAccess: LoadInstanceAccess,
) {
    suspend operator fun invoke(scope: CompanyScope): DomainResult<CompanyContext> =
        when (val access = loadAccess(scope.instanceId)) {
            is DomainResult.Failure -> {
                access
            }

            is DomainResult.Success -> {
                val company = access.value.companies.firstOrNull { it.id == scope.companyId }
                if (company == null) {
                    DomainResult.Failure(DomainError.Forbidden)
                } else {
                    DomainResult.Success(CompanyContext(scope, company.role, access.value.profile.userId))
                }
            }
        }
}
