package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.InboxState
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

private fun InboxState.has(id: String) = sections.any { section -> section.items.any { it.id == id } }

/** The demo Inbox behaves like a server for the UI (UI-020): resolving a card removes it and the badge follows. */
class DemoInboxFlowTest {
    private val graph = PaperclipCore.createDemoGraph()

    private suspend fun openInbox() =
        graph.attention.createInboxStateHolder("demo-home", "company-acme").also { inbox ->
            inbox.state.await { it.phase == InboxPhase.Content }
        }

    @Test
    fun `approving the hire card removes it from the feed and the badge drops`() =
        runTest {
            val inbox = openInbox()
            inbox.state.value.badgeCount shouldBe 3

            inbox.onIntent(InboxIntent.ApproveTapped("approval:ap-hire-qa"))

            val after = inbox.state.await { !it.has("approval:ap-hire-qa") && !it.isMutating }
            after.badgeCount shouldBe 2
            after.totalCount shouldBe 8
            inbox.close()
        }

    @Test
    fun `keeping an agent paused removes the budget card`() =
        runTest {
            val inbox = openInbox()

            inbox.onIntent(InboxIntent.KeepPausedTapped("budget_alert:inc-eng-2"))

            val after = inbox.state.await { !it.has("budget_alert:inc-eng-2") && !it.isMutating }
            after.badgeCount shouldBe 2
            inbox.close()
        }

    @Test
    fun `the first feed read is twelve seconds old and a refresh is current`() =
        runTest {
            val inbox = openInbox()
            val now = graph.nowEpochMillis()
            inbox.state.value.updatedAtEpochMillis shouldBe now - 12_000L

            inbox.onIntent(InboxIntent.Refresh)

            inbox.state.await { it.updatedAtEpochMillis == now && !it.isRefreshing }
            inbox.close()
        }

    @Test
    fun `going offline through the demo controls disables the actions and coming back enables them`() =
        runTest {
            val inbox = openInbox()
            val controls = checkNotNull(graph.demoControls)

            controls.setOnline(false)
            val offline = inbox.state.await { it.isOffline }
            offline.actionsDisabledReason shouldBe ActionsDisabledReason.Offline

            controls.setOnline(true)
            inbox.state.await { !it.isOffline }.actionsDisabledReason shouldBe null
            inbox.close()
        }

    @Test
    fun `the side project company has nothing that needs the person`() =
        runTest {
            val inbox = graph.attention.createInboxStateHolder("demo-home", "company-side")

            val state = inbox.state.await { it.phase == InboxPhase.Empty }

            state.badgeCount shouldBe 0
            state.totalCount shouldBe 0
            inbox.close()
        }
}
