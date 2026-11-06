package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AND-024: this phone (name, app lock, idle period), each factory, the doors, and signing out everywhere. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show() {
        rule.setContent { app.Themed { SettingsScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Home factory") }
    }

    /** What the shared core stores, read through a holder of its own. */
    private fun stored() =
        runBlocking {
            val holder = checkNotNull(app.session.graph).createSettingsStateHolder()
            try {
                holder.state.first { it.deviceName.isNotEmpty() }
            } finally {
                holder.close()
            }
        }

    @Test
    fun thePhoneAndEveryFactoryAreShownAsInTheMock() {
        show()

        rule.onNodeWithTag("settings-device-name").assertTextContains("Alex's iPhone")
        rule.onNodeWithTag("settings-idle-period").assertTextEquals("Lock after", "5 minutes")
        rule.onNodeWithTag("settings-factory-demo-home", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("settings-factory-status-demo-home", useUnmergedTree = true).assertTextEquals("Connected")
        rule.onNodeWithTag("settings-factory-status-demo-cloud", useUnmergedTree = true).assertTextEquals("Key expired")
        rule.onNodeWithTag("settings-factory-status-demo-test", useUnmergedTree = true).assertTextEquals("Signed out")
    }

    @Test
    fun destructiveActionsAlwaysAskAndTheSwitchSaysSoButCannotBeChanged() {
        show()

        rule.onNodeWithTag("settings-destructive-toggle").assertIsNotEnabled()
        rule.onNodeWithText("Pause, cancel, reject and budget always ask", substring = true).assertIsDisplayed()
    }

    @Test
    fun theDeviceNameIsSavedWhenTheKeyboardIsDone() {
        show()

        rule.onNodeWithTag("settings-device-name").performTextReplacement("Pixel Pro")
        rule.onNodeWithTag("settings-device-name").performImeAction()

        rule.waitUntil(WAIT_MILLIS) { stored().deviceName == "Pixel Pro" }
    }

    @Test
    fun anEmptyDeviceNameIsRefusedInWords() {
        show()

        rule.onNodeWithTag("settings-device-name").performTextReplacement("")
        rule.onNodeWithTag("settings-device-name").performImeAction()

        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Use 1–60 characters.") }
    }

    @Test
    fun theIdlePeriodIsChosenFromTheOptionsTheCoreOffers() {
        show()

        rule.onNodeWithTag("settings-idle-period").performClick()
        for (period in IdlePeriod.entries) rule.awaitDisplayed("idle-option-${period.name}")
        rule.onNodeWithTag("idle-option-FifteenMinutes").performClick()

        rule.waitUntil(WAIT_MILLIS) { stored().idlePeriod == IdlePeriod.FifteenMinutes }
        rule.waitUntil(WAIT_MILLIS) {
            runCatching {
                rule
                    .onNodeWithTag(
                        "settings-idle-period",
                    ).assertTextEquals("Lock after", "15 minutes")
            }.isSuccess
        }
        rule.onNodeWithTag("settings-idle-period").assertTextEquals("Lock after", "15 minutes")
    }

    @Test
    fun theAppLockSwitchFollowsTheCore() {
        show()
        stored().appLockEnabled shouldBe true

        rule.onNodeWithTag("settings-app-lock-toggle").performClick()

        rule.waitUntil(WAIT_MILLIS) { !stored().appLockEnabled }
    }

    @Test
    fun theDoorsOpenTheirScreens() {
        show()

        rule.onNodeWithTag("settings-factory-demo-cloud").performClick()
        rule.onNodeWithTag("settings-paired-devices").performScrollTo().performClick()
        rule.onNodeWithTag("settings-diagnostics").performScrollTo().performClick()

        awaitThat("the three doors to push their screens") { app.pushed.size >= 3 }
        app.pushed shouldBe listOf(AppRoute.EditFactory(DemoId.CLOUD), AppRoute.PairedDevices, AppRoute.Diagnostics)
    }

    @Test
    fun signingOutEverywhereAsksFirstAndThenSignsEveryFactoryOut() {
        show()

        rule.onNodeWithTag("settings-sign-out-everywhere").performScrollTo().performClick()
        rule.awaitTextDisplayed("Sign out of every factory?")
        rule.awaitDisplayed("confirm-sheet-cancel")
        rule.onNodeWithTag("confirm-sheet-cancel").performClick()
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("confirm-sheet") }
        rule.onNodeWithTag("settings-factory-status-demo-home", useUnmergedTree = true).assertTextEquals("Connected")

        rule.onNodeWithTag("settings-sign-out-everywhere").performScrollTo().performClick()
        rule.awaitDisplayed("confirm-sheet-confirm")
        rule.onNodeWithTag("confirm-sheet-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) {
            rule.onNodeWithTag("settings-factory-status-demo-home", useUnmergedTree = true).hasTextValue("Signed out")
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.hasTextValue(text: String) =
    runCatching { assertTextEquals(text) }.isSuccess
