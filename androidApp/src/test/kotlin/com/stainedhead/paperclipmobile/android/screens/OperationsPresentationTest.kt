package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.RunRowUi
import com.stainedhead.paperclipmobile.presentation.RunStatusUi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val SECONDS_45 = 45L
private const val SECONDS_4_12 = 252L
private const val SECONDS_1_02 = 3_720L
private const val SECONDS_OFFSET_2 = 2L
private const val SECONDS_OFFSET_2_55 = 175L
private const val SECONDS_OFFSET_1_02_03 = 3_723L
private const val ANY_TIME = 1_000L

private fun run(status: RunStatusUi) = RunRowUi("run", "Agent", null, status, ANY_TIME, ANY_TIME, null, null, null)

private fun agent(
    status: AgentStatusUi,
    actions: List<AgentActionUi>,
    currentRun: RunRowUi? = null,
) = AgentRowUi("a", "Agent", "role", status, null, false, true, 0, null, actions, currentRun)

/** The lookups behind the words, colours and times of Factory home, Agents, Agent detail and the Run viewer. */
class OperationsPresentationTest {
    @Test
    fun `an avatar shows a short capitals name as it is and a numbered name as a letter and the number`() {
        AgentInitials.of("CTO") shouldBe "CTO"
        AgentInitials.of("QA agent") shouldBe "QA"
        AgentInitials.of("Engineer 2") shouldBe "E2"
        AgentInitials.of("Designer") shouldBe "DE"
        AgentInitials.of("") shouldBe "?"
    }

    @Test
    fun `how long a run has gone reads in seconds, minutes with seconds, then hours with minutes`() {
        RunTime.elapsed(SECONDS_45) shouldBe "45s"
        RunTime.elapsed(SECONDS_4_12) shouldBe "4m 12s"
        RunTime.elapsed(SECONDS_1_02) shouldBe "1h 02m"
        RunTime.elapsed(-1) shouldBe "0s"
    }

    @Test
    fun `where in the run an event happened reads as a clock, never negative`() {
        RunTime.offset(SECONDS_OFFSET_2) shouldBe "0:02"
        RunTime.offset(SECONDS_OFFSET_2_55) shouldBe "2:55"
        RunTime.offset(SECONDS_OFFSET_1_02_03) shouldBe "1:02:03"
        RunTime.offset(-5) shouldBe "0:00"
    }

    @Test
    fun `a run is live while it is queued, running or waiting for a retry`() {
        val live = RunStatusUi.entries.filter { it.isLive() }

        live shouldBe listOf(RunStatusUi.Queued, RunStatusUi.ScheduledRetry, RunStatusUi.Running)
    }

    @Test
    fun `run and agent statuses have the colours of the mocks`() {
        RunStatusUi.Running.tone() shouldBe StatusTone.Success
        RunStatusUi.Succeeded.tone() shouldBe StatusTone.Success
        RunStatusUi.Failed.tone() shouldBe StatusTone.Critical
        RunStatusUi.TimedOut.tone() shouldBe StatusTone.Critical
        RunStatusUi.Cancelled.tone() shouldBe StatusTone.Warn
        RunStatusUi.Interrupted.tone() shouldBe StatusTone.Warn
        RunStatusUi.Queued.tone() shouldBe StatusTone.Neutral
        AgentStatusUi.Running.tone() shouldBe StatusTone.Success
        AgentStatusUi.Error.tone() shouldBe StatusTone.Critical
        AgentStatusUi.Paused.tone() shouldBe StatusTone.Warn
        AgentStatusUi.PendingApproval.tone() shouldBe StatusTone.Warn
        AgentStatusUi.Idle.tone() shouldBe StatusTone.Neutral
    }

    @Test
    fun `a row with a live run offers View run in place of Run now and keeps the other buttons`() {
        val all = listOf(AgentActionUi.Pause, AgentActionUi.RunNow)

        agent(AgentStatusUi.Running, all, run(RunStatusUi.Running)).visibleActions() shouldBe
            listOf(AgentActionUi.Pause)
        agent(AgentStatusUi.Running, all, run(RunStatusUi.Succeeded)).visibleActions() shouldBe all
        agent(AgentStatusUi.Idle, all).visibleActions() shouldBe all
    }
}
