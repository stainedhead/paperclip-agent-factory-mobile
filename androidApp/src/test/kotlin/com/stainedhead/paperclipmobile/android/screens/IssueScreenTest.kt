package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val COMMENT_AGENT = "c-1204-1"
private const val COMMENT_USER = "c-1204-2"
private const val SUBTASK_ONE = "pap-1183"

/** AND-061: PAP-1204 and PAP-1182 on the demo graph; header, tabs, comment, status, assignee and subtasks. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class IssueScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(id: String = DemoId.ISSUE_RETRIES) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { IssueScreen(app.session, AppRoute.Issue(id), app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("issue-title") }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun fieldText(tag: String) =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text

    /** A control outside the page scroll (the comment bar) or in a popup menu. */
    private fun press(tag: String) {
        rule.onNodeWithTag(tag).performClick()
    }

    private fun openTab(tab: String) {
        tap("issue-tab-$tab")
    }

    @Test
    fun theHeaderNamesTheIssueItsProjectStatusPriorityAndAssignee() {
        show()

        rule.onNodeWithTag("issue-identifier").assertTextEquals("PAP-1204 · Billing v2")
        rule.onNodeWithTag("issue-title").assertTextEquals("Add retries to webhook ingest")
        rule.onNodeWithTag("issue-status").assertTextEquals("Blocked")
        rule.onNodeWithTag("issue-priority").assertTextEquals("High")
        rule.onNodeWithTag("issue-assignee-menu").assertTextEquals("Engineer 1")
        rule.onNodeWithTag("updated-label").assertTextEquals("Updated 0s ago")
    }

    @Test
    fun theThreadShowsTheAgentsAndTheOwnersCommentsInTimeOrderWithWhoAndWhen() {
        show()

        rule.isSelected("issue-tab-Thread") shouldBe true
        rule.texts("issue-comment-$COMMENT_AGENT") shouldBe
            listOf(
                "Engineer 1 · agent · 5h",
                "Ingest drops events when the receiver answers 503. I propose retrying with backoff.",
            )
        rule.texts("issue-comment-$COMMENT_USER") shouldBe
            listOf("You · 4h", "Sounds right. Tell me which policy you want.")
    }

    @Test
    fun thePlanTabShowsTheReadOnlyPlan() {
        show()

        openTab("Plan")

        rule.isSelected("issue-tab-Plan") shouldBe true
        rule.onNodeWithTag("issue-plan-title").assertTextEquals("Retry plan")
        rule
            .onNodeWithTag(
                "issue-plan-body",
            ).assertTextEquals("1. Add a retry queue\n2. Back off exponentially\n3. Dead-letter")
    }

    @Test
    fun theRunsTabListsTheIssuesRunsAndOpensOne() {
        show()

        openTab("Runs")

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("issue-run-${DemoId.RUN_LIVE}") }
        rule.hasTag("issue-run-${DemoId.RUN_FAILED}") shouldBe true
        rule.hasTag("issue-run-${DemoId.RUN_DONE}") shouldBe true
        rule.onNodeWithTag("issue-tab-Runs").assertTextEquals("Runs 3")
        tap("issue-run-${DemoId.RUN_FAILED}")
        app.pushed shouldBe listOf(AppRoute.Run(DemoId.RUN_FAILED))
    }

    @Test
    fun theFilesTabListsEachFileWithItsTypeAndSizeAndSaysWhyItCannotOpen() {
        show()

        openTab("Files")

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("issue-file-att-1") }
        rule.texts("issue-file-att-1").first() shouldBe "retry-policy.md"
        rule.texts("issue-file-att-2").first() shouldBe "ingest-errors.log"
        rule.onNodeWithTag("issue-tab-Files").assertTextEquals("Files 2")
        rule.onNodeWithTag("issue-files-note").assertIsDisplayed()
    }

    @Test
    fun anIssueWithoutRunsOrFilesSaysSo() {
        show("pap-1186")

        openTab("Runs")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("issue-runs-empty") }
        openTab("Files")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("issue-files-empty") }
    }

    @Test
    fun theSubtasksOfBillingV2ListAndOpenAndASubtaskLinksBackToItsParent() {
        show(DemoId.ISSUE_BILLING)

        rule.hasTag("issue-subtask-$SUBTASK_ONE") shouldBe true
        rule.hasTag("issue-subtask-pap-1186") shouldBe true
        tap("issue-subtask-$SUBTASK_ONE")
        app.pushed shouldBe listOf(AppRoute.Issue(SUBTASK_ONE))
    }

    @Test
    fun aSubtaskNamesItsParentAsALink() {
        show(SUBTASK_ONE)

        rule.onNodeWithTag("issue-parent").assertTextEquals("Part of: PAP-1182 · Billing v2")
        tap("issue-parent")
        app.pushed shouldBe listOf(AppRoute.Issue(DemoId.ISSUE_BILLING))
    }

    @Test
    fun aCommentIsSentOnceTheBoxClearsAndTheThreadShowsIt() {
        show()

        rule.onNodeWithTag("issue-comment-field").performTextInput("Use exponential backoff")
        rule.onNodeWithTag("issue-comment-send").performClick()

        rule.waitUntil(WAIT_MILLIS) { fieldText("issue-comment-field") == "" }
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Use exponential backoff") }
        rule.onNodeWithTag("issue-comment-send").assertIsEnabled()
    }

    @Test
    fun aBlankCommentIsNotSentAndStaysInTheBox() {
        show()

        rule.onNodeWithTag("issue-comment-field").performTextInput("   ")

        rule.onNodeWithTag("issue-comment-send").assertIsNotEnabled()
        fieldText("issue-comment-field") shouldBe "   "
    }

    @Test
    fun anInProgressIssueOffersToInterruptTheAgentAndAClosedOneToReopen() {
        show(DemoId.ISSUE_BILLING)

        rule.hasTag("issue-interrupt") shouldBe true
        rule.hasTag("issue-reopen") shouldBe false
        press("issue-status-menu")
        press("issue-status-Done")
        rule.waitUntil(WAIT_MILLIS) { rule.texts("issue-status") == listOf("Done") }

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("issue-reopen") }
        rule.hasTag("issue-interrupt") shouldBe false
    }

    @Test
    fun theStatusMenuOffersEveryStatusButTheCurrentOneAndChangesTheHeader() {
        show()

        press("issue-status-menu")
        rule.hasTag("issue-status-Blocked") shouldBe false
        rule.hasTag("issue-status-InReview") shouldBe true
        press("issue-status-InReview")

        rule.waitUntil(WAIT_MILLIS) { rule.texts("issue-status") == listOf("In review") }
    }

    @Test
    fun anOwnerReassignsTheIssueToAnotherAgent() {
        show()

        tap("issue-assignee-menu")
        press("issue-assignee-${DemoId.AGENT_ENGINEER_2}")

        rule.waitUntil(WAIT_MILLIS) { rule.texts("issue-assignee-menu") == listOf("Engineer 2") }
    }

    @Test
    fun aSubtaskIsAddedFromTheSheetAndTheSheetClosesWhenItExists() {
        show(DemoId.ISSUE_BILLING)

        tap("issue-add-subtask")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("issue-subtask-title") }
        rule.onNodeWithTag("issue-subtask-title").performTextInput("Announce the switch")
        rule.onNodeWithTag("issue-subtask-save").performClick()

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("issue-subtask-form") }
        rule.waitUntil(WAIT_MILLIS) {
            rule
                .onAllNodesWithContentDescription(
                    "Announce the switch",
                    substring = true,
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun offlineKeepsTheIssueDisablesTheBarAndSaysWhy() {
        show()
        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.hasTag("disabled-reason") shouldBe true
        rule.onNodeWithTag("issue-title").assertIsDisplayed()
    }
}
