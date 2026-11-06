package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.ErrorUi
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.FeedbackUi
import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueRowUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.WorkListIntent
import com.stainedhead.paperclipmobile.presentation.WorkListState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val NOW = 1_800_000_000_000L

private fun issue(
    id: String,
    assignedToMe: Boolean = false,
) = IssueRowUi(
    id,
    id.uppercase(),
    "Title of $id",
    IssueStatusUi.InProgress,
    IssuePriorityUi.Critical,
    if (assignedToMe) null else "Engineer 2",
    assignedToMe,
    NOW,
)

private fun content(
    role: RoleUi = RoleUi.Owner,
    rows: List<IssueRowUi> = listOf(issue("pap-1"), issue("pap-2", assignedToMe = true)),
) = WorkListState(
    phase = DetailPhase.Content,
    rows = rows,
    canCreate = role != RoleUi.Viewer,
    role = role,
    updatedAtEpochMillis = NOW,
)

/** AND-060 variants the demo graph cannot produce: a viewer, a failed read, paging, and a create that fails. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class WorkListVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(
        state: WorkListState,
        send: (WorkListIntent) -> Unit = {},
        onOpen: (String) -> Unit = {},
    ) = rule.setContent { app.Themed { WorkListContent(state, send, onOpen, {}) } }

    @Test
    fun aViewerCanReadTheListButHasNoNewIssueAndIsToldWhy() {
        show(content(RoleUi.Viewer).copy(actionsDisabledReason = ActionsDisabledReason.ViewerRole))

        rule.hasTag("work-create") shouldBe false
        rule.hasTag("disabled-reason") shouldBe true
        rule.hasTag("work-row-pap-1") shouldBe true
    }

    @Test
    fun anIssueAssignedToMeSaysYouAndOneAssignedToAnAgentNamesIt() {
        show(content())

        rule.onNodeWithTag("work-row-pap-1").assertContentDescriptionEquals(
            "PAP-1. Title of pap-1. In progress · Critical · Engineer 2",
        )
        rule.onNodeWithTag("work-row-pap-2").assertContentDescriptionEquals(
            "PAP-2. Title of pap-2. In progress · Critical · You",
        )
    }

    @Test
    fun aFailedFirstReadShowsTheErrorWithTryAgainAndNoList() {
        val sent = mutableListOf<WorkListIntent>()
        show(
            WorkListState(phase = DetailPhase.Failed, error = ErrorUi(ErrorKey.Unreachable)),
            sent::add,
        )

        rule.hasTag("work-row-pap-1") shouldBe false
        rule.onNodeWithText("Try again").performClick()

        sent shouldBe listOf(WorkListIntent.Refresh)
    }

    @Test
    fun aSearchTypedButNotSentIsAppliedWhenAChipIsChosen() {
        val sent = mutableListOf<WorkListIntent>()
        show(content(), sent::add)
        rule.onNodeWithTag("work-search").performTextInput("retries")

        rule.onNodeWithTag("work-filter-Blocked").performScrollTo().performClick()

        sent shouldBe
            listOf(WorkListIntent.SearchChanged("retries"), WorkListIntent.FilterSelected(IssueFilterUi.Blocked))
    }

    @Test
    fun reachingTheEndOfAPageAsksForTheNextOne() {
        val sent = mutableListOf<WorkListIntent>()
        show(content().copy(hasMore = true), sent::add)

        rule.onNodeWithTag("work-loading-more").performScrollTo().assertIsDisplayed()
        rule.waitUntil(WAIT_MILLIS) { WorkListIntent.LoadMore in sent }
    }

    @Test
    fun aFailedCreateKeepsTheTitleAndThePriorityInTheSheet() {
        show(
            content().copy(
                feedback = FeedbackUi(FeedbackKind.Failed, error = ErrorUi(ErrorKey.ServerError)),
            ),
        )

        rule.onNodeWithTag("work-create").performScrollTo().performClick()
        rule.waitUntil(
            WAIT_MILLIS,
        ) { runCatching { rule.onNodeWithTag("work-create-form").assertIsDisplayed() }.isSuccess }
        rule.onNodeWithTag("work-create-title").performTextInput("Rotate the secret")
        rule.onNodeWithTag("work-create-priority-Critical").performClick()
        rule.onNodeWithTag("work-create-save").performClick()

        rule.onNodeWithTag("work-create-title").assertTextContains("Rotate the secret")
        rule.isSelected("work-create-priority-Critical") shouldBe true
        rule.onNodeWithTag("feedback-message").assertTextContains("The factory had a problem.", substring = true)
    }

    @Test
    fun theCountReadsOneIssueInTheSingular() {
        show(content(rows = listOf(issue("pap-1"))).copy(filter = IssueFilterUi.Blocked))

        rule.onNodeWithTag("work-count").assertTextEquals("1 issue")
        rule.isSelected("work-filter-Blocked") shouldBe true
    }
}
