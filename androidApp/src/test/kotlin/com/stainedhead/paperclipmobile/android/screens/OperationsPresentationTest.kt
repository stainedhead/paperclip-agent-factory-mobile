package com.stainedhead.paperclipmobile.android.screens

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val SECONDS_45 = 45L
private const val SECONDS_4_12 = 252L
private const val SECONDS_1_02 = 3_720L
private const val SECONDS_OFFSET_2 = 2L
private const val SECONDS_OFFSET_2_55 = 175L
private const val SECONDS_OFFSET_1_02_03 = 3_723L

/** The lookups behind the words and times of Factory home, Agents, Agent detail and the Run viewer. */
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
}
