package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.application.agent.InvokeHeartbeat
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.agent.ResumeAgent
import com.stainedhead.paperclipmobile.application.agent.SetAgentBudget
import com.stainedhead.paperclipmobile.application.approval.ApproveApproval
import com.stainedhead.paperclipmobile.application.approval.CommentOnApproval
import com.stainedhead.paperclipmobile.application.approval.RejectApproval
import com.stainedhead.paperclipmobile.application.approval.RequestRevision
import com.stainedhead.paperclipmobile.application.attention.DismissItem
import com.stainedhead.paperclipmobile.application.attention.SetDecideBy
import com.stainedhead.paperclipmobile.application.attention.SnoozeItem
import com.stainedhead.paperclipmobile.application.attention.UndoDismiss
import com.stainedhead.paperclipmobile.application.budget.ResolveBudgetIncident
import com.stainedhead.paperclipmobile.application.decision.DecideDecision
import com.stainedhead.paperclipmobile.application.decision.DismissDecision
import com.stainedhead.paperclipmobile.application.interaction.RespondToInteraction
import com.stainedhead.paperclipmobile.application.issue.AssignIssue
import com.stainedhead.paperclipmobile.application.issue.ChangeIssueStatus
import com.stainedhead.paperclipmobile.application.issue.CommentOnIssue
import com.stainedhead.paperclipmobile.application.issue.CreateIssue
import com.stainedhead.paperclipmobile.application.run.CancelRun
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.SnoozePreset
import com.stainedhead.paperclipmobile.testing.AgentFixtures
import com.stainedhead.paperclipmobile.testing.AttentionFixtures
import com.stainedhead.paperclipmobile.testing.WorkFixtures

/** Every call any gateway in the Env has received, reads and mutations alike. */
internal fun Env.allGatewayCalls(): Int =
    listOf(attention.calls, decisions.calls, approvals.calls, budgets.calls)
        .plus(listOf(issues.calls, interactions.calls, agents.calls, runs.calls, dashboard.calls))
        .sumOf { calls -> calls.values.sum() }

private val incident = AttentionFixtures.incident(observed = 10_000, limit = 10_000)
private val pending = AttentionFixtures.approval()
private val decision = AttentionFixtures.decision()

