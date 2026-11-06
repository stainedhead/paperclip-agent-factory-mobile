package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.infrastructure.api.ServerFixtures
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

/** AD-040, FR-090: a refresh session on the live graph follows the platform's connectivity and the app lifecycle. */
class LiveRefreshTest {
    private fun feedRequests(platform: LiveTestPlatform) =
        platform.stub.requests.count { it.path.endsWith("/attention") }

    private suspend fun settle() = withContext(Dispatchers.Default) { delay(SETTLE_MS) }

    @Test
    fun `a refresh session reads again on coming back online or to the foreground and not while away`() =
        runTest {
            val connectivity = FakeConnectivity()
            val platform = LiveTestPlatform(connectivity = connectivity).also { it.stub.withOneDecision() }
            val graph = PaperclipCore.createLiveGraph(false, ApprovingBrowser(platform.stub), platform)
            graph.createFirstRunStateHolder().completeFirstRun()
            val factory =
                graph
                    .createFactoriesStateHolder()
                    .state
                    .awaitLive { it.rows.isNotEmpty() }
                    .rows
                    .single()
                    .id
            val inbox = graph.attention.createInboxStateHolder(factory, ServerFixtures.COMPANY_ACME)
            inbox.state.awaitLive { it.phase == InboxPhase.Content && !it.isRefreshing }
            val session = graph.refresh.start(inbox.refreshTarget)
            settle()
            val loaded = feedRequests(platform)

            connectivity.setOnline(false)
            settle()
            val whileOffline = feedRequests(platform)
            connectivity.setOnline(true)
            withContext(Dispatchers.Default) { waitFor { feedRequests(platform) > whileOffline } }
            val afterOnline = feedRequests(platform)

            graph.lifecycle.setForeground(false)
            settle()
            val whileBackground = feedRequests(platform)
            graph.lifecycle.setForeground(true)
            withContext(Dispatchers.Default) { waitFor { feedRequests(platform) > whileBackground } }

            whileOffline shouldBe loaded
            (afterOnline > whileOffline) shouldBe true
            whileBackground shouldBe afterOnline
            session.stop()
            inbox.close()
            graph.close()
        }

    private suspend fun waitFor(condition: () -> Boolean) {
        repeat(WAIT_ROUNDS) {
            if (condition()) return
            delay(SETTLE_MS)
        }
        error("The condition did not hold in time")
    }

    private companion object {
        const val SETTLE_MS = 300L
        const val WAIT_ROUNDS = 50
    }
}
