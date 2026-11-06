package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentState
import com.stainedhead.paperclipmobile.presentation.BudgetIntent
import com.stainedhead.paperclipmobile.presentation.BudgetThresholdUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.MoneyUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW_MILLIS = 1_800_000_000_000L
private val twoHundred = MoneyUi(20_000, "USD")

/** The incident of the mock (Engineer 2 at its $200 hard stop) as the shared state describes it for [role]. */
fun budgetState(
    role: RoleUi,
    choice: BudgetChoice? = null,
    amountMinor: Long? = null,
) = BudgetIncidentState(
    phase = DetailPhase.Content,
    scopeName = "Engineer 2",
    scopeType = "agent",
    threshold = BudgetThresholdUi.Hard,
    observed = twoHundred,
    limit = twoHundred,
    observedPercent = 100,
    choice = choice,
    amountMinorUnits = amountMinor,
    canKeepPaused = role != RoleUi.Viewer,
    canRaise = role == RoleUi.Owner,
    raiseDisabledReason =
        when (role) {
            RoleUi.Owner -> null
            RoleUi.Operator -> ActionsDisabledReason.OwnerRole
            RoleUi.Viewer -> ActionsDisabledReason.ViewerRole
        },
    canSubmit = choice == BudgetChoice.KeepPaused || (choice == BudgetChoice.RaiseAndResume && amountMinor != null),
    submitNeedsDeviceAuth = choice == BudgetChoice.RaiseAndResume,
    actionsDisabledReason = if (role == RoleUi.Viewer) ActionsDisabledReason.ViewerRole else null,
    role = role,
    updatedAtEpochMillis = NOW_MILLIS,
)

/** AND-042 role variants the demo graph cannot produce: an operator (not an owner) and a viewer. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2000dp-xhdpi")
class BudgetVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val intents = mutableListOf<BudgetIntent>()

    @After
    fun tearDown() = app.close()

    private fun show(state: BudgetIncidentState) {
        rule.setContent { app.Themed { BudgetContent(state, { intents += it }, onClose = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun anOperatorMayKeepPausedButNotRaiseAndSeesWhy() {
        show(budgetState(RoleUi.Operator))

        rule.onNodeWithTag("budget-choice-keep").assertIsEnabled()
        rule.onNodeWithTag("budget-choice-raise").assertIsNotEnabled()
        rule.onNodeWithText("Only an owner can do this.").assertIsDisplayed()

        rule.onNodeWithTag("budget-choice-keep").performClick()

        intents shouldContain BudgetIntent.ChoiceSelected(BudgetChoice.KeepPaused)
    }

    @Test
    fun aViewerMayDoNeitherAndSeesWhy() {
        show(budgetState(RoleUi.Viewer))

        rule.onNodeWithTag("budget-choice-keep").assertIsNotEnabled()
        rule.onNodeWithTag("budget-choice-raise").assertIsNotEnabled()
        rule.onNodeWithTag("budget-submit").assertIsNotEnabled()
        rule.hasText("Your role is Viewer, so you can look but not change anything.", substring = true) shouldBe true
    }

    @Test
    fun theAmountIsSentWithTheLocalesSeparatorAndTheCurrencysDigits() {
        show(budgetState(RoleUi.Owner, BudgetChoice.RaiseAndResume))

        rule.onNodeWithTag("budget-amount").assertIsDisplayed()
        rule.onNodeWithTag("budget-submit").assertIsNotEnabled()
        rule.onNodeWithTag("budget-amount").performTextInput("250.5")

        intents.last() shouldBe BudgetIntent.AmountTyped("250.5", ".", 2)
    }
}
