package com.stainedhead.paperclipmobile.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class WorkEnumsTest {
    @Test
    fun `ids reject blank values`() {
        shouldThrow<IllegalArgumentException> { IssueId(" ") }
        shouldThrow<IllegalArgumentException> { AgentId("") }
        shouldThrow<IllegalArgumentException> { RunId("") }
        shouldThrow<IllegalArgumentException> { InteractionId("") }
    }

    @Test
    fun `FR-060 issue statuses map from the server and unknown values do not crash`() {
        val table =
            mapOf(
                "backlog" to IssueStatus.Backlog,
                "todo" to IssueStatus.Todo,
                "in_progress" to IssueStatus.InProgress,
                "in_review" to IssueStatus.InReview,
                "done" to IssueStatus.Done,
                "blocked" to IssueStatus.Blocked,
                "cancelled" to IssueStatus.Cancelled,
                "from_the_future" to IssueStatus.Unknown,
            )
        table.forEach { (raw, expected) -> IssueStatus.fromServer(raw) shouldBe expected }
    }

    @Test
    fun `an issue is open until it is done or cancelled`() {
        IssueStatus.entries.filter { it.isOpen }.toSet() shouldBe
            setOf(
                IssueStatus.Backlog,
                IssueStatus.Todo,
                IssueStatus.InProgress,
                IssueStatus.InReview,
                IssueStatus.Blocked,
            )
    }

    @Test
    fun `FR-060 priorities map from the server and critical and high are urgent`() {
        IssuePriority.fromServer("critical") shouldBe IssuePriority.Critical
        IssuePriority.fromServer("high") shouldBe IssuePriority.High
        IssuePriority.fromServer("medium") shouldBe IssuePriority.Medium
        IssuePriority.fromServer("low") shouldBe IssuePriority.Low
        IssuePriority.fromServer("x") shouldBe IssuePriority.Unknown
        IssuePriority.entries.filter { it.isUrgent }.toSet() shouldBe setOf(IssuePriority.Critical, IssuePriority.High)
    }

    @Test
    fun `FR-070 agent statuses map from the server`() {
        val table =
            mapOf(
                "active" to AgentStatus.Active,
                "paused" to AgentStatus.Paused,
                "idle" to AgentStatus.Idle,
                "running" to AgentStatus.Running,
                "error" to AgentStatus.Error,
                "pending_approval" to AgentStatus.PendingApproval,
                "terminated" to AgentStatus.Terminated,
                "zzz" to AgentStatus.Unknown,
            )
        table.forEach { (raw, expected) -> AgentStatus.fromServer(raw) shouldBe expected }
    }

    @Test
    fun `FR-075 run statuses map from the server and queued retry and running are active`() {
        val table =
            mapOf(
                "queued" to RunStatus.Queued,
                "scheduled_retry" to RunStatus.ScheduledRetry,
                "running" to RunStatus.Running,
                "succeeded" to RunStatus.Succeeded,
                "interrupted" to RunStatus.Interrupted,
                "failed" to RunStatus.Failed,
                "cancelled" to RunStatus.Cancelled,
                "timed_out" to RunStatus.TimedOut,
                "zzz" to RunStatus.Unknown,
            )
        table.forEach { (raw, expected) -> RunStatus.fromServer(raw) shouldBe expected }
        RunStatus.entries.filter { it.isActive }.toSet() shouldBe
            setOf(RunStatus.Queued, RunStatus.ScheduledRetry, RunStatus.Running)
        RunStatus.entries.filter { it.canCancel }.toSet() shouldBe
            setOf(RunStatus.Queued, RunStatus.ScheduledRetry, RunStatus.Running)
    }

    @Test
    fun `FR-063 interaction statuses map from the server and only pending is open`() {
        val table =
            mapOf(
                "pending" to InteractionStatus.Pending,
                "accepted" to InteractionStatus.Accepted,
                "rejected" to InteractionStatus.Rejected,
                "answered" to InteractionStatus.Answered,
                "cancelled" to InteractionStatus.Cancelled,
                "expired" to InteractionStatus.Expired,
                "failed" to InteractionStatus.Failed,
                "zzz" to InteractionStatus.Unknown,
            )
        table.forEach { (raw, expected) -> InteractionStatus.fromServer(raw) shouldBe expected }
        InteractionStatus.entries.filter { it.isOpen } shouldBe listOf(InteractionStatus.Pending)
    }
}
