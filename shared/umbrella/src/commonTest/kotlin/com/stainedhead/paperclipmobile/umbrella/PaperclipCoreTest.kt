package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.ApprovalTypeUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class PaperclipCoreTest {
    @Test
    fun `the graph builds a first-run state holder that validates addresses`() {
        val firstRun = PaperclipCore.createGraph(allowInsecureLocal = false).createFirstRunStateHolder()

        firstRun.onIntent(FirstRunIntent.AddressChanged("https://paperclip.example.com"))

        firstRun.state.value.canContinue shouldBe true
    }

    @Test
    fun `a debug graph accepts local http and a release graph refuses it`() {
        val debug = PaperclipCore.createGraph(allowInsecureLocal = true).createFirstRunStateHolder()
        val release = PaperclipCore.createGraph(allowInsecureLocal = false).createFirstRunStateHolder()

        debug.onIntent(FirstRunIntent.AddressChanged("http://localhost:3100"))
        release.onIntent(FirstRunIntent.AddressChanged("http://localhost:3100"))

        debug.state.value.canContinue shouldBe true
        release.state.value.canContinue shouldBe false
    }

    @Test
    fun `each call creates an independent state holder`() {
        val graph = PaperclipCore.createGraph(allowInsecureLocal = false)
        val first = graph.createFirstRunStateHolder()
        val second = graph.createFirstRunStateHolder()

        first.onIntent(FirstRunIntent.AddressChanged("https://example.com"))

        second.state.value.address shouldBe ""
    }
}

class PaperclipGraphWiringTest {
    @Test
    fun `the graph builds every slice 1a-1 state holder and they share one set of fakes`() {
        val graph = PaperclipCore.createGraph(allowInsecureLocal = false)

        val app = graph.createAppStateHolder()
        val factories = graph.createFactoriesStateHolder()
        val manage = graph.createManageFactoriesStateHolder()
        val settings = graph.createSettingsStateHolder()
        val add = graph.createEditInstanceStateHolder(null)
        val edit = graph.createEditInstanceStateHolder("missing")

        app.state.value.hasFactories shouldBe false
        factories.state.value.isEmpty shouldBe true
        manage.state.value.rows shouldBe emptyList()
        settings.state.value.destructiveActionAuthAlwaysOn shouldBe true
        add.state.value.canSave shouldBe false
        edit.state.value.instanceId shouldBe "missing"
        listOf(app::close, factories::close, manage::close, settings::close, add::close, edit::close).forEach { it() }
    }
}

class PaperclipGraphSliceTwoWiringTest {
    private val graph = PaperclipCore.createGraph(allowInsecureLocal = false)
    private val factory = "inst-1"
    private val company = "company-acme"

    private suspend fun <T> eventually(block: suspend () -> T): T =
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { block() }
        }

    @Test
    fun `demo mode shows the design mock's Inbox through the Inbox state holder`() =
        runTest {
            val inbox = graph.attention.createInboxStateHolder(factory, company)

            val state = eventually { inbox.state.first { it.phase == InboxPhase.Content } }

            state.sections
                .flatMap { it.items }
                .map { it.kind }
                .toSet() shouldBe
                setOf(SourceKindUi.Approval, SourceKindUi.Decision, SourceKindUi.BudgetAlert)
            inbox.close()
        }

    @Test
    fun `demo mode opens the decision the approval and the budget incident from the Inbox`() =
        runTest {
            val decision =
                graph.attention.createDecisionStateHolder(
                    factory,
                    company,
                    "dec-1",
                    DecisionHints("Engineer 1", "today", null),
                )
            val approval = graph.attention.createApprovalStateHolder(factory, company, "ap-1")
            val budget = graph.attention.createBudgetIncidentStateHolder(factory, company, "inc-1")

            eventually { decision.state.first { it.phase == DetailPhase.Content } }.title shouldBe
                "Ship the billing migration?"
            eventually { approval.state.first { it.phase == DetailPhase.Content } }.type shouldBe
                ApprovalTypeUi.HireAgent
            eventually { budget.state.first { it.phase == DetailPhase.Content } }.scopeName shouldBe "Engineer 2"
            listOf(decision::close, approval::close, budget::close).forEach { it() }
        }
}

class PaperclipGraphSliceThreeWiringTest {
    private val graph = PaperclipCore.createGraph(allowInsecureLocal = false)
    private val factory = "inst-1"
    private val company = "company-acme"

    private suspend fun <T> eventually(block: suspend () -> T): T =
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { block() }
        }

    @Test
    fun `demo mode shows the work list and the issue of the design mock`() =
        runTest {
            val list = graph.work.createWorkListStateHolder(factory, company)
            val issue = graph.work.createIssueStateHolder(factory, company, "pap-1182")

            eventually { list.state.first { it.phase == DetailPhase.Content } }
                .rows
                .map { it.identifier }
                .contains("PAP-1182") shouldBe true
            eventually { issue.state.first { it.phase == DetailPhase.Content } }.header?.title shouldBe
                "Ship the billing migration"
            listOf(list::close, issue::close).forEach { it() }
        }

    @Test
    fun `demo mode shows the agents factory home and a running run`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(factory, company)
            val detail = graph.agents.createAgentDetailStateHolder(factory, company, "agent-eng-1")
            val home = graph.factory.createFactoryHomeStateHolder(factory, company)
            val run = graph.runs.createRunViewerStateHolder(factory, company, "run-live")

            eventually { agents.state.first { it.phase == DetailPhase.Content } }.sections.size shouldBe 4
            eventually { detail.state.first { it.phase == DetailPhase.Content } }.agent?.name shouldBe "Engineer 1"
            eventually { home.state.first { it.phase == DetailPhase.Content } }.tiles?.pendingApprovals shouldBe 2
            eventually { run.state.first { it.phase == DetailPhase.Content } }.isLive shouldBe true
            listOf(agents::close, detail::close, home::close, run::close).forEach { it() }
        }
}
