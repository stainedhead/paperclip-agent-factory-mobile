package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AND-024: the board keys of the person's account on one factory, this phone's marked, each with Revoke. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class PairedDevicesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show() {
        rule.setContent { app.Themed { PairedDevicesScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("device-key-bk-demo-a91f") }
    }

    @Test
    fun theKeysOfTheFirstFactoryAreListedWithThisPhoneMarked() {
        show()

        rule.onNodeWithText("Alex's iPhone").assertIsDisplayed()
        rule.onNodeWithTag("device-this-phone-bk-demo-a91f", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("paperclipai cli").assertIsDisplayed()
        rule.hasTag("device-this-phone-bk-demo-cli") shouldBe false
        rule.onNodeWithText("Signed in as Alex Rivera").assertIsDisplayed()
        rule.onNodeWithText("Never used", substring = true).assertIsDisplayed()
    }

    @Test
    fun revokingAnotherKeyAsksFirstThenRemovesItFromTheList() {
        show()

        rule.onNodeWithTag("revoke-bk-demo-cli").performScrollTo().performClick()
        rule.onNodeWithText("Revoke “paperclipai cli”?").assertIsDisplayed()
        rule.onNodeWithText("Anything using this key loses access to the factory.").assertIsDisplayed()
        rule.onNodeWithTag("confirm-sheet-cancel").performClick()
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("confirm-sheet") }
        rule.hasTag("device-key-bk-demo-cli") shouldBe true

        rule.onNodeWithTag("revoke-bk-demo-cli").performScrollTo().performClick()
        rule.onNodeWithTag("confirm-sheet-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("device-key-bk-demo-cli") }
        rule.hasTag("device-key-bk-demo-a91f") shouldBe true
    }

    @Test
    fun revokingThisPhonesKeySaysItSignsThePhoneOut() {
        show()

        rule.onNodeWithTag("revoke-bk-demo-a91f").performScrollTo().performClick()

        rule
            .onNodeWithText("This is this phone’s key. Revoking it signs this phone out of the factory.")
            .assertIsDisplayed()
    }

    @Test
    fun anotherFactoryCanBeChosenAndItsKeysAreAskedFor() {
        show()
        rule.isSelected("devices-factory-demo-home") shouldBe true

        rule.onNodeWithTag("devices-factory-demo-cloud").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("devices-factory-demo-cloud") }
        rule.isSelected("devices-factory-demo-home") shouldBe false
        // The demo gateway answers every factory with the same keys, so the list is shown again once it has loaded.
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("device-key-bk-demo-cli") }
    }
}
