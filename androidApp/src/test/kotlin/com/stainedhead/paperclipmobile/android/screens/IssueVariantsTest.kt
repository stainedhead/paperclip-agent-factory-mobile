package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.FeedbackUi
import com.stainedhead.paperclipmobile.presentation.IssueHeaderUi
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueRefUi
import com.stainedhead.paperclipmobile.presentation.IssueState
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import com.stainedhead.paperclipmobile.presentation.IssueTab
import com.stainedhead.paperclipmobile.presentation.RoleUi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW = 1_800_000_000_000L

private fun header(status: IssueStatusUi = IssueStatusUi.InProgress) =
    IssueHeaderUi(
        id = "pap-9",
        identifier = "PAP-9",
        title = "Move the cron to the queue",
        summary = null,
        status = status,
        priority = IssuePriorityUi.High,
        assigneeName = "Engineer 2",
        assigneeIsMe = false,
        projectName = "Billing",
        parent = IssueRefUi("pap-1", "PAP-1", "Billing v2", IssueStatusUi.InProgress),
        blockedBy = listOf(IssueRefUi("pap-2", "PAP-2", "Provision the queue", IssueStatusUi.Todo)),
    )

private fun content(
    role: RoleUi = RoleUi.Owner,
    status: IssueStatusUi = IssueStatusUi.InProgress,
) = IssueState(
    phase = DetailPhase.Content,
    header = header(status),
    canComment = role != RoleUi.Viewer,
    canReopen = status == IssueStatusUi.Done,
    canInterrupt = status == IssueStatusUi.InProgress,
    canChangeStatus = role != RoleUi.Viewer,
    statusOptions = listOf(IssueStatusUi.Done),
    canAssign = role == RoleUi.Owner,
    canCreateSubtask = role != RoleUi.Viewer,
    role = role,
    updatedAtEpochMillis = NOW,
)

