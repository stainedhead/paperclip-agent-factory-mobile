package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val DEMO_CARDS = 9
private const val KEYS = "Rotate the signing keys this quarter?"

/**
 * R-030: the swipe itself, not the TalkBack custom actions that mirror it. Toward the end opens the snooze presets,
 * toward the start dismisses with an Undo, and nothing swipes while actions are unavailable (`confirmValueChange`
 * mapping and `enabled` gating of `SwipeableCard`).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class InboxSwipeTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private val state get() = checkNotNull(app.session.inbox.value).state.value

    private fun keys(): InboxItemUi = state.sections.flatMap { it.items }.first { it.title == KEYS }

    private fun show() {
        rule.setContent { app.Themed { InboxScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS }
    }

    @Test
    fun swipingTowardTheEndAsksForASnoozePresetAndChangesNothingYet() {
        show()

        rule.onNodeWithTag("inbox-card-${keys().id}").performTouchInput { swipeRight() }

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("snooze-sheet") }
        rule.onNodeWithTag("snooze-sheet").assertIsDisplayed()
        rule.cardCount() shouldBe DEMO_CARDS
    }

    @Test
    fun swipingTowardTheStartDismissesTheCardAndOffersUndo() {
        show()

        rule.onNodeWithTag("inbox-card-${keys().id}").performTouchInput { swipeLeft() }

        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
        rule.onNodeWithTag("inbox-undo").assertIsDisplayed()
    }

    @Test
    fun theUndoOfADismissLapsesOnItsOwnAfterTheFiveSecondWindow() {
        show()

        rule.onNodeWithTag("inbox-card-${keys().id}").performTouchInput { swipeLeft() }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("inbox-undo") }

        // The holder's five second timer clears the shared state and the snackbar leaves with it; the card stays gone.
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("inbox-undo") }
        rule.cardCount() shouldBe DEMO_CARDS - 1
    }

    @Test
    fun noSwipeDoesAnythingWhileActionsAreUnavailable() {
        show()
        val id = keys().id
        app.controls.setOnline(false)
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }

        rule.onNodeWithTag("inbox-card-$id").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("inbox-card-$id").performTouchInput { swipeRight() }
        rule.waitForIdle()

        rule.cardCount() shouldBe DEMO_CARDS
        rule.hasTag("snooze-sheet") shouldBe false
        rule.hasTag("inbox-undo") shouldBe false
    }
}
