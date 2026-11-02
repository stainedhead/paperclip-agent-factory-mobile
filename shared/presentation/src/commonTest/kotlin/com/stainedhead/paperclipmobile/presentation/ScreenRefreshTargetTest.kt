@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private val company = CompanyTarget("inst-1", TestData.acme.value)

private fun TestScope.env() = Env(listOf(TestData.instance())) { testScheduler.currentTime }

private fun TestScope.scope() = CoroutineScope(StandardTestDispatcher(testScheduler))

private val agent = Invalidation.Agent("agent-eng-1")
private val otherAgent = Invalidation.Agent("agent-other")
private val run = Invalidation.Run("run-live", "agent-eng-1", "pap-1182")
private val otherRun = Invalidation.Run("run-other", "agent-other", "pap-other")
private val issue = Invalidation.Issue("pap-1182")
private val otherIssue = Invalidation.Issue("pap-other")
private val feed = Invalidation.AttentionFeed

private fun RefreshTarget.affectedBy(vararg all: Invalidation) = all.filter { isAffectedBy(it) }

/** FR-090: each visible screen names its refresh interval; FR-091: and which live events make it stale. */
class ScreenRefreshTargetTest {
    @Test
    fun `FR-090 the Inbox and the Decision Approval and Budget screens refresh every 15 seconds`() =
        runTest {
            val env = env()
            val inbox = InboxStateHolder(env.inbox, env.connectivity, env.clock, scope(), company)
            val decision =
                DecisionStateHolder(
                    env.decision,
                    env.connectivity,
                    env.clock,
                    scope(),
                    DecisionTarget(company, "dec-1", null, null, null),
                )
            val approval = ApprovalStateHolder(env.approval, env.connectivity, env.clock, scope(), company, "ap-1")
            val budget = BudgetIncidentStateHolder(env.budget, env.connectivity, env.clock, scope(), company, "inc-1")
            advanceUntilIdle()

            listOf(inbox.refreshTarget, decision.refreshTarget, approval.refreshTarget, budget.refreshTarget).map {
                it.refreshIntervalSeconds.first()
            } shouldBe
                listOf(15, 15, 15, 15)
        }

    @Test
    fun `FR-090 a decided decision and a resolved approval stop refreshing`() =
        runTest {
            val env = env()
            val decision =
                DecisionStateHolder(
                    env.decision,
                    env.connectivity,
                    env.clock,
                    scope(),
                    DecisionTarget(company, "dec-1", null, null, null),
                )
            val approval = ApprovalStateHolder(env.approval, env.connectivity, env.clock, scope(), company, "ap-1")
            advanceUntilIdle()

            decision.onIntent(DecisionIntent.OptionSelected("hold"))
            decision.onIntent(DecisionIntent.ConfirmTapped)
            approval.onIntent(ApprovalIntent.ApproveTapped)
            advanceUntilIdle()

            decision.refreshTarget.refreshIntervalSeconds.first() shouldBe 0
            approval.refreshTarget.refreshIntervalSeconds.first() shouldBe 0
        }

    @Test
    fun `FR-090 work agents and factory home refresh every 15 seconds and a live run makes it 5`() =
        runTest {
            val env = env()
            val list = WorkListStateHolder(env.workList, env.connectivity, env.clock, scope(), company)
            val issueHolder = IssueStateHolder(env.issue, env.connectivity, env.clock, scope(), company, "pap-1182")
            val agents = AgentsStateHolder(env.agentList, env.connectivity, env.clock, scope(), company)
            val detail =
                AgentDetailStateHolder(env.agentDetail, env.connectivity, env.clock, scope(), company, "agent-eng-1")
            val home = FactoryHomeStateHolder(env.factory, env.connectivity, env.clock, scope(), company)
            val live = RunViewerStateHolder(env.runViewer, env.connectivity, env.clock, scope(), company, "run-live")
            val done = RunViewerStateHolder(env.runViewer, env.connectivity, env.clock, scope(), company, "run-done")
            advanceUntilIdle()

            listOf(list.refreshTarget, issueHolder.refreshTarget, agents.refreshTarget, home.refreshTarget).map {
                it.refreshIntervalSeconds.first()
            } shouldBe
                listOf(15, 15, 15, 15)
            listOf(detail.refreshTarget, live.refreshTarget).map { it.refreshIntervalSeconds.first() } shouldBe
                listOf(5, 5)
            done.refreshTarget.refreshIntervalSeconds.first() shouldBe 0
        }

