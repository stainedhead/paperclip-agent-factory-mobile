package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AND-041: the QA hire approval and the Q1 strategy on the demo graph; payload, actions, note, device owner. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class ApprovalScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun show(
        id: String = DemoId.APPROVAL_HIRE,
        ready: String = "approval-row-name",
    ) {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent { app.Themed { ApprovalScreen(app.session, AppRoute.Approval(id), app.navigator) } }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag(ready) }
    }

    @Test
    fun aHireShowsItsPayloadRowsThenTheLinkedWorkAndTheDiscussion() {
        show()

        rule.onNodeWithTag("approval-kind-line").assertTextEquals("APPROVAL · HIRE AGENT")
        rule.onNodeWithTag("approval-title").assertTextEquals("Hire QA engineer")
        rule.onNodeWithTag("approval-requested-by").assertTextEquals("Requested by CEO")
        rule.onNodeWithText("Pending").assertIsDisplayed()
        rule.onNodeWithText("QA engineer").assertIsDisplayed()
        rule.onNodeWithText("QA", substring = false).assertIsDisplayed()
        rule.onNodeWithText("claude_local").assertIsDisplayed()
        rule.onNodeWithText("$150").assertIsDisplayed()
        rule.onNodeWithText("regression-testing").assertIsDisplayed()
        rule.hasTag("approval-row-capabilities") shouldBe false
        rule.onNodeWithText("PAP-1190 Refund path regression").assertIsDisplayed()
        rule.onNodeWithText("Todo").assertIsDisplayed()
        rule.onNodeWithTag("approval-comment-c-hire-1").assertIsDisplayed()
        rule.onNodeWithText("Agent").assertIsDisplayed()
    }

    @Test
    fun aStrategyShowsItsPlanAsALongTextBlock() {
        show(DemoId.APPROVAL_STRATEGY, ready = "approval-block-plan")

        rule.onNodeWithTag("approval-title").assertTextEquals("Q1 strategy")
        rule.onNodeWithText("Focus on billing v2 and webhook reliability.").assertIsDisplayed()
        rule.onNodeWithTag("approval-kind-line").assertTextEquals("APPROVAL · CEO STRATEGY")
    }

    @Test
    fun aLinkedIssueOpensItsIssue() {
        show()

        rule.onNodeWithTag("approval-issue-${DemoId.ISSUE_REFUNDS}").performScrollTo().performClick()

        app.pushed shouldBe listOf(AppRoute.Issue(DemoId.ISSUE_REFUNDS))
    }

    @Test
    fun approvingResolvesItWithoutAskingTheDeviceOwner() {
        show()

        rule.onNodeWithTag("approval-approve").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("approval-final") }
        rule.onNodeWithText("This approval was approved. Nothing more to do.").assertIsDisplayed()
        rule.hasTag("approval-approve") shouldBe false
        rule.hasTag("approval-reject") shouldBe false
        app.controls.biometricPromptCount shouldBe 0
    }

    @Test
    fun rejectingNeedsANoteThenTheDeviceOwnerAndStaysOpenIfTheyCancel() {
        show()
        app.scriptBiometrics(BiometricResult.Cancelled)

        rule.onNodeWithTag("approval-reject").performClick()
        rule.awaitDisplayed("note-sheet")
        rule.onNodeWithTag("note-lock", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("note-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("note-field-issue") }
        rule.onNodeWithTag("note-field-issue", useUnmergedTree = true).assertTextEquals("Add a note first.")
        app.controls.biometricPromptCount shouldBe 0

        rule.onNodeWithTag("note-field").performTextInput("Too expensive")
        rule.awaitFieldText("note-field", "Too expensive")
        rule.onNodeWithTag("note-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("note-issue") }
        app.controls.biometricPromptCount shouldBe 1
        rule.hasTag("approval-final") shouldBe false

        rule.onNodeWithTag("note-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("approval-final") }
        app.controls.biometricPromptCount shouldBe 2
        rule.onNodeWithText("This approval was rejected. Nothing more to do.").assertIsDisplayed()
    }

    @Test
    fun requestingChangesNeedsANoteButNoDeviceOwner() {
        show()

        rule.onNodeWithTag("approval-request-revision").performClick()
        rule.awaitDisplayed("note-sheet")
        rule.hasTag("note-lock") shouldBe false
        rule.onNodeWithTag("note-field").performTextInput("Lower the budget")
        rule.awaitFieldText("note-field", "Lower the budget")
        rule.onNodeWithTag("note-confirm").performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Revision requested") }
        app.controls.biometricPromptCount shouldBe 0
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("note-sheet") }
    }

    @Test
    fun aCommentIsPostedToTheDiscussionAndTheBoxClears() {
        show()

        rule.onNodeWithTag("approval-comment-field").performScrollTo().performTextInput("Please add a test plan")
        rule.awaitFieldText("approval-comment-field", "Please add a test plan")
        rule.awaitEnabled("approval-comment-post")
        rule.onNodeWithTag("approval-comment-post").performScrollTo().performClick()

        rule.awaitFieldText("approval-comment-field", "")
        rule.waitUntil(WAIT_MILLIS) { rule.hasText("Please add a test plan") }
        rule.onAllNodesWithText("Please add a test plan").assertCountEquals(1)
    }

    @Test
    fun aHalfWrittenCommentSurvivesGoingOfflineAndBack() {
        show()
        rule.onNodeWithTag("approval-comment-field").performScrollTo().performTextInput("half written")

        app.controls.setOnline(false)
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        app.controls.setOnline(true)
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag("offline-banner") && rule.hasTag("approval-comment-field") }

        rule.onNodeWithTag("approval-comment-field").performScrollTo().assertTextContains("half written")
    }

    @Test
    fun anApprovalResolvedElsewhereShowsItsFinalStateAndNoActions() {
        app.controls.resolveApprovalElsewhere(DemoId.APPROVAL_HIRE)

        show(ready = "approval-final")

        rule.onNodeWithText("This approval was approved. Nothing more to do.").assertIsDisplayed()
        rule.hasTag("approval-approve") shouldBe false
        rule.hasTag("approval-request-revision") shouldBe false
    }

    @Test
    fun offlineTheActionsGoWithTheirReasonAndThePayloadStays() {
        show()

        app.controls.setOnline(false)

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag("offline-banner") }
        rule.onNodeWithText("You're offline. Actions are unavailable.").assertIsDisplayed()
        rule.hasTag("approval-approve") shouldBe false
        rule.hasTag("approval-reject") shouldBe false
        rule.onNodeWithTag("approval-row-name").assertIsDisplayed()
    }

    @Test
    fun anApprovalThatCannotBeReadSaysSoWithAWayToTryAgain() {
        show("missing", ready = "detail-failed")

        rule.hasTag("error-banner") shouldBe true
        rule.hasTag("approval-approve") shouldBe false
    }
}
