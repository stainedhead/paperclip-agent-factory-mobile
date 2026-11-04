package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** R-019: the tone (colour family) of every status is decided once, in shared code; the apps only pick the colour. */
class ToneRulesTest {
    @Test
    fun `R-019 run status tones`() {
        mapOf(
            RunStatusUi.Running to ToneUi.Success,
            RunStatusUi.Succeeded to ToneUi.Success,
            RunStatusUi.Failed to ToneUi.Critical,
            RunStatusUi.TimedOut to ToneUi.Critical,
            RunStatusUi.Cancelled to ToneUi.Warn,
            RunStatusUi.Interrupted to ToneUi.Warn,
            RunStatusUi.Queued to ToneUi.Neutral,
            RunStatusUi.ScheduledRetry to ToneUi.Neutral,
            RunStatusUi.Unknown to ToneUi.Neutral,
        ).forEach { (status, tone) -> status.tone shouldBe tone }
        RunStatusUi.entries.size shouldBe 9
    }

    @Test
    fun `R-019 agent status tones`() {
        mapOf(
            AgentStatusUi.Running to ToneUi.Success,
            AgentStatusUi.Error to ToneUi.Critical,
            AgentStatusUi.Paused to ToneUi.Warn,
            AgentStatusUi.PendingApproval to ToneUi.Warn,
            AgentStatusUi.Active to ToneUi.Neutral,
            AgentStatusUi.Idle to ToneUi.Neutral,
            AgentStatusUi.Terminated to ToneUi.Neutral,
            AgentStatusUi.Unknown to ToneUi.Neutral,
        ).forEach { (status, tone) -> status.tone shouldBe tone }
        AgentStatusUi.entries.size shouldBe 8
    }

    @Test
    fun `R-019 issue status and priority tones`() {
        IssueStatusUi.Blocked.tone shouldBe ToneUi.Critical
        IssueStatusUi.Done.tone shouldBe ToneUi.Success
        IssueStatusUi.InReview.tone shouldBe ToneUi.Warn
        listOf(
            IssueStatusUi.Backlog,
            IssueStatusUi.Todo,
            IssueStatusUi.InProgress,
            IssueStatusUi.Cancelled,
            IssueStatusUi.Unknown,
        ).forEach { it.tone shouldBe ToneUi.Neutral }
        IssuePriorityUi.Critical.tone shouldBe ToneUi.Critical
        IssuePriorityUi.High.tone shouldBe ToneUi.Warn
        listOf(IssuePriorityUi.Medium, IssuePriorityUi.Low, IssuePriorityUi.Unknown)
            .forEach { it.tone shouldBe ToneUi.Neutral }
    }

    @Test
    fun `R-019 approval, effect and budget threshold tones`() {
        ApprovalStatusUi.Approved.tone shouldBe ToneUi.Success
        ApprovalStatusUi.Rejected.tone shouldBe ToneUi.Critical
        ApprovalStatusUi.Pending.tone shouldBe ToneUi.Warn
        ApprovalStatusUi.RevisionRequested.tone shouldBe ToneUi.Warn
        ApprovalStatusUi.Cancelled.tone shouldBe ToneUi.Neutral
        ApprovalStatusUi.Unknown.tone shouldBe ToneUi.Neutral
        EffectStatusUi.Executed.tone shouldBe ToneUi.Success
        EffectStatusUi.Skipped.tone shouldBe ToneUi.Warn
        EffectStatusUi.Failed.tone shouldBe ToneUi.Critical
        EffectStatusUi.Claimed.tone shouldBe ToneUi.Neutral
        EffectStatusUi.Unknown.tone shouldBe ToneUi.Neutral
        BudgetThresholdUi.Hard.tone shouldBe ToneUi.Critical
        BudgetThresholdUi.Soft.tone shouldBe ToneUi.Warn
    }
}
