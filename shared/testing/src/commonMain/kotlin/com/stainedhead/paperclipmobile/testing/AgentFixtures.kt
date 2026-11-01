package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentCounts
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunEventLevel
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.RunStream
import com.stainedhead.paperclipmobile.domain.SpendRow
import com.stainedhead.paperclipmobile.domain.TaskCounts

/** Agents, runs and factory-home data that match the design mocks (Engineer 1, QA agent, CTO, Designer, CEO). */
object AgentFixtures {
    const val LOG = "Starting migration\nApplying 0042_add_invoices\n"

    private const val DEFAULT_BUDGET = 100_000L
    private const val DEFAULT_SPENT = 25_000L
    private const val SMALL_BUDGET = 10_000L
    private const val MONTH_SPEND = 312_000L
    private const val MONTH_BUDGET = 500_000L

    fun agent(
        id: String,
        name: String,
        role: String,
        status: AgentStatus,
        reportsTo: String? = null,
    ) = Agent(
        id = AgentId(id),
        name = name,
        role = role,
        title = null,
        status = status,
        lifecycleState = null,
        reportsToId = reportsTo?.let(::AgentId),
        pauseReason = null,
        monthlyBudget = Money.usdCents(DEFAULT_BUDGET),
        monthSpend = Money.usdCents(DEFAULT_SPENT),
        lastHeartbeatAt = TestData.epoch,
    )

    fun agents(): List<Agent> =
        listOf(
            agent("agent-ceo", "CEO", "ceo", AgentStatus.Idle),
            agent("agent-cto", "CTO", "cto", AgentStatus.Idle, reportsTo = "agent-ceo"),
            agent("agent-eng-1", "Engineer 1", "engineer", AgentStatus.Running, reportsTo = "agent-cto"),
            agent("agent-eng-2", "Engineer 2", "engineer", AgentStatus.Paused, "agent-cto")
                .copy(
                    pauseReason = PauseReason.Budget,
                    monthlyBudget = Money.usdCents(SMALL_BUDGET),
                    monthSpend = Money.usdCents(SMALL_BUDGET),
                ),
            agent("agent-qa", "QA agent", "qa", AgentStatus.Error, reportsTo = "agent-cto"),
            agent("agent-design", "Designer", "designer", AgentStatus.Paused, "agent-ceo")
                .copy(pauseReason = PauseReason.Manual),
        )

    fun run(
        id: String,
        status: RunStatus,
        agent: AgentId = WorkFixtures.engineer1,
        issue: IssueId? = WorkFixtures.pap1182,
    ) = HeartbeatRun(
        id = RunId(id),
        agentId = agent,
        agentName = agents().firstOrNull { it.id == agent }?.name,
        issueId = issue,
        status = status,
        invocationSource = "assignment",
        createdAt = TestData.epoch,
        startedAt = TestData.epoch,
        finishedAt = null,
        error = null,
    )

    fun runs(): List<HeartbeatRun> = listOf(run("run-live", RunStatus.Running), run("run-done", RunStatus.Succeeded))

    fun events(): List<RunEvent> =
        listOf(
            RunEvent(1, "run.started", RunStream.System, RunEventLevel.Info, "Run started", TestData.epoch),
            RunEvent(2, "tool.call", RunStream.Stdout, RunEventLevel.Info, "Ran the schema migration", TestData.epoch),
        )

    fun summary() =
        DashboardSummary(
            agents = AgentCounts(active = 4, running = 1, paused = 2, error = 1),
            tasks = TaskCounts(open = 5, inProgress = 1, blocked = 1, done = 1),
            monthSpend = Money.usdCents(MONTH_SPEND),
            monthBudget = Money.usdCents(MONTH_BUDGET),
            pendingApprovals = 2,
            activeBudgetIncidents = 1,
        )

    fun spendRows() =
        listOf(
            SpendRow("5h", FIVE_HOURS),
            SpendRow("24h", DAY_ONE),
            SpendRow("24h", DAY_TWO),
            SpendRow("7d", WEEK),
        )

    private const val FIVE_HOURS = 1_200L
    private const val DAY_ONE = 8_400L
    private const val DAY_TWO = 600L
    private const val WEEK = 61_000L
}
