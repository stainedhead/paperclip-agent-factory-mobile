package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.testing.contract.ApprovalContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-012, AD-014: the shared approval contract, run unchanged against the real adapter and the stub server. */
class ApprovalGatewayContractTest : ApprovalContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val pendingIds = (1..4).map { ApprovalId("aaaaaaa$it-0000-4000-8000-00000000000$it") }

    override fun create(): ApprovalGateway {
        val world = WorkWorld()
        pendingIds.forEach { world.home.approvals.approvals += StubApproval(it.value, ServerFixtures.COMPANY_ACME) }
        return world.approvals
    }

    @Test
    fun `AD-012 the real adapter passes the approval contract`() = runTest { runAll() }
}
