package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentGroupUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.ApprovalPayloadUi
import com.stainedhead.paperclipmobile.presentation.ApprovalStatusUi
import com.stainedhead.paperclipmobile.presentation.ApprovalTypeUi
import com.stainedhead.paperclipmobile.presentation.BudgetThresholdUi
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.EffectKindUi
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.InboxGroupUi
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import com.stainedhead.paperclipmobile.presentation.IssueTab
import com.stainedhead.paperclipmobile.presentation.KeyExpiryUi
import com.stainedhead.paperclipmobile.presentation.MoneyUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.RunStatusUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import com.stainedhead.paperclipmobile.presentation.SpendWindowUi
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private const val CENTS_PER_DOLLAR = 100L

private fun dollars(amount: Long) = MoneyUi(amount * CENTS_PER_DOLLAR, "USD")

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(60.seconds) { first(predicate) } }

private val home = DemoIds.HOME_FACTORY
private val acme = DemoIds.ACME

/**
 * Acceptance criterion 5: every screen's state holder, driven through the demo graph, renders exactly the facts the
 * design mocks show (Home factory, Acme Labs, PAP-1182, Engineer 1).
 */
class DemoGraphFactoriesTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `the Factories tab lists Home Cloud and Test factory as in the mock`() =
        runTest {
            val holder = graph.createFactoriesStateHolder()
            holder.state.await { it.rows.isNotEmpty() }
            holder.onIntent(FactoriesIntent.Refresh)

            val state =
                holder.state.await { s ->
                    s.rows
                        .firstOrNull()
                        ?.companies
                        ?.isNotEmpty() == true
                }

            state.rows.map { it.name } shouldBe listOf("Home factory", "Cloud factory", "Test factory")
            val (homeRow, cloud, test) = state.rows
            homeRow.status shouldBe FactoryStatusUi.SignedIn
            homeRow.address shouldBe "https://paperclip.tail1234.ts.net"
            homeRow.expiry.state shouldBe KeyExpiryUi.Valid
            homeRow.expiry.daysLeft shouldBe 23
            homeRow.companies.map { it.name } shouldBe listOf("Acme Labs", "Side Project Co")
            cloud.status shouldBe FactoryStatusUi.SignedOut
            cloud.expiry.state shouldBe KeyExpiryUi.Expired
            test.status shouldBe FactoryStatusUi.SignedOut
            test.expiry.state shouldBe KeyExpiryUi.NoExpiry
            holder.close()
        }

    @Test
    fun `Home factory is authenticated and private Cloud factory is public`() =
        runTest {
            val homeEdit = graph.createEditInstanceStateHolder(home)
            val cloudEdit = graph.createEditInstanceStateHolder(DemoIds.CLOUD_FACTORY)

            homeEdit.state
                .await { it.name.isNotEmpty() }
                .exposure
                .toString() shouldBe "Private"
            cloudEdit.state
                .await { it.name.isNotEmpty() }
                .exposure
                .toString() shouldBe "Public"
            homeEdit.close()
            cloudEdit.close()
        }
}

class DemoGraphInboxTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `the Inbox shows the mock's counts groups and the three Decide now cards`() =
        runTest {
            val inbox = graph.attention.createInboxStateHolder(home, acme)

            val state = inbox.state.await { it.phase == InboxPhase.Content }

            state.badgeCount shouldBe 3
            state.totalCount shouldBe 9
            state.filters.associate { it.kind to it.count } shouldBe
                mapOf(
                    SourceKindUi.Approval to 2,
                    SourceKindUi.Decision to 3,
                    SourceKindUi.IssueThreadInteraction to 2,
                    SourceKindUi.FailedRun to 1,
                    SourceKindUi.BudgetAlert to 1,
                )
            val decideNow = state.sections.first { it.group == InboxGroupUi.DecideNow }.items
            decideNow.map { it.title } shouldBe
                listOf(
                    "Ship the billing migration to production?",
                    "Engineer 2 hit its monthly budget",
                    "Hire a QA engineer agent",
                )
            // The mock lists the approval second; FR-030 orders a group by severity, so the critical budget stop
            // comes before the high approval (implementation-notes, slice 1a-4 deviations).
            decideNow.map { it.severity } shouldBe listOf(SeverityUi.Critical, SeverityUi.Critical, SeverityUi.High)
            decideNow.map { it.kind } shouldBe
                listOf(SourceKindUi.Decision, SourceKindUi.BudgetAlert, SourceKindUi.Approval)
            decideNow.all { it.decideBy.kind == DecideByKindUi.Today } shouldBe true
            decideNow[0].identifier shouldBe "PAP-1182"
            decideNow[2].whyNow shouldBe "Requested by CEO, reports to CTO, \$150/mo"
            state.role shouldBe RoleUi.Owner
            inbox.close()
        }

    @Test
    fun `the This week group holds the agent question and the failed run`() =
        runTest {
            val inbox = graph.attention.createInboxStateHolder(home, acme)

            val state = inbox.state.await { it.phase == InboxPhase.Content }

            val week = state.sections.first { it.group == InboxGroupUi.ThisWeek }.items
            week.map { it.title } shouldBe
                listOf("Engineer 1 asked 2 questions", "QA agent: run timed out on PAP-1190")
            week[0].identifier shouldBe "PAP-1204"
            week[0].kind shouldBe SourceKindUi.IssueThreadInteraction
            week[1].kind shouldBe SourceKindUi.FailedRun
            inbox.close()
        }

    @Test
    fun `a filter chip narrows the Inbox to decisions`() =
        runTest {
            val inbox = graph.attention.createInboxStateHolder(home, acme)
            inbox.state.await { it.phase == InboxPhase.Content }

            inbox.onIntent(InboxIntent.FilterSelected(SourceKindUi.Decision))

            val state = inbox.state.await { it.selectedFilter == SourceKindUi.Decision }
            state.sections.flatMap { it.items }.size shouldBe 3
            inbox.close()
        }
}

class DemoGraphDetailTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `the Decision shows the mock's three options with their effects and Snooze from the card`() =
        runTest {
            val decision =
                graph.attention.createDecisionStateHolder(
                    home,
                    acme,
                    DemoIds.DECISION_BILLING,
                    DecisionHints("CTO", "today", "attention:decision:${DemoIds.DECISION_BILLING}"),
                )

            val state = decision.state.await { it.phase == DetailPhase.Content }

            state.title shouldBe "Ship the billing migration to production?"
            state.originAgentName shouldBe "CTO"
            state.decideBy.kind shouldBe DecideByKindUi.Today
            state.options.map { it.label } shouldBe
                listOf("Ship tonight behind a feature flag", "Wait for QA sign-off", "Cancel the migration")
            state.options.map { o -> o.effects.map { it.kind } } shouldBe
                listOf(
                    listOf(EffectKindUi.UpdateIssueStatus, EffectKindUi.AssignIssue, EffectKindUi.CommentOnIssue),
                    listOf(EffectKindUi.CreateIssue, EffectKindUi.UpdateIssueStatus),
                    listOf(EffectKindUi.CancelIssueTree),
                )
            state.options[0]
                .effects
                .map { it.detail }
                .take(2) shouldBe listOf("in_progress", "DevOps")
            state.options[1].effects[0].detail shouldBe "QA regression on refunds"
            state.options.map { it.isDestructive } shouldBe listOf(false, false, true)
            state.options[2].effects[0].isStrict shouldBe true
            state.inputs.map { it.label } shouldBe listOf("Note to the agent (optional)")
            state.inputs[0].isRequired shouldBe false
            state.canSnooze shouldBe true
            decision.close()
        }

    @Test
    fun `the Approval shows the QA engineer hire with its payload and actions`() =
        runTest {
            val approval = graph.attention.createApprovalStateHolder(home, acme, DemoIds.APPROVAL_HIRE_QA)

            val state = approval.state.await { it.phase == DetailPhase.Content }

            state.type shouldBe ApprovalTypeUi.HireAgent
            state.status shouldBe ApprovalStatusUi.Pending
            state.requestedByAgentName shouldBe "CEO"
            val payload = state.payload as ApprovalPayloadUi.HireAgent
            payload.name shouldBe "QA engineer"
            payload.adapterType shouldBe "claude_local"
            payload.reportsToAgentId shouldBe DemoIds.AGENT_CTO
            payload.monthlyBudget shouldBe dollars(150)
            approval.close()
        }

    @Test
    fun `the Budget incident shows Engineer 2 at its hard stop`() =
        runTest {
            val budget = graph.attention.createBudgetIncidentStateHolder(home, acme, DemoIds.INCIDENT_ENGINEER_2)

            val state = budget.state.await { it.phase == DetailPhase.Content }

            state.scopeName shouldBe "Engineer 2"
            state.threshold shouldBe BudgetThresholdUi.Hard
            state.observedPercent shouldBe 100
            state.canRaise shouldBe true
            budget.close()
        }
}

class DemoGraphWorkTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `the Work list shows PAP-1204 as a blocked high priority issue of Engineer 1`() =
        runTest {
            val list = graph.work.createWorkListStateHolder(home, acme)

            val row =
                list.state
                    .await { it.phase == DetailPhase.Content }
                    .rows
                    .first { it.identifier == "PAP-1204" }

            row.title shouldBe "Add retries to webhook ingest"
            row.status shouldBe IssueStatusUi.Blocked
            row.priority shouldBe IssuePriorityUi.High
            row.assigneeName shouldBe "Engineer 1"
            list.close()
        }

    @Test
    fun `the Issue PAP-1204 has a thread with the agent's questions a plan three runs and two files`() =
        runTest {
            val issue = graph.work.createIssueStateHolder(home, acme, DemoIds.ISSUE_RETRIES)
            issue.state.await { it.phase == DetailPhase.Content }
            issue.onIntent(IssueIntent.TabSelected(IssueTab.Plan))
            issue.onIntent(IssueIntent.TabSelected(IssueTab.Runs))
            issue.onIntent(IssueIntent.TabSelected(IssueTab.Files))

            val state = issue.state.await { it.runsLoaded && it.filesLoaded }

            state.header?.identifier shouldBe "PAP-1204"
            state.header?.title shouldBe "Add retries to webhook ingest"
            state.header?.status shouldBe IssueStatusUi.Blocked
            state.header?.priority shouldBe IssuePriorityUi.High
            state.header?.assigneeName shouldBe "Engineer 1"
            state.plan?.title shouldBe "Retry plan"
            state.runs.size shouldBe 3
            state.files.size shouldBe 2
            state.thread.mapNotNull { it.interaction }.size shouldBe 1
            issue.close()
        }
}

class DemoGraphAgentsTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `the Agents tab groups the six agents as in the mock`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(home, acme)

            val state = agents.state.await { it.phase == DetailPhase.Content }

            val byGroup = state.sections.associate { s -> s.group to s.rows.map { it.name } }
            byGroup shouldBe
                mapOf(
                    AgentGroupUi.NeedsAttention to listOf("Engineer 2", "QA agent"),
                    AgentGroupUi.Running to listOf("CTO", "Designer", "Engineer 1"),
                    AgentGroupUi.Idle to listOf("CEO"),
                )
            val rows = state.sections.flatMap { it.rows }.associateBy { it.name }
            rows.getValue("QA agent").status shouldBe AgentStatusUi.Error
            rows.getValue("Engineer 2").isBudgetPaused shouldBe true
            rows.getValue("CEO").status shouldBe AgentStatusUi.Idle
            agents.close()
        }

    @Test
    fun `Agent detail for Engineer 1 shows the run on PAP-1204 and reports to the CTO`() =
        runTest {
            val detail = graph.agents.createAgentDetailStateHolder(home, acme, DemoIds.AGENT_ENGINEER_1)

            val state = detail.state.await { it.phase == DetailPhase.Content }

            state.agent?.name shouldBe "Engineer 1"
            state.reportsToName shouldBe "CTO"
            state.currentRun?.runId shouldBe DemoIds.RUN_RETRIES_LIVE
            state.currentIssue?.identifier shouldBe "PAP-1204"
            state.actions shouldBe listOf(AgentActionUi.Pause, AgentActionUi.RunNow)
            state.refreshIntervalSeconds shouldBe 5
            detail.close()
        }
}

class DemoGraphRunAndFactoryTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `the Run viewer shows Engineer 1's heartbeat with its four events and the pnpm test log tail`() =
        runTest {
            val run = graph.runs.createRunViewerStateHolder(home, acme, DemoIds.RUN_RETRIES_LIVE)

            val state = run.state.await { it.phase == DetailPhase.Content && it.logText.isNotEmpty() }

            state.run?.agentName shouldBe "Engineer 1"
            state.run?.status shouldBe RunStatusUi.Running
            state.isLive shouldBe true
            state.refreshIntervalSeconds shouldBe 5
            val started = state.run?.startedAtEpochMillis ?: error("no start")
            state.events.map { (it.createdAtEpochMillis - started) / 1000 } shouldBe listOf(2L, 40L, 175L, 238L)
            state.logText.contains("pnpm test") shouldBe true
            state.canCancel shouldBe true
            run.close()
        }

    @Test
    fun `Factory home shows the mock's spend windows agent and work tiles`() =
        runTest {
            val factory = graph.factory.createFactoryHomeStateHolder(home, acme)

            val state = factory.state.await { it.phase == DetailPhase.Content && it.spend.isNotEmpty() }

            val tiles = state.tiles ?: error("no tiles")
            tiles.monthSpend shouldBe dollars(1_240)
            tiles.monthBudget shouldBe dollars(2_000)
            tiles.monthPercentUsed shouldBe 62
            listOf(tiles.agentsRunning, tiles.agentsError, tiles.agentsPaused) shouldBe listOf(3, 1, 1)
            listOf(tiles.tasksOpen, tiles.tasksBlocked, tiles.tasksInProgress) shouldBe listOf(18, 2, 5)
            tiles.pendingApprovals shouldBe 2
            tiles.budgetIncidents shouldBe 1
            state.spend.associate { it.window to it.cost } shouldBe
                mapOf(
                    SpendWindowUi.Last5Hours to dollars(38),
                    SpendWindowUi.Last24Hours to dollars(212),
                    SpendWindowUi.Last7Days to dollars(1_015),
                )
            factory.close()
        }
}
