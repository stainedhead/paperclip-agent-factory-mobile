package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** R-019: which runs are live and what a row offers for one is a shared rule, not an Android or iPhone one. */
class RunLivenessRulesTest {
    @Test
    fun `R-019 a run that has not ended is live`() {
        val live = setOf(RunStatusUi.Running, RunStatusUi.Queued, RunStatusUi.ScheduledRetry)

        RunStatusUi.entries.forEach { it.isLive shouldBe (it in live) }
    }

    private fun row(
        status: RunStatusUi?,
        actions: List<AgentActionUi>,
    ) = AgentRowUi(
        id = "agent-1",
        name = "Engineer 1",
        role = "engineer",
        status = AgentStatusUi.Running,
        lifecycleState = null,
        isBudgetPaused = false,
        hasBudget = true,
        budgetPercentUsed = 10,
        lastHeartbeatAtEpochMillis = null,
        actions = actions,
        currentRun = status?.let { RunRowUi("run-1", "Engineer 1", null, it, null, 0L, null, null, null) },
    )

    @Test
    fun `R-019 a live run replaces Run now with View run`() {
        val all = listOf(AgentActionUi.Pause, AgentActionUi.RunNow)

        row(RunStatusUi.Running, all).visibleActions shouldBe listOf(AgentActionUi.Pause)
        row(RunStatusUi.Running, all).offersViewRun shouldBe true
        row(RunStatusUi.Queued, all).offersViewRun shouldBe true
    }

    @Test
    fun `R-019 without a live run Run now stays and there is no View run`() {
        val all = listOf(AgentActionUi.Pause, AgentActionUi.RunNow)

        row(null, all).visibleActions shouldBe all
        row(null, all).offersViewRun shouldBe false
        row(RunStatusUi.Succeeded, all).visibleActions shouldBe all
        row(RunStatusUi.Succeeded, all).offersViewRun shouldBe false
    }
}
