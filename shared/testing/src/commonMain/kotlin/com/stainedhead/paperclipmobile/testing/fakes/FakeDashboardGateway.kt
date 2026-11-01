package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.SpendRow
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.testing.AgentFixtures

/** Factory home data. [failures] scripts errors by operation name. */
class FakeDashboardGateway(
    var summary: DashboardSummary = AgentFixtures.summary(),
    var rows: List<SpendRow> = AgentFixtures.spendRows(),
) : DashboardGateway {
    val calls = mutableMapOf<String, Int>()
    val failures = mutableMapOf<String, DomainError>()

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun dashboard(scope: CompanyScope): DomainResult<DashboardSummary> =
        record("dashboard")?.let { DomainResult.Failure(it) } ?: DomainResult.Success(summary)

    override suspend fun windowSpend(scope: CompanyScope): DomainResult<List<SpendRow>> =
        record("windowSpend")?.let { DomainResult.Failure(it) } ?: DomainResult.Success(rows)
}