/** AND-061 variants the demo graph cannot produce: a viewer, a failed send, a failed read, a failed tab. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class IssueVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(
        state: IssueState,
        send: (IssueIntent) -> Unit = {},
        onOpenIssue: (String) -> Unit = {},
    ) = rule.setContent { app.Themed { IssueContent(state, send, onOpenIssue, {}, {}, {}) } }

    private fun fieldText(tag: String) =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text

    @Test
    fun aViewerReadsTheIssueButCannotCommentChangeOrAssignAndIsToldWhy() {
        show(content(RoleUi.Viewer).copy(actionsDisabledReason = ActionsDisabledReason.ViewerRole))

        rule.onNodeWithTag("issue-comment-send").assertIsNotEnabled()
        rule.onNodeWithTag("issue-comment-field").assertIsNotEnabled()
        rule.onNodeWithTag("issue-status-menu").assertIsNotEnabled()
        rule.hasTag("issue-assignee-menu") shouldBe false
        rule.hasTag("issue-assignee") shouldBe true
        rule.hasTag("issue-add-subtask") shouldBe false
        rule.hasTag("disabled-reason") shouldBe true
    }

    @Test
    fun theBlockerAndTheParentAreLinksThatOpenTheirIssues() {
        val opened = mutableListOf<String>()
        show(content(), onOpenIssue = opened::add)

        rule.onNodeWithTag("issue-parent").performClick()
        rule.onNodeWithTag("issue-blocker-pap-2").performClick()

        opened shouldBe listOf("pap-1", "pap-2")
    }

    @Test
    fun textTypedAfterTheTapSurvivesTheCountMovingAndOnlyTheSentTextGoes() {
        val sent = mutableListOf<IssueIntent>()
        var state by mutableStateOf(content().copy(commentSentCount = 0))
        rule.setContent { app.Themed { IssueContent(state, sent::add, {}, {}, {}, {}) } }
        rule.onNodeWithTag("issue-comment-field").performTextInput("first")
        rule.onNodeWithTag("issue-comment-send").performClick()
        rule.onNodeWithTag("issue-comment-field").performTextInput(" and more")

        state = state.copy(commentSentCount = 1)
        rule.waitForIdle()

        sent shouldContain IssueIntent.CommentSubmitted("first", reopen = false, interrupt = false)
        fieldText("issue-comment-field") shouldBe " and more"
    }

    @Test
    fun aBlankCommentCannotBeSent() {
        show(content())

        rule.onNodeWithTag("issue-comment-send").assertIsNotEnabled()
        rule.onNodeWithTag("issue-comment-field").performTextInput("   ")
        rule.onNodeWithTag("issue-comment-send").assertIsNotEnabled()
        rule.onNodeWithTag("issue-comment-field").performTextInput("x")
        rule.onNodeWithTag("issue-comment-send").assertIsEnabled()
    }

    @Test
    fun aFailureShowsInsideTheBarSoItIsSeenWithoutScrolling() {
        show(content().copy(feedback = FeedbackUi(FeedbackKind.Failed, error = ErrorUi(ErrorKey.ServerError))))

        rule
            .onNode(hasTestTag("feedback-banner") and hasAnyAncestor(hasTestTag("issue-comment-bar")))
            .assertExists()
        rule.onAllNodesWithTag("feedback-banner").assertCountEquals(1)
    }

    @Test
    fun aCommentThatFailedComesBackIntoTheBoxBecauseTheCountDidNotMove() {
        val sent = mutableListOf<IssueIntent>()
        show(
            content().copy(
                commentSentCount = 0,
                feedback = FeedbackUi(FeedbackKind.Failed, error = ErrorUi(ErrorKey.ServerError)),
            ),
            sent::add,
        )

        rule.onNodeWithTag("issue-comment-field").performTextInput("Please retry three times")
        rule.onNodeWithTag("issue-comment-send").performClick()

        sent shouldContain IssueIntent.CommentSubmitted("Please retry three times", reopen = false, interrupt = false)
        fieldText("issue-comment-field") shouldBe "Please retry three times"
        rule.onNodeWithTag("feedback-message").assertTextContains("didn't go through", substring = true)
    }

    @Test
    fun theBoxClearsOnlyWhenTheHolderCountsTheCommentAndNeverBefore() {
        var state by mutableStateOf(content())
        rule.setContent { app.Themed { IssueContent(state, {}, {}, {}, {}, {}) } }

        rule.onNodeWithTag("issue-comment-field").performTextInput("On my way")
        rule.onNodeWithTag("issue-comment-send").performClick()
        fieldText("issue-comment-field") shouldBe "On my way"

        state = state.copy(commentSentCount = 1)
        rule.waitForIdle()

        fieldText("issue-comment-field") shouldBe ""
    }

    @Test
    fun theReopenAndInterruptSwitchesAreSentWithTheComment() {
        val sent = mutableListOf<IssueIntent>()
        show(content(status = IssueStatusUi.InProgress), sent::add)

        rule.onNodeWithTag("issue-interrupt").performClick()
        rule.onNodeWithTag("issue-comment-field").performTextInput("Stop and re-plan")
        rule.onNodeWithTag("issue-comment-send").performClick()

        sent shouldContain IssueIntent.CommentSubmitted("Stop and re-plan", reopen = false, interrupt = true)
    }

    @Test
    fun aClosedIssueOffersReopenAndNotInterrupt() {
        val sent = mutableListOf<IssueIntent>()
        show(content(status = IssueStatusUi.Done), sent::add)

        rule.hasTag("issue-interrupt") shouldBe false
        rule.onNodeWithTag("issue-reopen").performClick()
        rule.onNodeWithTag("issue-comment-field").performTextInput("One more thing")
        rule.onNodeWithTag("issue-comment-send").performClick()

        sent shouldContain IssueIntent.CommentSubmitted("One more thing", reopen = true, interrupt = false)
    }

    @Test
    fun aFailedFirstReadShowsTheErrorWithTryAgainAndNoBar() {
        val sent = mutableListOf<IssueIntent>()
        show(IssueState(phase = DetailPhase.Failed, error = ErrorUi(ErrorKey.Unreachable)), sent::add)

        rule.hasTag("issue-comment-bar") shouldBe false
        rule.onNodeWithText("Try again").performClick()

        sent shouldBe listOf(IssueIntent.Refresh)
    }

    @Test
    fun aTabThatFailedToLoadSaysSoAndOffersTryAgain() {
        val sent = mutableListOf<IssueIntent>()
        show(
            content().copy(tab = IssueTab.Runs, tabError = ErrorUi(ErrorKey.Timeout)),
            sent::add,
        )

        rule.onNodeWithText("Try again").performClick()

        sent shouldBe listOf(IssueIntent.TabSelected(IssueTab.Runs))
    }

    @Test
    fun requestsThatCouldNotBeLoadedAreMentionedInTheThread() {
        show(content().copy(interactionsUnavailable = true))

        rule.hasTag("issue-interactions-unavailable") shouldBe true
        rule.onNodeWithTag("issue-comment-field").performTextInput("still works")
        rule.onNodeWithTag("issue-comment-send").assertIsEnabled()
    }

    @Test
    fun switchingTabsIsForwardedAsAnIntent() {
        val sent = mutableListOf<IssueIntent>()
        show(content(), sent::add)

        rule.onNodeWithTag("issue-tab-Plan").performClick()

        sent shouldBe listOf(IssueIntent.TabSelected(IssueTab.Plan))
    }
}
