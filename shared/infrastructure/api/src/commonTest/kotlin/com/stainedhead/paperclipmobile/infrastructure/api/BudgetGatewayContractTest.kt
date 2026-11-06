package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.testing.contract.BudgetContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-012, AD-014: the shared budget contract, run unchanged against the real adapter and the stub server. */
class BudgetGatewayContractTest : BudgetContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val openIds =
        listOf(
            BudgetIncidentId("b0000001-0000-4000-8000-000000000001"),
            BudgetIncidentId("b0000002-0000-4000-8000-000000000002"),
        )
    override val observed = Money.usdCents(OBSERVED_CENTS)

    override fun create(): BudgetGateway {
        val world = WorkWorld()
        openIds.forEach { id ->
            world.home.budgets.incidents +=
                StubIncident(id.value, ServerFixtures.COMPANY_ACME, "policy-${id.value}", observed = "$OBSERVED_CENTS")
        }
        return world.budget
    }

    @Test
    fun `AD-012 the real adapter passes the budget contract`() = runTest { runAll() }

    private companion object {
        const val OBSERVED_CENTS = 10_000L
    }
}
