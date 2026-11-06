package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.android.screens.awaitThat
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.umbrella.PaperclipCore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** R-007 (ADR-117): an Inbox holder with an approve or reject in flight is closed after the outcome is recorded. */
class GraphSessionDeferralTest {
    private val graph = PaperclipCore.createDemoGraph()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One Inbox holder per factory and company, each with a state the test moves and a count of closes. */
    private class Probe {
        val states = MutableStateFlow(InboxState())
        var closes = 0
        val holder =
            ScreenHolder<InboxState, InboxIntent>(states, {}, { closes++ }, inFlight = { it.isWorking })

        fun working(on: Boolean) {
            states.value = InboxState(isMutating = on)
        }
    }

    private val probes = mutableMapOf<String, Probe>()

    private fun session() = settled(newSession())

    /** The app holder follows the demo selection on its own thread; let that finish before the test drives it. */
    private fun settled(session: GraphSession): GraphSession {
        await {
            session.app
                ?.state
                ?.value
                ?.selectedCompanyId != null && session.inbox.value != null
        }
        return session
    }

    private fun newSession() =
        GraphSession(
            id = 1,
            mode = GraphMode.Demo,
            graph = graph,
            notice = null,
            parent = scope,
            options =
                SessionOptions(
                    autoRefresh = false,
                    inboxFactory = { _, instance, company -> probes.getOrPut("$instance/$company") { Probe() }.holder },
                ),
        )

    @AfterEach
    fun tearDown() {
        graph.close()
        scope.cancel()
    }

    private fun await(condition: () -> Boolean) = awaitThat("the session to settle", condition = condition)

    @Test
    fun `a selection change keeps a working inbox holder until the outcome is recorded`() {
        val session = session()
        session.followSelection("i1", "c1")
        val first = probes.getValue("i1/c1")
        first.working(true)

        session.followSelection("i2", "c2")

        first.closes shouldBe 0
        first.working(false)
        await { first.closes == 1 }
        first.closes shouldBe 1
        session.close()
    }

    @Test
    fun `a selection change closes an idle inbox holder at once`() {
        val session = session()
        session.followSelection("i1", "c1")
        val first = probes.getValue("i1/c1")

        session.followSelection("i2", "c2")

        first.closes shouldBe 1
        session.close()
    }

    @Test
    fun `closing the session while the inbox works releases it once the work finishes, and again changes nothing`() {
        val session = session()
        session.followSelection("i1", "c1")
        val first = probes.getValue("i1/c1")
        first.working(true)

        session.close()
        session.close()

        first.closes shouldBe 0
        first.working(false)
        await { first.closes == 1 }
        first.closes shouldBe 1
    }

    @Test
    fun `abandoning the session ends the wait and closes the holder with no coroutine left`() {
        val session = session()
        session.followSelection("i1", "c1")
        val first = probes.getValue("i1/c1")
        first.working(true)
        session.close()
        session.pendingCloses shouldBe 1

        session.abandonDeferred()

        first.closes shouldBe 1
        session.pendingCloses shouldBe 0
    }
}
