package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val DEMO_CARDS = 9
private const val SETTLE_MILLIS = 500L

/**
 * Main PRD acceptance criterion 6 on Android: every inline Inbox action resolves its item, and the tab badge (the
 * server's `deskBadgeCount`, which the app never computes) follows without a manual refresh.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class InlineActionsBadgeTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private val badge get() = app.session.inboxBadge.value

    private fun show(): Map<String, String> {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { InboxScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS }
        return checkNotNull(app.session.inbox.value)
            .state.value.sections
            .flatMap { it.items }
            .associate { (it.title ?: "") to it.id }
    }

    @Test
    fun approvingKeepingPausedAndRejectingEachResolveTheirCardAndTheBadgeFollows() {
        val ids = show()
        badge shouldBe 3

        rule.onNodeWithTag("inbox-approve-${ids.getValue("Hire a QA engineer agent")}").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
        rule.waitUntil(WAIT_MILLIS) { badge == 2 }

        rule.onNodeWithTag("inbox-keep-paused-${ids.getValue("Engineer 2 hit its monthly budget")}").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 2 }
        rule.waitUntil(WAIT_MILLIS) { badge == 1 }

        rule.onNodeWithTag("inbox-reject-${ids.getValue("Approve the Q1 strategy")}").performClick()
        rule.onNodeWithTag("note-field").performTextInput("Not this quarter")
        runBlocking { delay(SETTLE_MILLIS) }
        rule.onNodeWithTag("note-confirm").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 3 }
    }
}