    @Test
    fun `FR-091 the Inbox and the detail screens are stale when the attention feed is`() =
        runTest {
            val env = env()
            val inbox = InboxStateHolder(env.inbox, env.connectivity, env.clock, scope(), company)
            val decision =
                DecisionStateHolder(
                    env.decision,
                    env.connectivity,
                    env.clock,
                    scope(),
                    DecisionTarget(company, "dec-1", null, null, null),
                )
            val approval = ApprovalStateHolder(env.approval, env.connectivity, env.clock, scope(), company, "ap-1")
            val budget = BudgetIncidentStateHolder(env.budget, env.connectivity, env.clock, scope(), company, "inc-1")

            listOf(inbox.refreshTarget, decision.refreshTarget, approval.refreshTarget, budget.refreshTarget).forEach {
                it.affectedBy(agent, run, issue, feed) shouldBe listOf(feed)
            }
        }

    @Test
    fun `FR-091 work screens are stale when their issue changes or one of its runs does`() =
        runTest {
            val env = env()
            val list = WorkListStateHolder(env.workList, env.connectivity, env.clock, scope(), company)
            val issueHolder = IssueStateHolder(env.issue, env.connectivity, env.clock, scope(), company, "pap-1182")

            list.refreshTarget.affectedBy(agent, run, issue, otherIssue, feed) shouldBe listOf(issue, otherIssue)
            issueHolder.refreshTarget.affectedBy(agent, run, otherRun, issue, otherIssue, feed) shouldBe
                listOf(run, issue)
        }

    @Test
    fun `FR-091 agent run and factory screens are stale when their agent or run changes`() =
        runTest {
            val env = env()
            val agents = AgentsStateHolder(env.agentList, env.connectivity, env.clock, scope(), company)
            val detail =
                AgentDetailStateHolder(env.agentDetail, env.connectivity, env.clock, scope(), company, "agent-eng-1")
            val live = RunViewerStateHolder(env.runViewer, env.connectivity, env.clock, scope(), company, "run-live")
            val home = FactoryHomeStateHolder(env.factory, env.connectivity, env.clock, scope(), company)

            agents.refreshTarget.affectedBy(agent, otherAgent, run, otherRun, issue, feed) shouldBe
                listOf(agent, otherAgent, run, otherRun)
            detail.refreshTarget.affectedBy(agent, otherAgent, run, otherRun, issue, feed) shouldBe listOf(agent, run)
            live.refreshTarget.affectedBy(agent, run, otherRun, issue, feed) shouldBe listOf(run)
            home.refreshTarget.affectedBy(agent, run, issue, feed) shouldBe listOf(agent, run, issue, feed)
        }

    @Test
    fun `refreshNow reads again`() =
        runTest {
            val env = env()
            val inbox = InboxStateHolder(env.inbox, env.connectivity, env.clock, scope(), company)
            val agents = AgentsStateHolder(env.agentList, env.connectivity, env.clock, scope(), company)
            val home = FactoryHomeStateHolder(env.factory, env.connectivity, env.clock, scope(), company)
            advanceUntilIdle()
            val before = listOf(env.attention.count("feed"), env.agents.count("list"), env.dashboard.count("dashboard"))

            listOf(inbox.refreshTarget, agents.refreshTarget, home.refreshTarget).forEach { it.refreshNow() }
            advanceUntilIdle()

            listOf(env.attention.count("feed"), env.agents.count("list"), env.dashboard.count("dashboard")) shouldBe
                before.map { it + 1 }
        }
}
