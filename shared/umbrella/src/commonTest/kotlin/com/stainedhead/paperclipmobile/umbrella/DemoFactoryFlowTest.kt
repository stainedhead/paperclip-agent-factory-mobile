package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentGroupUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.AgentsIntent
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private suspend fun <S> StateFlow<S>.await(predicate: (S) -> Boolean): S =
    withContext(Dispatchers.Default) { withTimeout(60.seconds) { first(predicate) } }

private const val HOME = "demo-home"
private const val ACME = "company-acme"
private const val MILLIS = 1000L
private const val MINUTE = 60L

/** The demo Factory home, Agents and Run viewer carry the mocks' numbers and behave like a server (UI-040). */
class DemoFactoryFlowTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `Running now lists the three runs of the mock with their issues and ages`() =
        runTest {
            val factory = graph.factory.createFactoryHomeStateHolder(HOME, ACME)

            val state = factory.state.await { it.phase == DetailPhase.Content }

            val reference = graph.nowEpochMillis()
            val rows =
                state.liveRuns.associate {
                    it.agentName to
                        Triple(it.issueIdentifier, (reference - it.startedAtEpochMillis!!) / MILLIS, it.status)
                }
            rows.keys shouldBe setOf("Engineer 1", "CTO", "Designer")
            rows.getValue("Engineer 1").first shouldBe "PAP-1204"
            rows.getValue("Engineer 1").second / MINUTE shouldBe 4L
            rows.getValue("CTO").first shouldBe "PAP-1182"
            rows.getValue("CTO").second / MINUTE shouldBe 1L
            rows.getValue("Designer").first shouldBe "PAP-1199"
            rows.getValue("Designer").second / MINUTE shouldBe 12L
            factory.close()
        }

    @Test
    fun `the Run viewer shows the events and the log tail of the mock`() =
        runTest {
            val run = graph.runs.createRunViewerStateHolder(HOME, ACME, "run-1204-live")

            val state = run.state.await { it.phase == DetailPhase.Content && it.logText.isNotEmpty() }

            state.run?.issueIdentifier shouldBe "PAP-1204"
            state.run?.issueTitle shouldBe "Add retries to webhook ingest"
            state.events.map { it.message } shouldBe
                listOf(
                    "Checked out PAP-1204",
                    "Opened a branch and edited 3 files",
                    "Ran tests: 2 failing",
                    "Asked you 2 questions",
                )
            state.logText.lines().first() shouldBe "\$ pnpm test webhooks"
            state.logText.contains("Tests: 2 failed, 14 passed") shouldBe true
            state.logText.contains("Waiting for board answer on retry policy") shouldBe true
            run.close()
        }

    @Test
    fun `the Agents rows show the run in progress and the CTO's is the PAP-1182 one`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(HOME, ACME)

            val state = agents.state.await { it.phase == DetailPhase.Content }

            val rows = state.sections.flatMap { it.rows }.associateBy { it.name }
            rows.getValue("CTO").currentRun?.issueIdentifier shouldBe "PAP-1182"
            rows.getValue("Designer").currentRun?.issueIdentifier shouldBe "PAP-1199"
            rows.getValue("QA agent").currentRun shouldBe null
            state.canSetBudget shouldBe true
            agents.close()
        }

    @Test
    fun `the operator company cannot set a budget and is told why`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(HOME, "company-side")

            val state = agents.state.await { it.phase == DetailPhase.Content }

            state.canSetBudget shouldBe false
            state.budgetDisabledReason shouldBe
                com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason.OwnerRole
            agents.close()
        }

    @Test
    fun `run now on the QA agent starts a run so it moves to Running and the Factory home lists four`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(HOME, ACME)
            val factory = graph.factory.createFactoryHomeStateHolder(HOME, ACME)
            agents.state.await { it.phase == DetailPhase.Content }

            agents.onIntent(AgentsIntent.RunNowTapped("agent-qa"))

            val after =
                agents.state.await { state ->
                    state.sections
                        .firstOrNull { it.group == AgentGroupUi.Running }
                        ?.rows
                        ?.any { it.name == "QA agent" } ==
                        true
                }
            after.sections
                .flatMap { it.rows }
                .first { it.name == "QA agent" }
                .status shouldBe AgentStatusUi.Running
            after.sections
                .flatMap { it.rows }
                .first { it.name == "QA agent" }
                .actions shouldBe
                listOf(AgentActionUi.Pause, AgentActionUi.RunNow)
            factory.onIntent(com.stainedhead.paperclipmobile.presentation.FactoryHomeIntent.Refresh)
            factory.state.await { state -> state.liveRuns.size == 4 }
            agents.close()
            factory.close()
        }

    @Test
    fun `a server that fails every read shows the error state and recovers when it is fixed`() =
        runTest {
            val controls = checkNotNull(graph.demoControls)
            controls.failReads(true)
            val factory = graph.factory.createFactoryHomeStateHolder(HOME, ACME)

            val failed = factory.state.await { it.phase == DetailPhase.Failed && it.error != null }
            failed.error?.key shouldBe ErrorKey.ServerError

            controls.failReads(false)
            factory.onIntent(com.stainedhead.paperclipmobile.presentation.FactoryHomeIntent.Refresh)
            factory.state.await { it.phase == DetailPhase.Content }
            factory.close()
        }
}
