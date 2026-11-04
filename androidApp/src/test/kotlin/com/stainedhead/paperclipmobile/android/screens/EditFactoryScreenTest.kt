package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.diagnostics.PairingOutcome
import com.stainedhead.paperclipmobile.android.diagnostics.Recorded
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val HOLDER_SETTLE_MILLIS = 500L
private const val FAKE_PIN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

/** AND-022: add and edit a factory: address rules and refusals, the check, pin, re-pair, sign out and remove. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class EditFactoryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(instanceId: String?) {
        rule.setContent { app.Themed { EditFactoryScreen(app.session, instanceId, app.navigator) } }
    }

    private fun typeAddress(text: String) {
        rule.onNodeWithTag("edit-address").performTextClearance()
        rule.onNodeWithTag("edit-address").performTextInput(text)
    }

    private fun factoryNames(): List<String> =
        runBlocking {
            val holder = checkNotNull(app.session.graph).createManageFactoriesStateHolder()
            try {
                holder.state
                    .first { it.rows.isNotEmpty() }
                    .rows
                    .map { it.name }
            } finally {
                holder.close()
            }
        }

    @Test
    fun eachAddressIssueIsSaidInWordsAsTheAddressIsTyped() {
        show(instanceId = null)
        val cases =
            listOf(
                "paperclip.example.com" to "Start the address with https://",
                "ftp://paperclip.example.com" to "Use a secure https:// address",
                "https://" to "Add the server name after https://",
                "https://bad host" to "The server name has characters that are not allowed",
                "https://paperclip.example.com:99999" to "The port must be a number from 1 to 65535",
                "https://user:pw@paperclip.example.com" to "Remove the user name and password from the address",
                "https://paperclip.example.com?x=1" to "Remove everything after the server address",
            )

        for ((input, message) in cases) {
            typeAddress(input)
            rule.waitUntil(WAIT_MILLIS) { rule.hasText(message) }
        }
    }

    @Test
    fun anAddedFactoryNeedsTheAddressCheckedBeforeItCanBeSaved() {
        show(instanceId = null)
        rule.onNodeWithTag("edit-save-button").assertIsNotEnabled()
        rule.onNodeWithTag("edit-name").performTextInput("Staging factory")
        typeAddress("https://paperclip.staging.example.com")
        rule.onNodeWithTag("edit-save-button").assertIsNotEnabled()

        rule.onNodeWithTag("edit-check-address").performScrollTo().performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Server found") }
        rule.onNodeWithText("private network", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Paperclip 1.4.0", substring = true).assertIsDisplayed()
        rule.waitUntil(WAIT_MILLIS) { rule.isEnabled("edit-save-button") }
        rule.onNodeWithTag("edit-save-button").performClick()

        rule.waitUntil(WAIT_MILLIS) { app.pops == 1 }
        factoryNames() shouldBe listOf("Home factory", "Cloud factory", "Test factory", "Staging factory")
    }

    @Test
    fun anExistingFactoryOpensWithItsNameAddressAndKeyStatus() {
        show(DemoId.CLOUD)

        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Cloud factory") }
        rule.onNodeWithTag("edit-address").assertTextEquals("https://paperclip.example.com")
        rule.onNodeWithTag("edit-status").assertTextEquals("Key expired")
        rule.onNodeWithTag("edit-pair-again").assertIsEnabled()
    }

    @Test
    fun aConnectedFactoryShowsItsCompaniesRolesAndKeyDays() {
        show(DemoId.HOME)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("edit-access-company-company-acme") }
        rule.onNodeWithText("YOUR ACCESS · ALEX RIVERA").assertIsDisplayed()
        rule.onNodeWithTag("edit-access-company-company-acme").assertTextEquals("Acme Labs", "Owner")
        rule.onNodeWithTag("edit-access-company-company-side").assertTextEquals("Side Project Co", "Operator")
        rule.onNodeWithTag("edit-board-key").assertTextEquals("Board key", "Board key expires in 23 days")
    }

    @Test
    fun changingTheAddressWarnsThatTheKeyGoesAndNeedsANewCheck() {
        show(DemoId.HOME)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Home factory") }

        typeAddress("https://paperclip.other.example.com")

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("edit-address-warning") }
        rule.onNodeWithTag("edit-save-button").assertIsNotEnabled()
    }

    @Test
    fun pairingAgainSignsAnExpiredFactoryInAgain() {
        show(DemoId.CLOUD)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Cloud factory") }

        rule.onNodeWithTag("edit-pair-again").performScrollTo().performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.onNodeWithTag("edit-status").hasText("Connected") }
    }

    @Test
    fun aRePairThatWasApprovedIsRecordedInTheDiagnosticsAsAnOutcomeOnly() {
        show(DemoId.CLOUD)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Cloud factory") }

        rule.onNodeWithTag("edit-pair-again").performScrollTo().performClick()

        rule.waitUntil(WAIT_MILLIS) {
            app.session.diagnostics.report.value.pairing == Recorded.Value(PairingOutcome.Approved)
        }
    }

    @Test
    fun pairingAgainNeedsAConnection() {
        show(DemoId.CLOUD)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Cloud factory") }

        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("edit-offline") }
        rule.onNodeWithTag("edit-pair-again").assertIsNotEnabled()
    }

    @Test
    fun aCertificatePinIsCheckedAndListed() {
        show(DemoId.CLOUD)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Cloud factory") }

        rule.onNodeWithTag("edit-pin-toggle").performScrollTo().performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("edit-pin-field") }
        rule.onNodeWithTag("edit-pin-field").performTextInput("not a pin")
        rule.onNodeWithTag("edit-pin-add").performScrollTo().performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("That is not a valid pin.") }

        rule.onNodeWithTag("edit-pin-field").performTextClearance()
        rule.onNodeWithTag("edit-pin-field").performTextInput(FAKE_PIN)
        rule.onNodeWithTag("edit-pin-add").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasText(FAKE_PIN) }
    }

    @Test
    fun cancelWithNothingTypedLeavesAtOnceAndWithEditsAsksFirst() {
        show(DemoId.CLOUD)
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Cloud factory") }
        rule.onNodeWithTag("edit-cancel-button").performClick()
        app.pops shouldBe 1

        rule.onNodeWithTag("edit-name").performTextReplacement("Cloud factory two")
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Cloud factory two") }
        settleHolder()
        rule.onNodeWithTag("edit-cancel-button").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Discard your changes?") }
        rule.onNodeWithText("Keep editing").performClick()
        app.pops shouldBe 1

        rule.onNodeWithTag("edit-cancel-button").performClick()
        rule.onNodeWithText("Discard changes").performClick()
        app.pops shouldBe 2
    }

    @Test
    fun signingOutAsksForConfirmationThenLeavesTheFactorySignedOut() {
        show(DemoId.HOME)
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("edit-sign-out") }

        rule.onNodeWithTag("edit-sign-out").performScrollTo().performClick()
        rule.onNodeWithText("Sign out of “Home factory”?").assertIsDisplayed()
        rule.onNodeWithTag("confirm-sheet-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.onNodeWithTag("edit-status").hasText("Signed out") }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("edit-key-outcome") }
    }

    @Test
    fun removingAsksConfirmationThenTheDeviceOwnerAndACancelledPromptRemovesNothing() {
        show(DemoId.HOME)
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("edit-remove") }
        app.scriptBiometrics(BiometricResult.Cancelled)

        rule.onNodeWithTag("edit-remove").performScrollTo().performClick()
        rule.onNodeWithText("Remove “Home factory”?").assertIsDisplayed()
        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("confirm-destructive-issue") }
        rule.onNodeWithText("Authentication was cancelled. Nothing was changed.").assertIsDisplayed()
        app.controls.biometricPromptCount shouldBe 1
        app.popsToRoot shouldBe 0
        factoryNames() shouldBe listOf("Home factory", "Cloud factory", "Test factory")

        rule.onNodeWithTag("confirm-destructive-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { app.popsToRoot == 1 }
        app.controls.biometricPromptCount shouldBe 2
        factoryNames() shouldBe listOf("Cloud factory", "Test factory")
    }
}

/** The holder reads typed text on another dispatcher; give it a moment before asking what it made of it. */
private fun settleHolder() = runBlocking { delay(HOLDER_SETTLE_MILLIS) }

private fun ComposeTestRule.isEnabled(tag: String) = runCatching { onNodeWithTag(tag).assertIsEnabled() }.isSuccess

private fun SemanticsNodeInteraction.hasText(text: String) = runCatching { assertTextEquals(text) }.isSuccess
