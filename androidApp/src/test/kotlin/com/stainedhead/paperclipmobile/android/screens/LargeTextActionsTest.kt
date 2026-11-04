package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.RoleUi
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val LARGEST = 2.0f
private val MINIMUM_TARGET = 48.dp

/**
 * PRD acceptance criterion 8 for the attention family: at font scale 2.0 the named primary actions of Inbox, Decision,
 * Approval and the Budget incident sheet are displayed (not clipped) and at least 48 dp tall. The font scale is the
 * largest the system offers; the baselines of the same screens are in `AttentionScreenshotTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class LargeTextActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun ComposeTestRule.assertTarget(tag: String) {
        onNodeWithTag(tag).assertIsDisplayed()
        onNodeWithTag(tag).assertHeightIsAtLeast(MINIMUM_TARGET)
    }

    private fun showAtLargest(
        ready: () -> Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.ThemedAt(false, LARGEST, content) }
        rule.waitUntil(WAIT_MILLIS, ready)
        rule.waitForIdle()
    }

    @Test
    fun theInboxCardActionsFitAndAreAtLeast48dpTall() {
        showAtLargest({ rule.cardCount() >= 3 }) { InboxScreen(app.session, app.navigator) }
        val cards =
            checkNotNull(app.session.inbox.value)
                .state.value.sections
                .flatMap { it.items }
        val decision = cards.first { it.title == "Ship the billing migration to production?" }
        val budget = cards.first { it.title == "Engineer 2 hit its monthly budget" }
        val hire = cards.first { it.title == "Hire a QA engineer agent" }

        rule.assertTarget("inbox-review-${decision.id}")
        rule.assertTarget("inbox-keep-paused-${budget.id}")
        rule.assertTarget("inbox-raise-${budget.id}")
        rule.assertTarget("inbox-reject-${hire.id}")
        rule.assertTarget("inbox-details-${hire.id}")
        rule.assertTarget("inbox-approve-${hire.id}")
    }

    @Test
    fun theDecisionActionsFitAndAreAtLeast48dpTall() {
        val route =
            AppRoute.Decision(
                DemoId.DECISION_BILLING,
                "CTO",
                "today",
                "attention:decision:dec-1182",
                "PAP-1182",
            )
        showAtLargest({ rule.hasTag("decision-confirm") }) { DecisionScreen(app.session, route, app.navigator) }

        rule.assertTarget("decision-dismiss")
        rule.assertTarget("decision-confirm")
    }

    @Test
    fun theApprovalActionsFitAndAreAtLeast48dpTall() {
        showAtLargest({ rule.hasTag("approval-approve") }) {
            ApprovalScreen(app.session, AppRoute.Approval(DemoId.APPROVAL_HIRE), app.navigator)
        }

        rule.assertTarget("approval-reject")
        rule.assertTarget("approval-approve")
        rule.assertTarget("approval-request-revision")
    }

    @Test
    fun theBudgetChoicesAndSubmitFitAndAreAtLeast48dpTall() {
        showAtLargest({ true }) {
            BudgetContent(budgetState(RoleUi.Owner, BudgetChoice.KeepPaused), {}, onClose = {})
        }

        rule.assertTarget("budget-choice-keep")
        rule.assertTarget("budget-choice-raise")
        rule.assertTarget("budget-submit")
    }
}
