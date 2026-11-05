package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.designsystem.PaperclipTheme
import com.stainedhead.paperclipmobile.presentation.ApprovalActionUi
import com.stainedhead.paperclipmobile.presentation.ApprovalState
import com.stainedhead.paperclipmobile.presentation.BudgetChoice
import com.stainedhead.paperclipmobile.presentation.BudgetIncidentState
import com.stainedhead.paperclipmobile.presentation.BudgetIntent
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.FeedbackKind
import com.stainedhead.paperclipmobile.presentation.FeedbackUi
import com.stainedhead.paperclipmobile.presentation.MoneyUi
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val TYPED = "a half written thought"
private const val AMOUNT = "250"

/** R-003: what the person typed survives a state change that removes the box and a recreation of the activity. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class TypedTextTest {
    @get:Rule
    val rule = createComposeRule()

    private val open =
        ApprovalState(
            phase = DetailPhase.Content,
            actions = listOf(ApprovalActionUi.Comment, ApprovalActionUi.Approve),
        )

    @Test
    fun aFailedCommentPostLeavesTheTypedTextInTheBox() {
        var state by mutableStateOf(open)
        rule.setContent { PaperclipTheme(darkTheme = false) { ApprovalContent(state, {}, {}, {}) } }
        rule.onNodeWithTag("approval-comment-field").performScrollTo().performTextInput(TYPED)

        state = open.copy(actions = emptyList(), isSubmitting = true)
        rule.waitForIdle()
        state = open.copy(feedback = FeedbackUi(FeedbackKind.Failed))
        rule.waitForIdle()

        rule.onNodeWithTag("approval-comment-field").performScrollTo().assertTextContains(TYPED)
    }

    @Test
    fun theCommentBoxClearsWhenTheCountMovesButNotOnFirstComposition() {
        var state by mutableStateOf(open.copy(commentPostedCount = 3))
        rule.setContent { PaperclipTheme(darkTheme = false) { ApprovalContent(state, {}, {}, {}) } }
        rule.onNodeWithTag("approval-comment-field").performScrollTo().performTextInput(TYPED)
        rule.waitForIdle()
        rule.onNodeWithTag("approval-comment-field").performScrollTo().assertTextContains(TYPED)

        state = state.copy(commentPostedCount = 4)
        rule.waitForIdle()

        // The box is empty again, so the post button is disabled for blank text.
        rule.onNodeWithTag("approval-comment-post").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theCommentSurvivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { PaperclipTheme(darkTheme = false) { ApprovalContent(open, {}, {}, {}) } }
        rule.onNodeWithTag("approval-comment-field").performScrollTo().performTextInput(TYPED)

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("approval-comment-field").performScrollTo().assertTextContains(TYPED)
    }

    @Test
    fun theRejectNoteSurvivesRecreation() {
        val text = NoteSheetText("Reject", "Note", "Why?", "Say why", "Reject")
        val tester = StateRestorationTester(rule)
        tester.setContent {
            PaperclipTheme(darkTheme = false) { NoteContent(text, {}, {}, true, false, null, null) }
        }
        rule.onNodeWithTag("note-field").performTextInput(TYPED)

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("note-field").assertTextContains(TYPED)
    }

    @Test
    fun aNoteKeepsItsNewlinesInTheIntent() {
        var sent = ""
        val text = NoteSheetText("Reject", "Note", "Why?", "Say why", "Reject")
        rule.setContent {
            PaperclipTheme(darkTheme = false) { NoteContent(text, { sent = it }, {}, false, false, null, null) }
        }
        rule.onNodeWithTag("note-field").performTextInput("a\nb")

        rule.onNodeWithTag("note-confirm").performClick()

        sent shouldBe "a\nb"
    }

    @Test
    fun theBudgetAmountSurvivesRecreationAndIsSentAgain() {
        val sent = mutableListOf<BudgetIntent>()
        val state =
            BudgetIncidentState(
                phase = DetailPhase.Content,
                observed = MoneyUi(20_000, "USD"),
                choice = BudgetChoice.RaiseAndResume,
                canKeepPaused = true,
                canRaise = true,
            )
        val tester = StateRestorationTester(rule)
        tester.setContent { PaperclipTheme(darkTheme = false) { BudgetBody(state) { sent += it } } }
        rule.onNodeWithTag("budget-amount").performTextInput(AMOUNT)
        sent.clear()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("budget-amount").assertTextContains(AMOUNT)
        // A new holder after a process death has no amount: the restored text is sent once so both agree.
        rule.waitForIdle()
        (sent.lastOrNull() as? BudgetIntent.AmountTyped)?.text shouldBe AMOUNT
    }
}
