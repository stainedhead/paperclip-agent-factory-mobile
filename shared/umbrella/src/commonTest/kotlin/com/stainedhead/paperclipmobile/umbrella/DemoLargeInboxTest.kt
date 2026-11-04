package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private const val CARDS = 200

/** The large-Inbox demo graph the on-demand frame-timing benchmark scrolls (T8.6): the mock's cards plus more. */
class DemoLargeInboxTest {
    @Test
    fun `the large demo Inbox holds the mock's nine cards plus the requested number of generated ones`() =
        runTest {
            val graph = PaperclipCore.createLargeInboxDemoGraph(CARDS)
            val inbox = graph.attention.createInboxStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)

            val state =
                withContext(Dispatchers.Default) {
                    withTimeout(60.seconds) { inbox.state.first { it.phase == InboxPhase.Content } }
                }

            state.totalCount shouldBe CARDS + NINE
            state.badgeCount shouldBe THREE
            inbox.close()
        }

    @Test
    fun `zero generated cards is the ordinary demo Inbox`() =
        runTest {
            val graph = PaperclipCore.createLargeInboxDemoGraph(0)
            val inbox = graph.attention.createInboxStateHolder(DemoIds.HOME_FACTORY, DemoIds.ACME)

            val state =
                withContext(Dispatchers.Default) {
                    withTimeout(60.seconds) { inbox.state.first { it.phase == InboxPhase.Content } }
                }

            state.totalCount shouldBe NINE
            inbox.close()
        }

    private companion object {
        const val NINE = 9
        const val THREE = 3
    }
}
