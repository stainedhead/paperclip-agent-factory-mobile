package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.testing.contract.DecisionContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-012, AD-014: the shared decision contract, run unchanged against the real adapter and the stub server. */
class DecisionGatewayContractTest : DecisionContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val openIds = (1..5).map { DecisionId("dddddddd-0000-4000-8000-00000000000$it") }
    override val validOptionId = "ship"
    override val otherOptionId = "cancel"
    override val inputId = "note"
    override val effectLessOptionId = "hold"

    override fun create(): DecisionGateway {
        val world = WorkWorld()
        openIds.forEachIndexed { index, id ->
            val standard = DecisionFixtures.standardOptions()
            world.home.decisions.decisions +=
                when (index) {
                    2 -> StubDecision(id.value, ServerFixtures.COMPANY_ACME, inputs = DecisionFixtures.inputs("note"))
                    4 -> StubDecision(id.value, ServerFixtures.COMPANY_ACME, options = listOf(standard[0], standard[2]))
                    else -> StubDecision(id.value, ServerFixtures.COMPANY_ACME)
                }
        }
        return world.decisions
    }

    @Test
    fun `AD-012 the real adapter passes the decision contract`() = runTest { runAll() }
}
