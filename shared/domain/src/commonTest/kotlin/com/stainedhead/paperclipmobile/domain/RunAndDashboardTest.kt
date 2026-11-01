package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

internal fun testRun(
    id: String = "run-1",
    status: RunStatus = RunStatus.Running,
) = HeartbeatRun(
    id = RunId(id),
    agentId = AgentId("a1"),
    agentName = "Engineer 1",
    issueId = IssueId("i1"),
    status = status,
    invocationSource = "assignment",
    createdAt = Instant.parse("2026-11-01T10:00:00Z"),
    startedAt = Instant.parse("2026-11-01T10:00:05Z"),
    finishedAt = null,
    error = null,
)

class RunTest {
    @Test
    fun `FR-075 only an active run is live and can be cancelled`() {
        testRun(status = RunStatus.Running).isLive shouldBe true
        testRun(status = RunStatus.Queued).canCancel shouldBe true
        testRun(status = RunStatus.Succeeded).isLive shouldBe false
        testRun(status = RunStatus.Failed).canCancel shouldBe false
    }

    @Test
    fun `FR-075 the log tail appends chunks and follows the next offset`() {
        var tail = LogTail.Empty
        tail.offset shouldBe 0L
        tail = tail.append(LogChunk("hello ", nextOffset = 6))
        tail = tail.append(LogChunk("world", nextOffset = 11))
        tail.text shouldBe "hello world"
        tail.offset shouldBe 11L
    }

    @Test
    fun `a chunk with no next offset leaves the cursor where it was`() {
        val tail = LogTail.Empty.append(LogChunk("a", nextOffset = 1)).append(LogChunk("", nextOffset = null))
        tail.offset shouldBe 1L
        tail.text shouldBe "a"
    }

    @Test
    fun `the log tail keeps only the last characters and says it dropped the start`() {
        val big = "x".repeat(LogTail.MAX_CHARS)
        val tail = LogTail.Empty.append(LogChunk(big, nextOffset = 10)).append(LogChunk("END", nextOffset = 13))
        tail.text.length shouldBe LogTail.MAX_CHARS
        tail.text.endsWith("END") shouldBe true
        tail.droppedStart shouldBe true
        LogTail.Empty.droppedStart shouldBe false
    }

    @Test
    fun `events are merged by sequence without duplicates and in order`() {
        fun event(seq: Long) =
            RunEvent(seq, "log", RunStream.Stdout, RunEventLevel.Info, "m$seq", Instant.parse("2026-11-01T10:00:00Z"))
        val merged = RunEvents.merge(listOf(event(1), event(2)), listOf(event(2), event(3)))
        merged.map { it.seq } shouldBe listOf(1L, 2L, 3L)
        RunEvents.lastSeq(merged) shouldBe 3L
        RunEvents.lastSeq(emptyList()) shouldBe 0L
    }
}

class DashboardTest {
    @Test
    fun `FR-078 spend windows sum providers per window and a missing window is zero`() {
        val windows =
            SpendWindows.fromRows(
                listOf(
                    SpendRow("5h", 120),
                    SpendRow("5h", 30),
                    SpendRow("7d", 5_000),
                    SpendRow("1y", 99),
                ),
            )
        windows.map { it.window } shouldBe
            listOf(SpendWindowKind.Last5Hours, SpendWindowKind.Last24Hours, SpendWindowKind.Last7Days)
        windows.map { it.cost.minorUnits } shouldBe listOf(150L, 0L, 5_000L)
    }

    @Test
    fun `FR-078 the month budget share is spend over budget and zero without a budget`() {
        val summary = testSummary(spend = 2_500, budget = 10_000)
        summary.monthPercentUsed shouldBe 25
        testSummary(spend = 500, budget = 0).monthPercentUsed shouldBe 0
        testSummary(spend = 500, budget = 0).hasBudget shouldBe false
    }

    private fun testSummary(
        spend: Long,
        budget: Long,
    ) = DashboardSummary(
        agents = AgentCounts(active = 4, running = 2, paused = 1, error = 1),
        tasks = TaskCounts(open = 20, inProgress = 5, blocked = 2, done = 30),
        monthSpend = Money.usdCents(spend),
        monthBudget = Money.usdCents(budget),
        pendingApprovals = 2,
        activeBudgetIncidents = 1,
    )
}
