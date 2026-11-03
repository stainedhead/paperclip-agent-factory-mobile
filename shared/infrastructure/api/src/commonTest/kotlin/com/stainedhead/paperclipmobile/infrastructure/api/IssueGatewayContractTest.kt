package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.testing.contract.IssueReadContract
import com.stainedhead.paperclipmobile.testing.contract.IssueWriteContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal const val PAP_1182 = "c0000001-0000-4000-8000-000000000001"
internal const val PAP_1190 = "c0000002-0000-4000-8000-000000000002"
internal const val PAP_1191 = "c0000003-0000-4000-8000-000000000003"
internal const val PAP_1150 = "c0000004-0000-4000-8000-000000000004"
internal const val PAP_1160 = "c0000005-0000-4000-8000-000000000005"
internal const val PAP_1100 = "c0000006-0000-4000-8000-000000000006"
internal const val ENGINEER_ID = "e0000001-0000-4000-8000-000000000001"

/** The design-mock issues of Acme: PAP-1182 with two subtasks, one in review, one blocked and one done. */
internal fun seedIssues(world: WorkWorld) {
    val acme = ServerFixtures.COMPANY_ACME
    world.home.agents.agents += StubAgent(ENGINEER_ID, acme)
    val root = StubIssue(PAP_1182, acme, "PAP-1182", "Ship the billing migration", "in_progress", "high")
    root.assigneeAgentId = ENGINEER_ID
    root.number = 1182
    val backfill = StubIssue(PAP_1190, acme, "PAP-1190", "Backfill invoices", "todo", "critical")
    backfill.parentId = PAP_1182
    val runbook = StubIssue(PAP_1191, acme, "PAP-1191", "Update the runbook", "backlog", "low")
    runbook.parentId = PAP_1182
    val review = StubIssue(PAP_1150, acme, "PAP-1150", "Review pricing page", "in_review")
    val blocked = StubIssue(PAP_1160, acme, "PAP-1160", "Waiting for vendor keys", "blocked", "high")
    blocked.blockedBy += PAP_1182
    val done = StubIssue(PAP_1100, acme, "PAP-1100", "Old cleanup", "done", "low")
    world.home.issueStore.issues += listOf(root, backfill, runbook, review, blocked, done)
}

/** AD-013, AD-014: the shared issue read contract, run unchanged against the real adapter and the stub server. */
class IssueGatewayReadContractTest : IssueReadContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val searchableIdentifier = "PAP-1182"

    override fun create(): IssueGateway = WorkWorld().also(::seedIssues).issues

    @Test
    fun `AD-013 the real adapter passes the issue read contract`() = runTest { runAll() }
}

/** AD-013, AD-014: the shared issue write contract, run unchanged against the real adapter and the stub server. */
class IssueGatewayWriteContractTest : IssueWriteContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val openId = IssueId(PAP_1182)
    override val closedId = IssueId(PAP_1100)
    override val agent = AgentId(ENGINEER_ID)

    override fun create(): IssueGateway = WorkWorld().also(::seedIssues).issues

    @Test
    fun `AD-013 the real adapter passes the issue write contract`() = runTest { runAll() }
}