internal val mutationMatrix: List<MutationCase> =
    listOf(
        MutationCase(
            "SnoozeItem",
            setOf(Action.SnoozeItem),
            "snooze",
            { it.attention.calls },
            { it.attention.failures },
        ) {
            e,
            r,
            ->
            SnoozeItem(e.attention, e.clock, e.timeZone)(
                e.context(r),
                AttentionFixtures.approvalItem().dismissalKey,
                SnoozePreset.OneHour,
            )
        },
        MutationCase(
            "DismissItem",
            setOf(Action.DismissItem),
            "dismiss",
            { it.attention.calls },
            { it.attention.failures },
        ) {
            e,
            r,
            ->
            DismissItem(e.attention)(e.context(r), AttentionFixtures.approvalItem())
        },
        MutationCase(
            "UndoDismiss",
            setOf(Action.UndoDismiss),
            "restore",
            { it.attention.calls },
            { it.attention.failures },
        ) {
            e,
            r,
            ->
            UndoDismiss(e.attention, e.clock)(e.context(r), AttentionFixtures.approvalItem(), e.clock.now())
        },
        MutationCase(
            "SetDecideBy",
            setOf(Action.SetDecideBy),
            "setDecideBy",
            { it.attention.calls },
            { it.attention.failures },
        ) { e, r -> SetDecideBy(e.attention)(e.context(r), AttentionFixtures.decisionItem(), DecideBy.Today) },
        MutationCase("DecideDecision", setOf(Action.DecideOption), "decide", {
            it.decisions.calls
        }, { it.decisions.failures }) {
            e,
            r,
            ->
            DecideDecision(e.decisions, e.deviceAuth, e.ids)(e.context(r), decision.id, "ship", emptyMap())
        },
        MutationCase(
            "DecideDecision",
            setOf(Action.DecideDestructiveOption),
            "decide",
            { it.decisions.calls },
            { it.decisions.failures },
        ) { e, r -> DecideDecision(e.decisions, e.deviceAuth, e.ids)(e.context(r), decision.id, "cancel", emptyMap()) },
        MutationCase(
            "DismissDecision",
            setOf(Action.DismissDecision),
            "dismiss",
            { it.decisions.calls },
            { it.decisions.failures },
        ) { e, r -> DismissDecision(e.decisions)(e.context(r), decision, null) },
        MutationCase("ApproveApproval", setOf(Action.ApproveApproval), "approve", {
            it.approvals.calls
        }, { it.approvals.failures }) {
            e,
            r,
            ->
            ApproveApproval(e.approvals)(e.context(r), pending.id, pending.status, null)
        },
        MutationCase("RejectApproval", setOf(Action.RejectApproval), "reject", {
            it.approvals.calls
        }, { it.approvals.failures }) {
            e,
            r,
            ->
            RejectApproval(e.approvals, e.deviceAuth)(e.context(r), pending.id, pending.status, "not now")
        },
        MutationCase(
            "RequestRevision",
            setOf(Action.RequestApprovalRevision),
            "requestRevision",
            { it.approvals.calls },
            { it.approvals.failures },
        ) { e, r -> RequestRevision(e.approvals)(e.context(r), pending.id, pending.status, "add a budget") },
        MutationCase(
            "CommentOnApproval",
            setOf(Action.CommentOnApproval),
            "addComment",
            { it.approvals.calls },
            { it.approvals.failures },
        ) { e, r -> CommentOnApproval(e.approvals)(e.context(r), pending.id, "Looks good") },
        MutationCase(
            "ResolveBudgetIncident",
            setOf(Action.KeepBudgetPaused),
            "resolve",
            { it.budgets.calls },
            { it.budgets.failures },
        ) { e, r ->
            ResolveBudgetIncident(
                e.budgets,
                e.deviceAuth,
            )(e.context(r), incident, BudgetResolution.KeepPaused)
        },
        MutationCase(
            "ResolveBudgetIncident",
            setOf(Action.RaiseBudgetAndResume),
            "resolve",
            { it.budgets.calls },
            { it.budgets.failures },
        ) { e, r ->
            ResolveBudgetIncident(e.budgets, e.deviceAuth)(
                e.context(r),
                incident,
                BudgetResolution.RaiseAndResume(Money.usdCents(20_000)),
            )
        },
        MutationCase(
            "SetAgentBudget",
            setOf(Action.SetAgentBudget),
            "setBudget",
            { it.agents.calls },
            { it.agents.failures },
        ) {
            e,
            r,
            ->
            SetAgentBudget(e.agents)(e.context(r), AgentFixtures.agents()[2], Money.usdCents(1_000))
        },
        MutationCase("CommentOnIssue", setOf(Action.CommentOnIssue), "addComment", {
            it.issues.calls
        }, { it.issues.failures }) {
            e,
            r,
            ->
            CommentOnIssue(e.issues)(e.context(r), WorkFixtures.pap1182, "hi", CommentOptions())
        },
        MutationCase("ChangeIssueStatus", setOf(Action.ChangeIssueStatus), "setStatus", {
            it.issues.calls
        }, { it.issues.failures }) {
            e,
            r,
            ->
            ChangeIssueStatus(e.issues)(e.context(r), WorkFixtures.issues().first(), IssueStatus.InReview)
        },
        MutationCase(
            "AssignIssue",
            setOf(Action.AssignIssue),
            "assign",
            { it.issues.calls },
            { it.issues.failures },
        ) { e, r ->
            AssignIssue(e.issues)(e.context(r), WorkFixtures.issues().first(), Assignee.None)
        },
        MutationCase(
            "CreateIssue",
            setOf(Action.CreateIssue),
            "create",
            { it.issues.calls },
            { it.issues.failures },
        ) { e, r ->
            CreateIssue(e.issues)(e.context(r), NewIssue("x"))
        },
        MutationCase(
            "RespondToInteraction",
            setOf(Action.RespondToInteraction),
            "answer",
            { it.interactions.calls },
            { it.interactions.failures },
        ) { e, r ->
            RespondToInteraction(e.interactions)(
                e.context(r),
                WorkFixtures.pap1182,
                WorkFixtures.interactions().first(),
                InteractionAnswer.Accept,
            )
        },
        MutationCase(
            "PauseAgent",
            setOf(Action.PauseAgent),
            "pause",
            { it.agents.calls },
            { it.agents.failures },
        ) { e, r ->
            PauseAgent(e.agents, e.deviceAuth)(e.context(r), AgentFixtures.agents()[2])
        },
        MutationCase(
            "ResumeAgent",
            setOf(Action.ResumeAgent),
            "resume",
            { it.agents.calls },
            { it.agents.failures },
        ) { e, r ->
            ResumeAgent(e.agents)(e.context(r), AgentFixtures.agents()[5])
        },
        MutationCase(
            "InvokeHeartbeat",
            setOf(Action.RunAgentNow),
            "invokeHeartbeat",
            { it.agents.calls },
            { it.agents.failures },
        ) { e, r -> InvokeHeartbeat(e.agents, e.runs)(e.context(r), AgentFixtures.agents()[1]) },
        MutationCase("CancelRun", setOf(Action.CancelRun), "cancel", { it.runs.calls }, { it.runs.failures }) { e, r ->
            CancelRun(e.runs, e.deviceAuth)(e.context(r), AgentFixtures.runs().first())
        },
    )
