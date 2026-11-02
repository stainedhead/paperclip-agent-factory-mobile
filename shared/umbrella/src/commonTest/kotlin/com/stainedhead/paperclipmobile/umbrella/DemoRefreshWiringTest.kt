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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** FR-090, FR-091 through the graph: the platform starts a refresh session for a holder's `refreshTarget`. */
class DemoRefreshWiringTest {
    private val graph = PaperclipCore.createDemoGraph()

    /** The session subscribes on another thread: wait for that signal instead of re-sending on a timer. */
    private suspend fun FakeLiveEvents.awaitSubscribers(count: Int) {
        withTimeout(10.seconds) { subscriberCount.first { it == count } }
    }

    @Test
    fun `FR-091 a live agent status event refreshes the Agents screen through the graph`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)
            val session = graph.refresh.start(agents.refreshTarget)
            val events = graph.resolve<LiveEvents>() as FakeLiveEvents
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) { agents.state.first { it.phase == DetailPhase.Content } }
                events.awaitSubscribers(1)
                val gateway = graph.resolveRaw<AgentGateway>() as FakeAgentGateway
                val ceo = AgentId(DemoIds.AGENT_CEO)
                gateway.store[ceo] = gateway.store.getValue(ceo).copy(status = AgentStatus.Running)

                events.emit(LiveEvent("agent.status", DemoIds.AGENT_CEO, null, null))

                val state =
                    withTimeout(10.seconds) {
                        agents.state.first { state ->
                            state.sections
                                .flatMap { it.rows }
                                .first { it.id == DemoIds.AGENT_CEO }
                                .status ==
                                AgentStatusUi.Running
                        }
                    }
                state.phase shouldBe DetailPhase.Content
            }
            session.stop()
            agents.close()
        }

    @Test
    fun `a stopped session holds no live event subscription`() =
        runTest {
            val agents = graph.agents.createAgentsStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)
            val session = graph.refresh.start(agents.refreshTarget)
            val events = graph.resolve<LiveEvents>() as FakeLiveEvents
            withContext(Dispatchers.Default) {
                events.awaitSubscribers(1)

                session.stop()

                events.awaitSubscribers(0)
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
