package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.testing.contract.InteractionContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal const val CONFIRM_ONE = "d1000000-0000-4000-8000-000000000001"
internal const val CONFIRM_TWO = "d1000000-0000-4000-8000-000000000002"
internal const val ANSWERED = "d1000000-0000-4000-8000-000000000003"

/** AD-013, AD-014: the shared interaction contract, run unchanged against the real adapter and the stub server. */
class InteractionGatewayContractTest : InteractionContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val issue = IssueId(PAP_1182)
    override val pendingConfirmations = listOf(InteractionId(CONFIRM_ONE), InteractionId(CONFIRM_TWO))
    override val answeredId = InteractionId(ANSWERED)

    override fun create(): InteractionGateway {
        val world = WorkWorld().also(::seedIssues)
        val acme = ServerFixtures.COMPANY_ACME
        val rows = world.home.interactions.interactions
        listOf(CONFIRM_ONE, CONFIRM_TWO).forEach { id ->
            rows +=
                StubInteraction(
                    id,
                    acme,
                    PAP_1182,
                    "request_confirmation",
                    InteractionFixtures.confirmation("Proceed?"),
                )
        }
        rows +=
            StubInteraction(ANSWERED, acme, PAP_1182, "ask_user_questions", InteractionFixtures.questions()).also {
                it.status = "answered"
            }
        return world.interactions
    }

    @Test
    fun `AD-013 the real adapter passes the interaction contract`() = runTest { runAll() }
}
