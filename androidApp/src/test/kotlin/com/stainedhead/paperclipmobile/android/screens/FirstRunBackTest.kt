package com.stainedhead.paperclipmobile.android.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** R-027: system Back on the Pair step does what the Back button does, and Back on the Address step leaves. */
@RunWith(AndroidJUnit4::class)
class FirstRunBackTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app = firstRunApp()

    @After
    fun tearDown() = app.close()

    private fun pressBack() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    private fun walkToThePairStep() {
        rule.setContent { app.Themed { FirstRunScreen(app.session, onTryDemo = null) } }
        rule.onNodeWithTag("address-field").performTextInput("https://paperclip.example.com")
        rule.waitUntil(
            WAIT_MILLIS,
        ) { runCatching { rule.onNodeWithTag("check-address-button").assertIsEnabled() }.isSuccess }
        rule.onNodeWithTag("check-address-button").performScrollTo().performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("probe-result") }
        rule.onNodeWithTag("continue-button").performScrollTo().performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("pair-button") }
    }

    @Test
    fun systemBackOnThePairStepReturnsToTheAddressWithPairingIdle() {
        walkToThePairStep()

        pressBack()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("address-field") }
        val state = checkNotNull(app.session.firstRun).state.value
        state.step shouldBe FirstRunStep.Address
        state.pairing shouldBe PairingStatus.Idle
        rule.hasTag("pair-button") shouldBe false
    }

    @Test
    fun systemBackOnTheAddressStepIsLeftToTheSystem() {
        rule.setContent { app.Themed { FirstRunScreen(app.session, onTryDemo = null) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("address-field") }

        // No handler is registered for the Address step, so the dispatcher has nothing to consume the press.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.hasEnabledCallbacks() } shouldBe false
    }
}
