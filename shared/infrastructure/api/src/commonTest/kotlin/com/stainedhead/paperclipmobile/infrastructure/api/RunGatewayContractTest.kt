package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.testing.contract.RunReadContract
import com.stainedhead.paperclipmobile.testing.contract.RunWriteContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private const val ENGINEER = "e0000001-0000-4000-8000-000000000001"
private const val ISSUE = "c0000001-0000-4000-8000-000000000001"
private const val LIVE_RUN = "a0000001-0000-4000-8000-000000000001"
private const val DONE_RUN = "a0000002-0000-4000-8000-000000000002"
private const val LOG_BYTES = 150_000

/** A host with Engineer 1, the issue PAP-1182, a live run with two events and a long log, and a finished run. */
private fun runsWorld(): WorkWorld {
    val world = WorkWorld()
    val acme = ServerFixtures.COMPANY_ACME
    world.home.agents.agents += StubAgent(ENGINEER, acme)
    world.home.issues.issues += StubIssue(ISSUE, acme, "PAP-1182", "Ship the billing migration")
    val live = StubRun(LIVE_RUN, acme, ENGINEER, "Engineer 1", ISSUE, "running")
    live.events += StubRunEvent(1, "lifecycle", "system", "info", "Run started")
    live.events += StubRunEvent(2, "adapter.invoke", "stdout", "info", "Ran the schema migration")
    live.log = "migration step\n".repeat(LOG_BYTES / "migration step\n".length)
    val done = StubRun(DONE_RUN, acme, ENGINEER, "Engineer 1", ISSUE, "succeeded")
    done.finishedAt = "2026-11-01T10:30:00.000Z"
    world.home.runs.runs += listOf(done, live)
    return world
}

/** AD-013, AD-014: the shared run read contract, run unchanged against the real adapter and the stub server. */
class RunGatewayReadContractTest : RunReadContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val liveRunId = RunId(LIVE_RUN)
    override val issueWithRuns = IssueId(ISSUE)
    override val agentWithRuns = AgentId(ENGINEER)

    override fun create(): RunGateway = runsWorld().runs

    @Test
    fun `AD-013 the real adapter passes the run read contract`() = runTest { runAll() }
}

/** AD-013, AD-014: the shared run cancel contract, run unchanged against the real adapter and the stub server. */
class RunGatewayWriteContractTest : RunWriteContract() {
    override val scope: CompanyScope = WorkWorld().acme
    override val liveRunId = RunId(LIVE_RUN)
    override val finishedRunId = RunId(DONE_RUN)

    override fun create(): RunGateway = runsWorld().runs

    @Test
    fun `AD-013 the real adapter passes the run cancel contract`() = runTest { runAll() }
}
