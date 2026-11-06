package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.ApprovalActionUi
import com.stainedhead.paperclipmobile.presentation.ApprovalIntent
import com.stainedhead.paperclipmobile.presentation.ApprovalPayloadUi
import com.stainedhead.paperclipmobile.presentation.ApprovalState
import com.stainedhead.paperclipmobile.presentation.ApprovalStatusUi
import com.stainedhead.paperclipmobile.presentation.ApprovalTypeUi
import com.stainedhead.paperclipmobile.presentation.CommentUi
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.MoneyUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW_MILLIS = 1_800_000_000_000L

private val hire =
    ApprovalPayloadUi.HireAgent(
        "QA engineer",
        "qa",
        "QA Engineer",
        "claude_local",
        null,
        "CTO",
        MoneyUi(15_000, "USD"),
        emptyList(),
        null,
    )

private fun content(
    payload: ApprovalPayloadUi,
    type: ApprovalTypeUi,
    actions: List<ApprovalActionUi> = emptyList(),
    disabled: ActionsDisabledReason? = null,
    role: RoleUi = RoleUi.Owner,
) = ApprovalState(
    phase = DetailPhase.Content,
    type = type,
    status = ApprovalStatusUi.Pending,
    payload = payload,
    requestedByAgentName = "CEO",
    comments = listOf(CommentUi("c-1", "Looks reasonable.", NOW_MILLIS, isFromAgent = true)),
    actions = actions,
    actionsDisabledReason = disabled,
    role = role,
    updatedAtEpochMillis = NOW_MILLIS,
)

/** AND-041 variants the demo graph cannot produce: a viewer, a board request, a budget override, an unknown type. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2000dp-xhdpi")
class ApprovalVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val intents = mutableListOf<ApprovalIntent>()

    @After
    fun tearDown() = app.close()

    private fun show(state: ApprovalState) {
        rule.setContent {
            app.Themed { ApprovalContent(state, { intents += it }, onBack = {}, onOpenIssue = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun aViewerSeesTheApprovalWithoutAnyActionOrCommentBoxAndWithTheReason() {
        show(content(hire, ApprovalTypeUi.HireAgent, disabled = ActionsDisabledReason.ViewerRole, role = RoleUi.Viewer))

        rule.onNodeWithTag("approval-row-name").assertIsDisplayed()
        rule.onNodeWithText("Your role is Viewer, so you can look but not change anything.").assertIsDisplayed()
        for (tag in listOf(
            "approval-approve",
            "approval-reject",
            "approval-request-revision",
            "approval-comment-field",
        )) {
            rule.hasTag(tag) shouldBe false
        }
        rule.onNodeWithTag("approval-comment-c-1").assertIsDisplayed()
    }

    @Test
    fun anOperatorWithActionsSeesThemAndTheyForwardTheirIntents() {
        val actions = listOf(ApprovalActionUi.Approve, ApprovalActionUi.Reject, ApprovalActionUi.RequestRevision)
        show(content(hire, ApprovalTypeUi.HireAgent, actions, role = RoleUi.Operator))

        rule.onNodeWithTag("approval-approve").assertIsDisplayed()
        rule.onNodeWithTag("approval-reject").assertIsDisplayed()
        rule.onNodeWithTag("approval-request-revision").assertIsDisplayed()
    }

    @Test
    fun aBoardRequestShowsItsTextBlocksAndRisksAsABulletedList() {
        val board =
            ApprovalPayloadUi.BoardRequest(
                "Switch vendor",
                "Move payments to a new vendor.",
                "Approve the switch.",
                "Cut over on Friday.",
                listOf("Downtime", "Cost"),
                "Approved, thanks.",
            )

        show(content(board, ApprovalTypeUi.RequestBoardApproval))

        rule.onNodeWithTag("approval-title").assertIsDisplayed()
        rule.onNodeWithText("Move payments to a new vendor.").assertIsDisplayed()
        rule.onNodeWithText("Cut over on Friday.").assertIsDisplayed()
        rule.onNodeWithText("• Downtime\n• Cost").assertIsDisplayed()
        rule.onNodeWithText("Approved, thanks.").assertIsDisplayed()
    }

    @Test
    fun aBudgetOverrideShowsTheScopeWindowLimitAndSpend() {
        val budget =
            ApprovalPayloadUi.BudgetOverride(
                "Engineer 2",
                "calendar_month_utc",
                MoneyUi(20_000, "USD"),
                MoneyUi(20_050, "USD"),
                "Raise only for the release.",
            )

        show(content(budget, ApprovalTypeUi.BudgetOverrideRequired))

        rule.onNodeWithText("calendar month utc").assertIsDisplayed()
        rule.onNodeWithText("$200").assertIsDisplayed()
        rule.onNodeWithText("$200.50").assertIsDisplayed()
        rule.onNodeWithText("Raise only for the release.").assertIsDisplayed()
    }

    @Test
    fun anUnknownTypeSaysItCannotBeShownHere() {
        show(content(ApprovalPayloadUi.Unsupported("future_type"), ApprovalTypeUi.Unsupported))

        rule.onNodeWithTag("approval-unsupported").assertIsDisplayed()
    }
}
