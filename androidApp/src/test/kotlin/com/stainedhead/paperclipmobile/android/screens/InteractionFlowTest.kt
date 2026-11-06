package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.presentation.InboxIntent
import com.stainedhead.paperclipmobile.presentation.InboxState
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val CARD = "int-1204-questions"

/**
 * Main PRD acceptance criterion 7, as far as the Android app can prove it on the demo graph: the agent's two questions
 * on PAP-1204 are answered on the phone with live validation, the card reads Answered, and the answer reaches the Inbox
 * feed (the demo `onAnswered` hook). That the answer appears in the web app's thread needs a real server (T8.9).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class InteractionFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()

    @After
    fun tearDown() = app.close()

    private fun tag(part: String) = "interaction-$CARD-$part"

    private fun show() {
        rule.waitUntil(WAIT_MILLIS) { app.appState.selectedCompanyId != null }
        rule.setContent {
            app.Themed { IssueScreen(app.session, AppRoute.Issue(DemoId.ISSUE_RETRIES), app.navigator) }
        }
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag(tag("kind")) }
    }

    private fun choose(part: String) {
        rule.onNodeWithTag(tag(part)).performScrollTo().performClick()
    }

    private val inboxState: InboxState get() = checkNotNull(app.session.inbox.value).state.value

    private fun inboxHasTheQuestion() =
        inboxState.sections
            .flatMap { it.items }
            .any { it.kind == SourceKindUi.IssueThreadInteraction && it.identifier == "PAP-1204" }

    @Test
    fun theAgentsTwoQuestionsAreDrawnWithTheirOptionsAndSendIsOffUntilBothAreAnswered() {
        show()

        rule.onNodeWithTag(tag("kind")).assertTextEquals("QUESTIONS FOR YOU · 2 QUESTIONS")
        rule
            .onNodeWithTag(
                tag("question-q-policy-prompt"),
            ).assertTextEquals("Which retry policy should webhook ingest use?")
        rule
            .onNodeWithTag(
                tag("question-q-attempts-prompt"),
            ).assertTextEquals("How many attempts before dead-lettering?")
        rule.onNodeWithTag(tag("primary")).assertTextEquals("Send answers")
        rule.onNodeWithTag(tag("primary")).assertIsNotEnabled()
        rule.onNodeWithTag(tag("submit-issue")).assertTextEquals("Answer every required question.")
    }

    @Test
    fun answeringValidatesLiveOneQuestionAtATimeAndAChoiceReplacesTheOneBeforeIt() {
        show()

        choose("choice-q-policy-exponential")
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected(tag("choice-q-policy-exponential")) }
        rule.onNodeWithTag(tag("primary")).assertIsNotEnabled()

        choose("choice-q-policy-fixed")
        rule.waitUntil(WAIT_MILLIS) { rule.isSelected(tag("choice-q-policy-fixed")) }
        rule.isSelected(tag("choice-q-policy-exponential")) shouldBe false
        rule.onNodeWithTag(tag("primary")).assertIsNotEnabled()

        choose("choice-q-attempts-5")
        rule.waitUntil(WAIT_MILLIS) { rule.hasTag(tag("primary")) && !rule.hasTag(tag("submit-issue")) }
        rule.onNodeWithTag(tag("primary")).assertIsEnabled()
    }

    @Test
    fun aTypedAnswerInPlaceOfAnOptionCompletesTheSecondQuestionAndKeepsEveryCharacter() {
        show()

        choose("choice-q-policy-exponential")
        rule.onNodeWithTag(tag("text-q-attempts")).performScrollTo().performTextInput("12")
        rule.onNodeWithTag(tag("text-q-attempts")).performTextInput("0")

        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag(tag("submit-issue")) }
        rule.onNodeWithTag(tag("primary")).assertIsEnabled()
        rule.onNodeWithTag(tag("text-q-attempts")).assertTextEquals("120", includeEditableText = true)
    }

    @Test
    fun sendingTheAnswersMarksTheCardAnsweredAndTheQuestionLeavesTheInboxFeed() {
        show()
        rule.waitUntil(WAIT_MILLIS) { inboxHasTheQuestion() }
        val before = inboxState.totalCount

        choose("choice-q-policy-exponential")
        choose("choice-q-attempts-3")
        rule.waitUntil(WAIT_MILLIS) { !rule.hasTag(tag("submit-issue")) }
        rule.onNodeWithTag(tag("primary")).performScrollTo().performClick()

        rule.waitUntil(WAIT_MILLIS) { rule.hasTag(tag("kind")) && rule.texts(tag("kind")).first().endsWith("ANSWERED") }
        rule.hasTag(tag("primary")) shouldBe false
        checkNotNull(app.session.inbox.value).send(InboxIntent.Refresh)
        rule.waitUntil(WAIT_MILLIS) { !inboxHasTheQuestion() }
        inboxState.totalCount shouldBe before - 1
    }
}
