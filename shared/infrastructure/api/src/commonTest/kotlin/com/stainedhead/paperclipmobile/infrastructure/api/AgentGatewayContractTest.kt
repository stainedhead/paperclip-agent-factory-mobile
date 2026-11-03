package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.testing.contract.AgentContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-013, AD-014: the shared agent contract, run unchanged against the real adapter and the stub server. */
class AgentGatewayContractTest : AgentContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val runningAgent = AgentId(ENGINEER)
    override val unknownAgent = AgentId("no-such-agent")

    override fun create(): AgentGateway {
        val world = WorkWorld()
        world.home.agents.agents +=
            listOf(
                StubAgent(ENGINEER, ServerFixtures.COMPANY_ACME),
                StubAgent(CTO, ServerFixtures.COMPANY_ACME, "CTO", "cto", "idle"),
            )
        return world.agents
    }

    @Test
    fun `AD-013 the real adapter passes the agent contract`() = runTest { runAll() }

    private companion object {
        const val ENGINEER = "e0000001-0000-4000-8000-000000000001"
        const val CTO = "e0000002-0000-4000-8000-000000000002"
    }
}
