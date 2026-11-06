package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.testing.contract.DashboardContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-013, AD-014: the shared dashboard contract, run unchanged against the real adapter and the stub server. */
class DashboardGatewayContractTest : DashboardContract() {
    override val scope: CompanyScope = WorkWorld().acme

    override fun create(): DashboardGateway {
        val world = WorkWorld()
        world.home.dashboard.spend +=
            listOf(
                StubSpendRow("anthropic", "5h", "1200"),
                StubSpendRow("anthropic", "24h", "8400"),
                StubSpendRow("openai", "24h", "600"),
                StubSpendRow("anthropic", "7d", "61000"),
            )
        return world.dashboard
    }

    @Test
    fun `AD-013 the real adapter passes the dashboard contract`() = runTest { runAll() }
}
