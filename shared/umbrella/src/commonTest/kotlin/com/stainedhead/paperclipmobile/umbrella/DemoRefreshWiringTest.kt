package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.LiveEvent
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeLiveEvents
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** FR-090, FR-091 through the graph: the platform starts a refresh session for a holder's `refreshTarget`. */
class DemoRefreshWiringTest {
    private val graph = PaperclipCore.createDemoGraph()

    @Test
    fun `FR-091 a live agent status event refreshes the Agents screen through the graph`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)
            val session = graph.refresh.start(agents.refreshTarget)
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { agents.state.first { it.phase == DetailPhase.Content } }
                val gateway = graph.resolve<AgentGateway>() as FakeAgentGateway
                val ceo = AgentId(DemoIds.AGENT_CEO)
                gateway.store[ceo] = gateway.store.getValue(ceo).copy(status = AgentStatus.Running)

                // The session subscribes asynchronously and events sent before that are not replayed: send until seen.
                val events = graph.resolve<LiveEvents>() as FakeLiveEvents
                var state = agents.state.value
                withTimeout(10.seconds) {
                    while (state.sections
                            .flatMap { it.rows }
                            .first { it.id == DemoIds.AGENT_CEO }
                            .status !=
                        AgentStatusUi.Running
                    ) {
                        events.emit(LiveEvent("agent.status", DemoIds.AGENT_CEO, null, null))
                        delay(100)
                        state = agents.state.value
                    }
                }
                state.phase shouldBe DetailPhase.Content
            }
            session.stop()
            agents.close()
        }

    @Test
    fun `a stopped session no longer refreshes on live events`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)
            val session = graph.refresh.start(agents.refreshTarget)
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { agents.state.first { it.phase == DetailPhase.Content } }
                session.stop()
                val gateway = graph.resolve<AgentGateway>() as FakeAgentGateway
                val before = gateway.count("list")

                (graph.resolve<LiveEvents>() as FakeLiveEvents).emit(
                    LiveEvent("agent.status", DemoIds.AGENT_CEO, null, null),
                )
                delay(300)

                gateway.count("list") shouldBe before
            }
            agents.close()
        }

    @Test
    fun `the lifecycle bridge reports the foreground to the refresh policy`() {
        graph.lifecycle.isForeground.value shouldBe true
        graph.lifecycle.enterBackground()
        graph.lifecycle.isForeground.value shouldBe false
        graph.lifecycle.enterForeground()
        graph.lifecycle.isForeground.value shouldBe true
    }
}
