package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

/** Every decorated method reaches the gateway it wraps exactly once and leaves the status alone on success. */
class SignedOutAwareForwardingTest {
    private val issue = IssueId("pap-1182")
    private val agent = AgentId("agent-eng-1")
    private val run = RunId("run-live")
    private val approval = ApprovalId("ap-1")

    @Test
    fun `attention decision approval and budget methods are forwarded once`() =
        runTest {
            val env = Env()
            val scope = env.scope
            val marker = env.signedOut

            val attention = SignedOutAwareAttentionGateway(env.attention, marker)
            attention.feed(scope, FeedQuery())
            attention.dismiss(scope, "k")
            attention.snooze(scope, "k", Instant.fromEpochSeconds(0))
            attention.restore(scope, "k")
            attention.setDecideBy(scope, AttentionSourceKind.Approval, "ap-1", DecideBy.Today)
            env.attention.calls.values
                .sum() shouldBe 5

            val decisions = SignedOutAwareDecisionGateway(env.decisions, marker)
            decisions.get(scope, DecisionId("dec-1"))
            decisions.decide(scope, DecisionId("dec-1"), "ship", emptyMap(), "key")
            decisions.dismiss(scope, DecisionId("dec-1"), null)
            env.decisions.calls.values
                .sum() shouldBe 3

            val approvals = SignedOutAwareApprovalGateway(env.approvals, marker)
            approvals.get(scope, approval)
            approvals.linkedIssues(scope, approval)
            approvals.comments(scope, approval)
            approvals.approve(scope, approval, null)
            approvals.reject(scope, approval, "n")
            approvals.requestRevision(scope, approval, "n")
            approvals.addComment(scope, approval, "b")
            env.approvals.calls.values
                .sum() shouldBe 7

            val budgets = SignedOutAwareBudgetGateway(env.budgets, marker)
            budgets.incident(scope, BudgetIncidentId("inc-1"))
            budgets.resolve(scope, BudgetIncidentId("inc-1"), BudgetResolution.KeepPaused, null)
            env.budgets.calls.values
                .sum() shouldBe 2
        }

    @Test
    fun `issue interaction and agent methods are forwarded once`() =
        runTest {
            val env = Env()
            val scope = env.scope
            val marker = env.signedOut

            val issues = SignedOutAwareIssueGateway(env.issues, marker)
            issues.list(scope, IssueQuery())
            issues.get(scope, issue)
            issues.comments(scope, issue)
            issues.addComment(scope, issue, "b", CommentOptions())
            issues.setStatus(scope, issue, IssueStatus.Todo)
            issues.assign(scope, issue, Assignee.None)
            issues.create(scope, NewIssue("t"))
            issues.attachments(scope, issue)
            env.issues.calls.values
                .sum() shouldBe 8

            val interactions = SignedOutAwareInteractionGateway(env.interactions, marker)
            interactions.list(scope, issue)
            interactions.answer(scope, issue, InteractionId("i-1"), InteractionAnswer.Accept)
            env.interactions.calls.values
                .sum() shouldBe 2

            val agents = SignedOutAwareAgentGateway(env.agents, marker)
            agents.list(scope)
            agents.get(scope, agent)
            agents.pause(scope, agent)
            agents.resume(scope, agent)
            agents.invokeHeartbeat(scope, agent)
            agents.setBudget(scope, agent, Money.usdCents(1))
            env.agents.calls.values
                .sum() shouldBe 6
        }

    @Test
    fun `run and dashboard methods are forwarded once`() =
        runTest {
            val env = Env()
            val scope = env.scope
            val marker = env.signedOut

            val runs = SignedOutAwareRunGateway(env.runs, marker)
            runs.get(scope, run)
            runs.events(scope, run, 0)
            runs.log(scope, run, 0)
            runs.cancel(scope, run)
            runs.forIssue(scope, issue)
            runs.forAgent(scope, agent)
            runs.live(scope)
            env.runs.calls.values
                .sum() shouldBe 7

            val dashboard = SignedOutAwareDashboardGateway(env.dashboard, marker)
            dashboard.dashboard(scope)
            dashboard.windowSpend(scope)
            env.dashboard.calls.values
                .sum() shouldBe 2
        }
}
