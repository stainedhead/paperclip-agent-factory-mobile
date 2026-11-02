package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class DecisionModelTest {
    private val at = Instant.parse("2026-11-04T12:00:00Z")

    private fun option(
        style: OptionStyle = OptionStyle.Default,
        vararg effects: DecisionEffect,
    ) = DecisionOption("o", "Label", null, style, effects.toList())

    private val comment = DecisionEffect.CommentOnIssue("iss-1", EffectStaleness.Lenient, "hello")
    private val cancel = DecisionEffect.CancelIssueTree("iss-1", "stop")

    @Test
    fun `FR-041 a destructive style or a cancel_issue_tree effect makes an option destructive`() {
        option(OptionStyle.Default, comment).isDestructive shouldBe false
        option(OptionStyle.Primary, comment).isDestructive shouldBe false
        option(OptionStyle.Destructive, comment).isDestructive shouldBe true
        option(OptionStyle.Default, cancel).isDestructive shouldBe true
        option(OptionStyle.Default).isDestructive shouldBe false
    }

    @Test
    fun `styles and statuses parse and unknown values are safe`() {
        OptionStyle.fromServer("primary") shouldBe OptionStyle.Primary
        OptionStyle.fromServer("destructive") shouldBe OptionStyle.Destructive
        OptionStyle.fromServer("default") shouldBe OptionStyle.Default
        OptionStyle.fromServer(null) shouldBe OptionStyle.Default
        OptionStyle.fromServer("glittery") shouldBe OptionStyle.Default
        DecisionStatus.fromServer("open") shouldBe DecisionStatus.Open
        DecisionStatus.fromServer("decided") shouldBe DecisionStatus.Decided
        DecisionStatus.fromServer("expired") shouldBe DecisionStatus.Expired
        DecisionStatus.fromServer("cancelled") shouldBe DecisionStatus.Cancelled
        DecisionStatus.fromServer("archived") shouldBe DecisionStatus.Unknown
        ExecutionStatus.fromServer("running") shouldBe ExecutionStatus.Running
        ExecutionStatus.fromServer("succeeded") shouldBe ExecutionStatus.Succeeded
        ExecutionStatus.fromServer("partial") shouldBe ExecutionStatus.Partial
        ExecutionStatus.fromServer("failed") shouldBe ExecutionStatus.Failed
        ExecutionStatus.fromServer(null) shouldBe null
        ExecutionStatus.fromServer("???") shouldBe ExecutionStatus.Unknown
        EffectStatus.fromServer("claimed") shouldBe EffectStatus.Claimed
        EffectStatus.fromServer("executed") shouldBe EffectStatus.Executed
        EffectStatus.fromServer("skipped") shouldBe EffectStatus.Skipped
        EffectStatus.fromServer("failed") shouldBe EffectStatus.Failed
        EffectStatus.fromServer("x") shouldBe EffectStatus.Unknown
    }

    private fun decision(
        status: DecisionStatus = DecisionStatus.Open,
        outcome: DecisionOutcome? = null,
        inputs: List<DecisionInput> = emptyList(),
    ) = Decision(
        id = DecisionId("dec-1"),
        title = "Ship it?",
        body = "body",
        status = status,
        options = listOf(option(OptionStyle.Primary, comment)),
        inputs = inputs,
        createdAt = at,
        outcome = outcome,
    )

    @Test
    fun `FR-044 only an open decision accepts a choice`() {
        decision(DecisionStatus.Open).isOpen shouldBe true
        DecisionStatus.entries.filter { it != DecisionStatus.Open }.forEach {
            decision(it).isOpen shouldBe false
        }
    }

    @Test
    fun `FR-043 execution is terminal when it succeeded partly succeeded or failed and pending while running`() {
        fun outcome(status: ExecutionStatus?) = DecisionOutcome(status, emptyList())
        outcome(ExecutionStatus.Succeeded).isTerminal shouldBe true
        outcome(ExecutionStatus.Partial).isTerminal shouldBe true
        outcome(ExecutionStatus.Failed).isTerminal shouldBe true
        outcome(ExecutionStatus.Running).isTerminal shouldBe false
        outcome(null).isTerminal shouldBe false
        outcome(ExecutionStatus.Unknown).isTerminal shouldBe true

        decision(DecisionStatus.Decided, outcome(ExecutionStatus.Running)).isExecutionPending shouldBe true
        decision(DecisionStatus.Decided, outcome(ExecutionStatus.Succeeded)).isExecutionPending shouldBe false
        decision(DecisionStatus.Decided, null).isExecutionPending shouldBe false
        decision(DecisionStatus.Open, outcome(ExecutionStatus.Running)).isExecutionPending shouldBe false
        decision(DecisionStatus.Expired).isFinal shouldBe true
        decision(DecisionStatus.Open).isFinal shouldBe false
    }

    @Test
    fun `FR-042 required inputs must be filled and values must fit maxLength`() {
        val inputs =
            listOf(
                DecisionInput("why", "Why", required = true),
                DecisionInput("note", "Note", required = false, maxLength = 5),
            )
        val d = decision(inputs = inputs)

        d.validateInputs(mapOf("why" to "because")) shouldBe null
        d.validateInputs(mapOf("why" to "because", "note" to "12345")) shouldBe null
        d.validateInputs(emptyMap()) shouldBe InputIssue.Required("why")
        d.validateInputs(mapOf("why" to "   ")) shouldBe InputIssue.Required("why")
        d.validateInputs(mapOf("why" to "ok", "note" to "123456")) shouldBe InputIssue.TooLong("note")
        decision().validateInputs(emptyMap()) shouldBe null
    }

    @Test
    fun `effects expose their target issue and unsupported effects are kept`() {
        comment.targetIssueId shouldBe "iss-1"
        cancel.staleness shouldBe EffectStaleness.Strict
        DecisionEffect.Unsupported("teleport", "iss-9").targetIssueId shouldBe "iss-9"
        DecisionEffect.CreateIssue("iss-1", EffectStaleness.Lenient, "New").title shouldBe "New"
        DecisionEffect.UpdateIssueStatus("iss-1", EffectStaleness.Strict, "done").status shouldBe "done"
        DecisionEffect.AssignIssue("iss-1", EffectStaleness.Lenient, assigneeName = null).targetIssueId shouldBe "iss-1"
        DecisionEffect.ResolveBlocker("iss-1", EffectStaleness.Lenient, 2).blockerCount shouldBe 2
    }
}
