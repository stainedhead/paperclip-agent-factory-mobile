package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val SERVER_SPEND_CENTS = 30_000L
private const val DEMO_CARDS = 9

/** AND-042: the Engineer 2 hard stop ($200 of $200) opened from its Inbox card, on the demo graph. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class BudgetSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun open() {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { InboxScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS }
        val card =
            checkNotNull(app.session.inbox.value)
                .state.value.sections
                .flatMap { it.items }
        val budget = card.first { it.title == "Engineer 2 hit its monthly budget" }
        rule.onNodeWithTag("inbox-raise-${budget.id}").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("budget-usage") }
    }

    private fun choose(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected(tag) }
    }

    private fun typeAmount(text: String) {
        rule.onNodeWithTag("budget-amount").performTextInput(text)
        rule.awaitFieldText("budget-amount", text)
    }

    @Test
    fun theSheetShowsTheThresholdScopeAndHowMuchOfTheBudgetIsUsed() {
        open()

        rule.onNodeWithText("Hard stop reached").assertIsDisplayed()
        rule.onNodeWithTag("budget-scope").assertTextEquals("Engineer 2")
        rule.onNodeWithTag("budget-usage").assertTextEquals("$200 of $200 monthly budget · 100% used")
        rule.onNodeWithTag("budget-choice-keep").assertIsDisplayed()
        rule.onNodeWithTag("budget-choice-raise").assertIsDisplayed()
        rule.onNodeWithTag("budget-submit").assertTextEquals("Choose an option")
        rule.onNodeWithTag("budget-submit").assertIsNotEnabled()
        rule.hasTag("budget-amount") shouldBe false
    }

    @Test
    fun keepingPausedResolvesItWithoutTheDeviceOwnerAndTheCardGoesFromTheInbox() {
        open()

        choose("budget-choice-keep")
        rule.onNodeWithTag("budget-submit").assertTextEquals("Keep paused")
        rule.onNodeWithTag("budget-submit").assertIsEnabled()
        rule.onNodeWithTag("budget-submit").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("budget-outcome") }
        rule.onNodeWithText("Kept paused. The agent stays paused.").assertIsDisplayed()
        app.controls.biometricPromptCount shouldBe 0
        rule.onNodeWithTag("budget-close").performClick()
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS - 1 }
    }

    @Test
    fun theAmountMustBeAboveWhatWasSpentAndTheRaiseWaitsForTheDeviceOwner() {
        open()
        app.scriptBiometrics(BiometricResult.Cancelled)

        choose("budget-choice-raise")
        rule
            .onNodeWithTag(
                "budget-amount-help",
                useUnmergedTree = true,
            ).assertTextEquals("Must be more than $200, what has been spent.")
        typeAmount("150")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("budget-amount-issue") }
        rule
            .onNodeWithTag("budget-amount-issue", useUnmergedTree = true)
            .assertTextEquals("The new budget must be higher than what has been spent.")
        rule.onNodeWithTag("budget-submit").assertIsNotEnabled()

        rule.onNodeWithTag("budget-amount").performTextReplacement("250")
        rule.awaitFieldText("budget-amount", "250")
        rule.waitUntil(WAIT_MILLIS) { rule.isEnabled("budget-submit") }
        rule.onNodeWithTag("budget-submit").assertTextEquals("Raise to $250 and resume")
        rule.onNodeWithTag("budget-submit").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("feedback-banner") }
        rule.onNodeWithTag("feedback-message").assertTextEquals("Authentication was cancelled. Nothing was changed.")
        app.controls.biometricPromptCount shouldBe 1
        rule.hasTag("budget-outcome") shouldBe false

        rule.onNodeWithTag("budget-submit").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("budget-outcome") }
        rule.onNodeWithText("Budget raised. The agent resumes.").assertIsDisplayed()
        app.controls.biometricPromptCount shouldBe 2
    }

    @Test
    fun anAmountTypedThenAbandonedIsNotSentWhenRaiseIsChosenAgain() {
        open()
        choose("budget-choice-raise")
        typeAmount("250")
        rule.waitUntil(WAIT_MILLIS) { rule.isEnabled("budget-submit") }

        choose("budget-choice-keep")
        choose("budget-choice-raise")

        // The field is blank again and the state holds no amount: Submit is off and names no amount (R-024).
        rule
            .onNodeWithTag("budget-amount")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text shouldBe
            ""
        rule.onNodeWithTag("budget-submit").assertIsNotEnabled()
        rule.onNodeWithTag("budget-submit").assertTextEquals("Raise and resume")
    }

    @Test
    fun theServersReasonShowsWhenItRefusesAnAmountTheLocalCheckAccepted() {
        open()
        app.controls.setBudgetServerSpend(SERVER_SPEND_CENTS)

        choose("budget-choice-raise")
        typeAmount("250")
        rule.waitUntil(WAIT_MILLIS) { rule.isEnabled("budget-submit") }
        rule.onNodeWithTag("budget-submit").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("feedback-banner") }
        rule
            .onNodeWithTag("feedback-message")
            .assertTextContains("New budget must exceed current observed spend", substring = true)
        rule.hasTag("budget-outcome") shouldBe false
    }

    @Test
    fun anIncidentResolvedElsewhereShowsThatInsteadOfTheChoices() {
        app.controls.resolveIncidentElsewhere(DemoId.INCIDENT)
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { InboxScreen(app.session, app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.cardCount() == DEMO_CARDS }
        val card =
            checkNotNull(app.session.inbox.value)
                .state.value.sections
                .flatMap { it.items }
        rule
            .onNodeWithTag("inbox-raise-${card.first { it.title == "Engineer 2 hit its monthly budget" }.id}")
            .performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("budget-outcome") || rule.hasTag("detail-failed") }
        rule.hasTag("budget-choice-raise") shouldBe false
    }

    @Test
    fun offlineTheSubmitIsDisabledWithItsReason() {
        open()
        choose("budget-choice-keep")

        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule
            .onAllNodes(
                hasTestTag("disabled-reason") and hasAnyAncestor(hasTestTag("budget-sheet")),
            )[0]
            .assertIsDisplayed()
        rule.onNodeWithTag("budget-submit").assertIsNotEnabled()
    }
}

private fun ComposeTestRule.isEnabled(tag: String) = runCatching { onNodeWithTag(tag).assertIsEnabled() }.isSuccess
