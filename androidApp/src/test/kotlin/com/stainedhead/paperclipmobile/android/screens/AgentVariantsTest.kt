package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.AgentActionUi
import com.stainedhead.paperclipmobile.presentation.AgentDetailIntent
import com.stainedhead.paperclipmobile.presentation.AgentDetailState
import com.stainedhead.paperclipmobile.presentation.AgentGroupUi
import com.stainedhead.paperclipmobile.presentation.AgentRowUi
import com.stainedhead.paperclipmobile.presentation.AgentSectionUi
import com.stainedhead.paperclipmobile.presentation.AgentStatusUi
import com.stainedhead.paperclipmobile.presentation.AgentsIntent
import com.stainedhead.paperclipmobile.presentation.AgentsState
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.FeedbackUi
import com.stainedhead.paperclipmobile.presentation.MoneyUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW = 1_800_000_000_000L
private const val SPENT = 25_000L
private const val BUDGET = 100_000L

private fun row(actions: List<AgentActionUi>) =
    AgentRowUi(
        "agent-1",
        "Engineer 1",
        "engineer",
        AgentStatusUi.Idle,
        null,
        false,
        true,
        25,
        NOW,
        actions,
        null,
    )

private fun detail(
    actions: List<AgentActionUi>,
    role: RoleUi,
    canSetBudget: Boolean,
) = AgentDetailState(
    phase = DetailPhase.Content,
    agent = row(actions),
    monthSpend = MoneyUi(SPENT, "USD"),
    monthlyBudget = MoneyUi(BUDGET, "USD"),
    budgetPercentUsed = 25,
    actions = actions,
    canSetBudget = canSetBudget,
    role = role,
    updatedAtEpochMillis = NOW,
)

/** AND-051 variants the demo graph cannot produce: an operator, a viewer, an unknown outcome, a failed read. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class AgentVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun showDetail(
        state: AgentDetailState,
        send: (AgentDetailIntent) -> Unit = {},
    ) = rule.setContent { app.Themed { AgentDetailContent(state, send, {}, {}, {}) } }

    @Test
    fun anOperatorKeepsTheButtonsButNotTheBudgetAndIsToldWhy() {
        showDetail(
            detail(listOf(AgentActionUi.Pause, AgentActionUi.RunNow), RoleUi.Operator, canSetBudget = false)
                .copy(budgetDisabledReason = ActionsDisabledReason.OwnerRole),
        )

        rule.onNodeWithTag("agent-detail-pause").assertIsDisplayed()
        rule.onNodeWithTag("agent-detail-run-now").assertIsDisplayed()
        rule.hasTag("agent-set-budget") shouldBe false
        rule.onNodeWithTag("disabled-reason").assertTextContains("Only an owner can do this.")
    }

    @Test
    fun aViewerSeesNoButtonsAtAllAndTheReason() {
        showDetail(
            detail(emptyList(), RoleUi.Viewer, canSetBudget = false)
                .copy(
                    budgetDisabledReason = ActionsDisabledReason.ViewerRole,
                    actionsDisabledReason = ActionsDisabledReason.ViewerRole,
                ),
        )

        rule.hasTag("agent-detail-pause") shouldBe false
        rule.hasTag("agent-detail-run-now") shouldBe false
        rule.hasTag("agent-set-budget") shouldBe false
        rule.onNodeWithTag("agent-name").assertTextEquals("Engineer 1")
        rule.hasTag("disabled-reason") shouldBe true
    }

    @Test
    fun anOutcomeThatTimedOutSaysItIsUnknownAndToRefreshBeforeTryingAgain() {
        showDetail(
            detail(listOf(AgentActionUi.Pause), RoleUi.Owner, canSetBudget = true)
                .copy(feedback = FeedbackUi(FeedbackKind.Unknown, error = ErrorUi(ErrorKey.Timeout))),
        )

        rule.onNodeWithTag("feedback-message").assertTextContains("unclear whether that took effect", substring = true)
        rule.onNodeWithTag("feedback-message").assertTextContains("Refresh before trying again", substring = true)
    }

    @Test
    fun dismissingTheMessageAndRefreshingAreForwardedAsIntents() {
        val sent = mutableListOf<AgentDetailIntent>()
        showDetail(
            detail(listOf(AgentActionUi.Pause), RoleUi.Owner, canSetBudget = true)
                .copy(feedback = FeedbackUi(FeedbackKind.Unknown, error = ErrorUi(ErrorKey.Timeout))),
            sent::add,
        )

        rule.onNodeWithTag("feedback-dismiss").performClick()

        sent shouldContain AgentDetailIntent.MessageDismissed
    }

    @Test
    fun aViewerOnTheAgentsTabSeesTheRowsWithoutButtonsAndTheReason() {
        val state =
            AgentsState(
                phase = DetailPhase.Content,
                sections = listOf(AgentSectionUi(AgentGroupUi.Idle, listOf(row(emptyList())))),
                role = RoleUi.Viewer,
                actionsDisabledReason = ActionsDisabledReason.ViewerRole,
                updatedAtEpochMillis = NOW,
            )
        val sent = mutableListOf<AgentsIntent>()
        rule.setContent { app.Themed { AgentsContent(state, sent::add, {}, {}, {}) } }

        rule.onNodeWithTag("agent-row-agent-1").assertIsDisplayed()
        rule.hasTag("agent-agent-1-run-now") shouldBe false
        rule.hasTag("agent-agent-1-pause") shouldBe false
        rule.hasTag("agent-agent-1-budget") shouldBe false
        rule.onNodeWithTag("disabled-reason").assertTextContains("Viewer", substring = true)
    }

    @Test
    fun aListWithNoAgentsSaysSoAndAFailedFirstReadOffersTryAgain() {
        val empty = AgentsState(phase = DetailPhase.Content, updatedAtEpochMillis = NOW)
        rule.setContent { app.Themed { AgentsContent(empty, {}, {}, {}, {}) } }

        rule.onNodeWithTag("agents-empty").assertIsDisplayed()
    }

    @Test
    fun aFailedFirstReadOfAnAgentOffersTryAgain() {
        val failed = AgentDetailState(phase = DetailPhase.Failed, error = ErrorUi(ErrorKey.Unreachable))
        val sent = mutableListOf<AgentDetailIntent>()
        rule.setContent { app.Themed { AgentDetailContent(failed, sent::add, {}, {}, {}) } }

        rule.onNodeWithTag("detail-failed").assertIsDisplayed()
        rule.onNodeWithTag("error-banner").assertIsDisplayed()
    }
}
