package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val TOO_LONG = 41
private const val SETTLE_MILLIS = 500L

/** AND-040: the billing decision on the demo graph; effects before the tap, the device-owner gate, inputs, finals. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class DecisionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val billing =
        AppRoute.Decision(DemoId.DECISION_BILLING, "CTO", "today", "attention:decision:dec-1182", "PAP-1182")

    @After
    fun tearDown() = app.close()

    private fun show(
        route: AppRoute.Decision = billing,
        ready: String = "decision-option-ship",
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { DecisionScreen(app.session, route, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag(ready) }
    }

    @Test
    fun theHeaderNamesTheKindDeadlineProposerAndExpiry() {
        show()

        rule.onNodeWithTag("decision-kind-line").assertTextEquals("DECISION · DECIDE BY TODAY")
        rule.onNodeWithTag("decision-title").assertTextEquals("Ship the billing migration to production?")
        rule.onNodeWithTag("decision-meta").assertTextEquals("Proposed by CTO · PAP-1182")
        rule.onNodeWithTag("decision-expires").assertTextContains("Expires", substring = true)
        rule.onNodeWithTag("decision-body").assertIsDisplayed()
        rule.hasTag("updated-label") shouldBe true
    }

    @Test
    fun everyOptionShowsItsEffectsBeforeAnythingIsChosen() {
        show()

        rule.onNodeWithText("Ship tonight behind a feature flag").assertIsDisplayed()
        rule.onNodeWithText("Sets the issue status to in progress", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Assigns the issue to DevOps", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Posts a comment: Rollout plan", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Creates the issue “QA regression on refunds”", substring = true).assertIsDisplayed()
        rule
            .onNodeWithText("Cancels the issue and its sub-issues: Cancelled by the board.", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithTag("decision-confirm").assertIsNotEnabled()
        rule.isSelected("decision-option-ship") shouldBe false
    }

    @Test
    fun onlyTheDestructiveOptionCarriesTheLockGlyph() {
        show()

        rule.onNodeWithTag("decision-option-lock-cancel", useUnmergedTree = true).assertIsDisplayed()
        rule.hasTag("decision-option-lock-ship") shouldBe false
        rule.hasTag("decision-option-lock-wait") shouldBe false
    }

    @Test
    fun choosingAnOptionRestatesItAndEnablesConfirm() {
        show()

        rule.onNodeWithTag("decision-option-wait").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("decision-option-wait") }
        rule.onNodeWithTag("decision-chosen").assertTextEquals("Selected: Wait for QA sign-off")
        rule.onNodeWithTag("decision-confirm").assertIsEnabled()
    }

    @Test
    fun aNonDestructiveChoiceGoesThroughWithoutAskingTheDeviceOwnerAndShowsWhatHappened() {
        show()

        rule.onNodeWithTag("decision-option-ship").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("decision-option-ship") }
        rule.onNodeWithTag("decision-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("decision-execution") }
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("All effects ran") }
        for (index in 0..2) rule.onNodeWithTag("decision-result-$index").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("decision-result-0-status", useUnmergedTree = true).assertTextEquals("Done")
        rule.hasTag("decision-final") shouldBe true
        rule.hasTag("decision-option-ship") shouldBe false
        app.controls.biometricPromptCount shouldBe 0
    }

    @Test
    fun aDestructiveChoiceWaitsForTheDeviceOwnerAndDoesNothingWhenTheyCancel() {
        show()
        app.scriptBiometrics(BiometricResult.Cancelled)

        rule.onNodeWithTag("decision-option-cancel").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("decision-option-cancel") }
        rule.onNodeWithTag("decision-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("feedback-banner") }
        rule.onNodeWithTag("feedback-message").assertTextEquals("Authentication was cancelled. Nothing was changed.")
        app.controls.biometricPromptCount shouldBe 1
        rule.hasTag("decision-execution") shouldBe false
        rule.hasTag("decision-final") shouldBe false
        rule.onNodeWithTag("decision-option-cancel").assertIsDisplayed()

        rule.onNodeWithTag("decision-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("decision-execution") }
        app.controls.biometricPromptCount shouldBe 2
        rule.hasText("Already decided: Cancel the migration") shouldBe true
    }

    @Test
    fun aRequiredInputBlocksTheChoiceUntilItIsAnswered() {
        show(AppRoute.Decision(DemoId.DECISION_KEYS), ready = "decision-input-reason")

        rule.onNodeWithTag("decision-input-reason-help", useUnmergedTree = true).assertTextEquals("0 / 40")
        rule.onNodeWithTag("decision-option-yes").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("decision-option-yes") }
        rule.onNodeWithTag("decision-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("decision-input-reason-issue") }
        rule
            .onNodeWithTag(
                "decision-input-reason-issue",
                useUnmergedTree = true,
            ).assertTextEquals("This answer is required.")
        rule.hasTag("decision-execution") shouldBe false
    }

    @Test
    fun anInputLimitIsShownAsACounterAndNeverTruncatedHere() {
        show(AppRoute.Decision(DemoId.DECISION_KEYS), ready = "decision-input-reason")

        rule.onNodeWithTag("decision-input-reason").performTextInput("x".repeat(TOO_LONG))
        rule.onNodeWithTag("decision-input-reason-help", useUnmergedTree = true).assertTextEquals("$TOO_LONG / 40")
        settle()
        rule.onNodeWithTag("decision-option-yes").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("decision-option-yes") }
        rule.onNodeWithTag("decision-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasText("That answer is too long.") }
        rule.hasTag("decision-execution") shouldBe false
    }

    @Test
    fun aDecisionDecidedElsewhereShowsItsFinalStateAndNoOptions() {
        app.controls.resolveDecisionElsewhere(DemoId.DECISION_BILLING, "wait")

        show(ready = "decision-final")

        rule.onNodeWithText("Already decided: Wait for QA sign-off").assertIsDisplayed()
        rule.hasTag("decision-option-ship") shouldBe false
        rule.hasTag("decision-confirm") shouldBe false
        rule.hasTag("decision-dismiss") shouldBe false
    }

    @Test
    fun snoozeIsOfferedOnlyWhenOpenedFromAnInboxCardAndClosesTheScreen() {
        show()

        rule.onNodeWithTag("decision-snooze").performClick()
        rule.onNodeWithTag("snooze-sheet").assertIsDisplayed()
        rule.onNodeWithTag("snooze-Tonight").performClick()

        rule.waitUntil(WAIT_MILLIS) { app.pops == 1 }
    }

    @Test
    fun withoutAnInboxCardThereIsNoSnooze() {
        show(AppRoute.Decision(DemoId.DECISION_BILLING))

        rule.hasTag("decision-snooze") shouldBe false
        rule.hasTag("decision-dismiss") shouldBe true
    }

    @Test
    fun offlineTheChoiceIsDisabledWithItsReason() {
        show()
        rule.onNodeWithTag("decision-option-ship").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected("decision-option-ship") }

        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.onNodeWithText("You're offline. Actions are unavailable.").assertIsDisplayed()
        rule.onNodeWithTag("decision-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("decision-dismiss").assertIsNotEnabled()
        rule.hasTag("decision-snooze") shouldBe false
    }

    @Test
    fun aDecisionThatCannotBeReadSaysSoWithAWayToTryAgain() {
        show(AppRoute.Decision("missing"), ready = "detail-failed")

        rule.hasTag("error-banner") shouldBe true
        rule.hasTag("decision-confirm") shouldBe false
    }
}

/** The holder reads typed text on another dispatcher; give it a moment before the next tap asks what it made of it. */
private fun settle() = runBlocking { delay(SETTLE_MILLIS) }
