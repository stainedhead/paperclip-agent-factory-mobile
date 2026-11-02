package com.stainedhead.paperclipmobile.testing.demo

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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Agents of the mocks: QA in error, Engineer 2 budget-paused, CTO, Engineer 1 and Designer running, CEO idle. */
object DemoAgents {
    private val now = DemoFactories.now
    private const val DEFAULT_BUDGET_CENTS = 100_000L
    private const val DEFAULT_SPENT_CENTS = 25_000L
    private const val STOP_BUDGET_CENTS = 20_000L

    private fun agent(
        id: String,
        name: String,
        role: String,
        status: AgentStatus,
        reportsTo: String?,
    ) = Agent(
        id = AgentId(id),
        name = name,
        role = role,
        title = null,
        status = status,
        lifecycleState = null,
        reportsToId = reportsTo?.let(::AgentId),
        pauseReason = null,
        monthlyBudget = Money.usdCents(DEFAULT_BUDGET_CENTS),
        monthSpend = Money.usdCents(DEFAULT_SPENT_CENTS),
        lastHeartbeatAt = now - 2.minutes,
    )

    fun agents(): List<Agent> =
        listOf(
            agent(DemoIds.AGENT_CEO, "CEO", "ceo", AgentStatus.Idle, null),
            agent(DemoIds.AGENT_CTO, "CTO", "cto", AgentStatus.Running, DemoIds.AGENT_CEO),
            agent(DemoIds.AGENT_ENGINEER_1, "Engineer 1", "engineer", AgentStatus.Running, DemoIds.AGENT_CTO),
            agent(DemoIds.AGENT_ENGINEER_2, "Engineer 2", "engineer", AgentStatus.Paused, DemoIds.AGENT_CTO)
                .copy(
                    pauseReason = PauseReason.Budget,
                    monthlyBudget = Money.usdCents(STOP_BUDGET_CENTS),
                    monthSpend = Money.usdCents(STOP_BUDGET_CENTS),
                ),
            agent(DemoIds.AGENT_QA, "QA agent", "qa", AgentStatus.Error, DemoIds.AGENT_CTO),
            agent(DemoIds.AGENT_DESIGNER, "Designer", "designer", AgentStatus.Running, DemoIds.AGENT_CEO),
        )

    private fun run(
        id: String,
        agentId: String,
        issue: String?,
        status: RunStatus,
        age: kotlin.time.Duration,
    ) = HeartbeatRun(
        id = RunId(id),
        agentId = AgentId(agentId),
        agentName = agents().first { it.id.value == agentId }.name,
        issueId = issue?.let(::IssueId),
        status = status,
        invocationSource = "assignment",
        createdAt = now - age,
        startedAt = now - age,
        finishedAt = if (status.isActive) null else now - age + 3.minutes,
        error = if (status == RunStatus.TimedOut) "timed_out" else null,
    )

    fun runs(): List<HeartbeatRun> =
        listOf(
            run(DemoIds.RUN_RETRIES_LIVE, DemoIds.AGENT_ENGINEER_1, DemoIds.ISSUE_RETRIES, RunStatus.Running, RUN_AGE),
            run("run-1204-failed", DemoIds.AGENT_ENGINEER_1, DemoIds.ISSUE_RETRIES, RunStatus.Failed, 1.hours),
            run("run-1204-done", DemoIds.AGENT_ENGINEER_1, DemoIds.ISSUE_RETRIES, RunStatus.Succeeded, 3.hours),
            run("run-cto-live", DemoIds.AGENT_CTO, DemoIds.ISSUE_BILLING, RunStatus.Running, 12.minutes),
            run("run-design-live", DemoIds.AGENT_DESIGNER, null, RunStatus.Running, 6.minutes),
            run(DemoIds.RUN_QA_TIMEOUT, DemoIds.AGENT_QA, DemoIds.ISSUE_REFUNDS, RunStatus.TimedOut, 5.hours),
        )

    private val RUN_AGE = 4.minutes
    private const val FIRST_SEQ = 1L
    private const val THIRD_SEQ = 3L
    private const val FOURTH_SEQ = 4L
    private const val TEST_RUN_SECONDS = 175
    private const val FAILURE_SECONDS = 238

    private fun event(
        seq: Long,
        type: String,
        stream: RunStream,
        message: String,
        offset: kotlin.time.Duration,
    ) = RunEvent(
        seq,
        type,
        stream,
        if (stream == RunStream.Stderr) RunEventLevel.Warn else RunEventLevel.Info,
        message,
        now - RUN_AGE + offset,
    )

    /** Events of Engineer 1's heartbeat at 0:02, 0:40, 2:55 and 3:58 after it started (the Run viewer mock). */
    fun events(): List<RunEvent> =
        listOf(
            event(FIRST_SEQ, "run.started", RunStream.System, "Heartbeat started on PAP-1204", 2.seconds),
            event(FIRST_SEQ + 1, "tool.call", RunStream.Stdout, "Read ingest/retry.ts", 40.seconds),
            event(
                THIRD_SEQ,
                "tool.call",
                RunStream.Stdout,
                "Ran pnpm test --filter webhook-ingest",
                TEST_RUN_SECONDS.seconds,
            ),
            event(
                FOURTH_SEQ,
                "run.output",
                RunStream.Stderr,
                "1 test failing: dead-letter queue",
                FAILURE_SECONDS.seconds,
            ),
        )

    /** The tail of `pnpm test` that Engineer 1 sees. */
    const val LOG =
        "\$ pnpm test --filter webhook-ingest\n" +
            " RUN  v1.6.0\n" +
            " ✓ ingest/retry.test.ts (4 tests) 212ms\n" +
            " ✓ ingest/backoff.test.ts (3 tests) 98ms\n" +
            " ❯ ingest/dead-letter.test.ts (2 tests | 1 failed)\n" +
            "   × moves exhausted deliveries to the dead-letter queue\n" +
            " Test Files  1 failed | 2 passed (3)\n"

    private const val MONTH_SPEND_CENTS = 124_000L
    private const val MONTH_BUDGET_CENTS = 200_000L
    private const val ACTIVE_AGENTS = 4
    private const val RUNNING_AGENTS = 3
    private const val OPEN_TASKS = 18
    private const val TASKS_IN_PROGRESS = 5
    private const val BLOCKED_TASKS = 2
    private const val DONE_TASKS = 41

    fun summary() =
        DashboardSummary(
            agents = AgentCounts(active = ACTIVE_AGENTS, running = RUNNING_AGENTS, paused = 1, error = 1),
            tasks =
                TaskCounts(
                    open = OPEN_TASKS,
                    inProgress = TASKS_IN_PROGRESS,
                    blocked = BLOCKED_TASKS,
                    done = DONE_TASKS,
                ),
            monthSpend = Money.usdCents(MONTH_SPEND_CENTS),
            monthBudget = Money.usdCents(MONTH_BUDGET_CENTS),
            pendingApprovals = 2,
            activeBudgetIncidents = 1,
        )

    private const val FIVE_HOURS_CENTS = 3_800L
    private const val DAY_ANTHROPIC_CENTS = 12_000L
    private const val DAY_OTHER_CENTS = 9_200L
    private const val WEEK_CENTS = 101_500L

    /** $38 in 5 hours, $212 in 24 hours (two providers) and $1,015 in 7 days. */
    fun spendRows() =
        listOf(
            SpendRow("5h", FIVE_HOURS_CENTS),
            SpendRow("24h", DAY_ANTHROPIC_CENTS),
            SpendRow("24h", DAY_OTHER_CENTS),
            SpendRow("7d", WEEK_CENTS),
        )
}
