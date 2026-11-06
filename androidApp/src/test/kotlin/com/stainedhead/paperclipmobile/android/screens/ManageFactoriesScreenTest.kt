package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AND-023: reorder by drag or by the accessibility actions, and remove behind a confirmation and the device owner. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class ManageFactoriesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show() {
        rule.setContent { app.Themed { ManageFactoriesScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Home factory") }
    }

    /** The order the shared core keeps, read through a holder of its own. */
    private fun storedOrder(): List<String> =
        runBlocking {
            val holder = checkNotNull(app.session.graph).createManageFactoriesStateHolder()
            try {
                holder.state
                    .first { it.rows.isNotEmpty() }
                    .rows
                    .map { it.id }
            } finally {
                holder.close()
            }
        }

    private fun customAction(
        tag: String,
        label: String,
    ) {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        rule.runOnUiThread { action.action() }
    }

    @Test
    fun everyFactoryIsListedWithItsAddressAndStatusAndTheCountIsSaid() {
        show()

        rule.onNodeWithTag("manage-count").assertTextEquals("3 factories")
        rule.onNodeWithTag("manage-name-demo-home", useUnmergedTree = true).assertTextEquals("Home factory")
        rule
            .onNodeWithTag(
                "manage-address-demo-cloud",
                useUnmergedTree = true,
            ).assertTextEquals("https://paperclip.example.com")
        rule.onNodeWithTag("manage-status-demo-home", useUnmergedTree = true).assertTextEquals("Connected")
        rule.onNodeWithTag("manage-status-demo-test", useUnmergedTree = true).assertTextEquals("Signed out")
        rule.onNodeWithText("Drag to reorder.", substring = true).assertIsDisplayed()
    }

    @Test
    fun aFactoryMovesUpAndDownThroughItsAccessibilityActionsAndTheOrderIsKept() {
        show()
        storedOrder() shouldBe listOf("demo-home", "demo-cloud", "demo-test")

        customAction("manage-row-demo-test", "Move up")

        rule.waitUntil(WAIT_MILLIS) { storedOrder() == listOf("demo-home", "demo-test", "demo-cloud") }
        customAction("manage-row-demo-home", "Move down")
        rule.waitUntil(WAIT_MILLIS) { storedOrder() == listOf("demo-test", "demo-home", "demo-cloud") }
    }

    @Test
    fun theFirstFactoryCannotMoveUpAndTheLastCannotMoveDown() {
        show()

        val first = rule.onNodeWithTag("manage-row-demo-home").fetchSemanticsNode()
        val last = rule.onNodeWithTag("manage-row-demo-test").fetchSemanticsNode()

        first.config[SemanticsActions.CustomActions].map { it.label } shouldBe listOf("Move down")
        last.config[SemanticsActions.CustomActions].map { it.label } shouldBe listOf("Move up")
    }

    @Test
    fun draggingTheHandleDownPastTheNextRowReordersTheList() {
        show()

        rule.onNodeWithTag("manage-handle-demo-home").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            moveBy(Offset(0f, DRAG_DISTANCE))
            up()
        }

        rule.waitUntil(WAIT_MILLIS) { storedOrder().first() != "demo-home" }
    }

    @Test
    fun removingAsksConfirmationWithWhatItDeletesAndNothingGoesBeforeTheDeviceOwnerAgrees() {
        show()
        app.scriptBiometrics(BiometricResult.Cancelled)

        rule.onNodeWithTag("manage-remove-demo-home").performClick()

        rule.onNodeWithText("Remove “Home factory”?").assertIsDisplayed()
        rule.onNodeWithText("Deletes its saved settings from this phone.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Revokes the key on the server if it can be reached.", substring = true).assertIsDisplayed()
        app.controls.biometricPromptCount shouldBe 0
        rule.awaitDisplayed("confirm-destructive-confirm")
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("confirm-destructive-issue") }
        app.controls.biometricPromptCount shouldBe 1
        storedOrder() shouldBe listOf("demo-home", "demo-cloud", "demo-test")

        rule.awaitDisplayed("confirm-destructive-confirm")
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { storedOrder() == listOf("demo-cloud", "demo-test") }
        app.controls.biometricPromptCount shouldBe 2
    }

    @Test
    fun decliningTheConfirmationRemovesNothingAndAsksNoOne() {
        show()

        rule.onNodeWithTag("manage-remove-demo-cloud").performClick()
        rule.awaitDisplayed("confirm-destructive-cancel")
        rule.onNodeWithTag("confirm-destructive-cancel").performClick()

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("confirm-destructive-sheet") }
        app.controls.biometricPromptCount shouldBe 0
        storedOrder() shouldBe listOf("demo-home", "demo-cloud", "demo-test")
    }

    @Test
    fun theRowsAndTheDoorsOpenTheirScreens() {
        show()

        rule.onNodeWithTag("manage-open-demo-cloud").performClick()
        rule.onNodeWithTag("add-factory-button").performScrollTo().performClick()
        rule.onNodeWithTag("manage-done-button").performClick()

        awaitThat(
            "the screens to be pushed and Done to pop to the root",
        ) { app.pushed.size >= 2 && app.popsToRoot >= 1 }
        app.pushed shouldBe listOf(AppRoute.EditFactory(DemoId.CLOUD), AppRoute.EditFactory(null))
        app.popsToRoot shouldBe 1
    }
}

private const val LONG_PRESS_MILLIS = 700L
private const val DRAG_DISTANCE = 200f
