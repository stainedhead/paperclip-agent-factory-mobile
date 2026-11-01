package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

internal fun testAgent(
    name: String = "Engineer 1",
    status: AgentStatus = AgentStatus.Idle,
    pauseReason: PauseReason? = null,
    budgetCents: Long = 10_000,
    spentCents: Long = 2_500,
) = Agent(
    id = AgentId("a1"),
    name = name,
    role = "engineer",
    title = null,
    status = status,
    lifecycleState = null,
    reportsToId = null,
    pauseReason = pauseReason,
    monthlyBudget = Money.usdCents(budgetCents),
    monthSpend = Money.usdCents(spentCents),
    lastHeartbeatAt = Instant.parse("2026-11-01T10:00:00Z"),
)

class AgentTest {
    @Test
    fun `FR-070 error and pending approval and budget paused agents need attention`() {
        testAgent(status = AgentStatus.Error).group shouldBe AgentGroup.NeedsAttention
        testAgent(status = AgentStatus.PendingApproval).group shouldBe AgentGroup.NeedsAttention
        testAgent(status = AgentStatus.Paused, pauseReason = PauseReason.Budget).group shouldBe
            AgentGroup.NeedsAttention
    }

    @Test
    fun `FR-070 running paused and idle agents land in their own groups`() {
        testAgent(status = AgentStatus.Running).group shouldBe AgentGroup.Running
        testAgent(status = AgentStatus.Paused, pauseReason = PauseReason.Manual).group shouldBe AgentGroup.Paused
        testAgent(status = AgentStatus.Paused).group shouldBe AgentGroup.Paused
        testAgent(status = AgentStatus.Idle).group shouldBe AgentGroup.Idle
        testAgent(status = AgentStatus.Active).group shouldBe AgentGroup.Idle
        testAgent(status = AgentStatus.Unknown).group shouldBe AgentGroup.Idle
    }

    @Test
    fun `terminated agents are not listed`() {
        testAgent(status = AgentStatus.Terminated).group shouldBe null
    }

    @Test
    fun `FR-070 lifecycle state is shown only when it is not ready`() {
        testAgent().visibleLifecycle shouldBe null
        testAgent().copy(lifecycleState = "ready").visibleLifecycle shouldBe null
        testAgent().copy(lifecycleState = "pausing").visibleLifecycle shouldBe "pausing"
    }

    @Test
    fun `grouping orders groups and keeps names sorted inside them and omits empty groups`() {
        val sections =
            AgentGrouping.group(
                listOf(
                    testAgent("Zed", AgentStatus.Idle),
                    testAgent("Bob", AgentStatus.Running),
                    testAgent("Amy", AgentStatus.Idle),
                    testAgent("Gone", AgentStatus.Terminated),
                ),
            )
        sections.map { it.group } shouldBe listOf(AgentGroup.Running, AgentGroup.Idle)
        sections[1].agents.map { it.name } shouldBe listOf("Amy", "Zed")
    }

    @Test
    fun `a budget of zero means no budget and the share used is capped by the budget`() {
        testAgent(budgetCents = 0).hasBudget shouldBe false
        testAgent(budgetCents = 10_000, spentCents = 2_500).budgetPercentUsed shouldBe 25
        testAgent(budgetCents = 10_000, spentCents = 12_000).budgetPercentUsed shouldBe 120
        testAgent(budgetCents = 0, spentCents = 500).budgetPercentUsed shouldBe 0
    }

    @Test
    fun `a manual pause can be resumed but a budget pause cannot and a terminated agent has no actions`() {
        testAgent(status = AgentStatus.Paused, pauseReason = PauseReason.Manual).canResume shouldBe true
        testAgent(status = AgentStatus.Paused, pauseReason = PauseReason.Budget).canResume shouldBe false
        testAgent(status = AgentStatus.Idle).canResume shouldBe false
        testAgent(status = AgentStatus.Idle).canPause shouldBe true
        testAgent(status = AgentStatus.Running).canPause shouldBe true
        testAgent(status = AgentStatus.Paused).canPause shouldBe false
        testAgent(status = AgentStatus.Terminated).canPause shouldBe false
        testAgent(status = AgentStatus.Idle).canRunNow shouldBe true
        testAgent(status = AgentStatus.Paused).canRunNow shouldBe false
        testAgent(status = AgentStatus.Terminated).canRunNow shouldBe false
    }

    @Test
    fun `pause reasons map from the server`() {
        PauseReason.fromServer("manual") shouldBe PauseReason.Manual
        PauseReason.fromServer("budget") shouldBe PauseReason.Budget
        PauseReason.fromServer("system") shouldBe PauseReason.System
        PauseReason.fromServer("company_archived") shouldBe PauseReason.Other
        PauseReason.fromServer(null) shouldBe null
    }
}
