package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.presentation.InteractionKindUi
import com.stainedhead.paperclipmobile.presentation.InteractionPrimaryUi
import com.stainedhead.paperclipmobile.presentation.InteractionReadOnlyUi
import com.stainedhead.paperclipmobile.presentation.InteractionStatusUi
import com.stainedhead.paperclipmobile.presentation.InteractionUi
import com.stainedhead.paperclipmobile.presentation.InvalidKey
import com.stainedhead.paperclipmobile.presentation.IssueIntent
import com.stainedhead.paperclipmobile.presentation.QuestionUi
import com.stainedhead.paperclipmobile.presentation.VerdictItemUi
import com.stainedhead.paperclipmobile.presentation.VerdictUi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val ID = "int-1"
private const val URL = "https://paperclip.example.ts.net/issues/PAP-9"

/** AND-061 / FR-063: the five kinds as native forms, and the kind the app does not answer, drawn from built cards. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class InteractionCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val app = DemoApp()
    private val sent = mutableListOf<IssueIntent>()
    private val launcher = RecordingLauncher()

    @After
    fun tearDown() = app.close()

    private fun show(
        card: InteractionUi,
        isBusy: Boolean = false,
    ) = rule.setContent {
        app.Themed {
            CompositionLocalProvider(LocalWebOpener provides WebOpener(launcher)) {
                InteractionCard(card, isBusy, sent::add)
            }
        }
    }

    private fun tag(part: String) = "interaction-$ID-$part"

    private fun fieldText(part: String) =
        rule
            .onNodeWithTag(tag(part))
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text

    @Test
    fun suggestedTasksAreAllChosenToStartWithAndEachToggleIsForwarded() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.SuggestTasks,
                    tasks =
                        listOf(
                            choice("t1", "Add a retry queue", selected = true),
                            choice("t2", "Dead-letter", selected = true),
                        ),
                    hasReject = true,
                ),
            ),
        )

        rule.onNodeWithTag(tag("kind")).assertTextEquals("SUGGESTED TASKS")
        rule.isSelected(tag("choice--t1")) shouldBe true
        rule.onNodeWithTag(tag("choice--t2")).performClick()
        rule.onNodeWithTag(tag("primary")).assertTextEquals("Accept")

        sent shouldContain IssueIntent.InteractionChoiceToggled(ID, "", "t2")
    }

    @Test
    fun aSuggestedTaskSelectionThatIsEmptyDisablesAcceptAndSaysWhy() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.SuggestTasks,
                    tasks = listOf(choice("t1", "Add a retry queue")),
                    hasReject = true,
                    submitIssue = InvalidKey.AnswerNothingSelected,
                ),
            ),
        )

        rule.onNodeWithTag(tag("primary")).assertIsNotEnabled()
        rule.onNodeWithTag(tag("submit-issue")).assertTextEquals("Choose an answer.")
    }

    @Test
    fun aConfirmationShowsItsPromptAndDetailsAcceptsAndRejectsWithAReason() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.RequestConfirmation,
                    prompt = "Switch traffic to the new billing service?",
                    details = "Cutover takes about a minute.",
                    hasReject = true,
                    rejectRequiresReason = true,
                    rejectIssue = InvalidKey.AnswerReasonRequired,
                ),
            ),
        )

        rule.onNodeWithTag(tag("prompt")).assertTextEquals("Switch traffic to the new billing service?")
        rule.onNodeWithTag(tag("details")).assertTextEquals("Cutover takes about a minute.")
        rule.onNodeWithTag(tag("reject")).assertIsNotEnabled()
        rule.onNodeWithTag(tag("reject-issue")).assertTextEquals("Add a reason.")
        rule.onNodeWithTag(tag("reject-reason")).performTextInput("Not before the audit")
        rule.onNodeWithTag(tag("primary")).performClick()

        sent shouldContain IssueIntent.InteractionTextChanged(ID, "", "Not before the audit")
        sent shouldContain IssueIntent.InteractionPrimaryTapped(ID)
    }

    @Test
    fun aRejectThatNeedsNoReasonIsSentAsATap() {
        show(interactionCard(CardSpec(prompt = "Proceed?", hasReject = true)))

        rule.onNodeWithTag(tag("reject")).performClick()

        sent shouldContain IssueIntent.InteractionRejectTapped(ID)
    }

    @Test
    fun aCheckboxConfirmationShowsItsOptionsWithTheirDefaultsAndForwardsToggles() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.RequestCheckboxConfirmation,
                    prompt = "Which environments?",
                    options = listOf(choice("staging", "Staging", selected = true), choice("prod", "Production")),
                    hasReject = true,
                ),
            ),
        )

        rule.isSelected(tag("choice--staging")) shouldBe true
        rule.isSelected(tag("choice--prod")) shouldBe false
        rule.onNodeWithTag(tag("choice--prod")).performClick()

        sent shouldContain IssueIntent.InteractionChoiceToggled(ID, "", "prod")
    }

    @Test
    fun questionsAreSingleOrMultipleChoiceOptionalOrNotWithAFieldForSomethingElse() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.AskUserQuestions,
                    primary = InteractionPrimaryUi.Respond,
                    questions =
                        listOf(
                            QuestionUi(
                                "q1",
                                "Which policy?",
                                "Applies to all webhooks",
                                multi = false,
                                required = true,
                                allowOther = false,
                                options = listOf(choice("a", "Exponential"), choice("b", "Fixed", selected = true)),
                                otherText = "",
                            ),
                            QuestionUi(
                                "q2",
                                "Anything else?",
                                null,
                                multi = true,
                                required = false,
                                allowOther = true,
                                options = listOf(choice("x", "Alert on-call")),
                                otherText = "keep logs",
                            ),
                        ),
                ),
            ),
        )

        rule.onNodeWithTag(tag("question-q1-prompt")).assertTextEquals("Which policy?")
        rule.onNodeWithTag(tag("question-q1-help")).assertTextEquals("Applies to all webhooks")
        rule.onNodeWithTag(tag("question-q2-prompt")).assertTextEquals("Anything else? (optional)")
        rule.isSelected(tag("choice-q1-b")) shouldBe true
        rule.onNodeWithTag(tag("choice-q1-a")).performClick()
        fieldText("text-q2") shouldBe "keep logs"
        rule.onNodeWithTag(tag("text-q2")).performTextReplacement("keep logs!")

        sent shouldContain IssueIntent.InteractionChoiceToggled(ID, "q1", "a")
        sent shouldContain IssueIntent.InteractionTextChanged(ID, "q2", "keep logs!")
    }

    @Test
    fun aQuestionWithoutOptionsIsAFreeTextAnswer() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.AskUserQuestions,
                    primary = InteractionPrimaryUi.Respond,
                    questions =
                        listOf(QuestionUi("q1", "Who owns this?", null, false, true, true, emptyList(), "")),
                    submitIssue = InvalidKey.AnswerQuestionRequired,
                ),
            ),
        )

        rule.onNodeWithTag(tag("primary")).assertTextEquals("Send answers")
        rule.onNodeWithTag(tag("primary")).assertIsNotEnabled()
        rule.onNodeWithTag(tag("submit-issue")).assertTextEquals("Answer every required question.")
        rule.onNodeWithTag(tag("text-q1")).performTextInput("Platform team")

        sent shouldContain IssueIntent.InteractionTextChanged(ID, "q1", "Platform team")
    }

    @Test
    fun verdictsAreChosenPerItemWithAReasonAndOnlyTheEnabledOnesCanBeTapped() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.RequestItemVerdicts,
                    primary = InteractionPrimaryUi.SubmitVerdicts,
                    prompt = "Review the changes",
                    items =
                        listOf(
                            VerdictItemUi("i1", "Rename the table", "Breaking", VerdictUi.Approve, ""),
                            VerdictItemUi("i2", "Drop the index", null, null, "slow"),
                        ),
                    enabledVerdicts = listOf(VerdictUi.Approve, VerdictUi.Reject),
                    submitIssue = InvalidKey.AnswerNothingSelected,
                ),
            ),
        )

        rule.isSelected(tag("verdict-i1-Approve")) shouldBe true
        rule.onNodeWithTag(tag("verdict-i2-Defer")).assertIsNotEnabled()
        rule.onNodeWithTag(tag("verdict-i2-Reject")).assertIsEnabled()
        rule.onNodeWithTag(tag("verdict-i2-Reject")).performClick()
        rule.onNodeWithTag(tag("reason-i1")).performTextInput("ok")
        fieldText("reason-i2") shouldBe "slow"

        sent shouldContain IssueIntent.InteractionVerdictChosen(ID, "i2", VerdictUi.Reject)
        sent shouldContain IssueIntent.InteractionTextChanged(ID, "i1", "ok")
        rule.onNodeWithTag(tag("primary")).assertIsNotEnabled()
    }

    @Test
    fun aKindTheAppDoesNotAnswerOffersTheWebAppAndOpensTheInstanceAddress() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.Unsupported,
                    readOnly = InteractionReadOnlyUi.Unsupported,
                    primary = null,
                    openInWebUrl = URL,
                ),
            ),
        )

        rule.hasTag(tag("primary")) shouldBe false
        rule.onNodeWithTag(tag("read-only")).assertTextEquals(
            "This kind of request can't be answered in the app. Open it in the web app.",
        )
        rule.onNodeWithTag(tag("open-web")).performClick()

        launcher.tabbed shouldBe listOf(URL)
        rule.hasTag(tag("open-web-failed")) shouldBe false
    }

    @Test
    fun anAddressThatIsNotHttpsIsNeverOpenedAndTheCardSaysSo() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.Unsupported,
                    readOnly = InteractionReadOnlyUi.Unsupported,
                    primary = null,
                    openInWebUrl = "http://paperclip.example.ts.net/issues/PAP-9",
                ),
            ),
        )

        rule.onNodeWithTag(tag("open-web")).performClick()

        launcher.tabbed shouldBe emptyList()
        launcher.browsed shouldBe emptyList()
        rule.hasTag(tag("open-web-failed")) shouldBe true
    }

    @Test
    fun anUnsupportedKindWithoutAnAddressHasNoButtonJustTheReason() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.Unsupported,
                    readOnly = InteractionReadOnlyUi.Unsupported,
                    primary = null,
                    openInWebUrl = null,
                ),
            ),
        )

        rule.hasTag(tag("open-web")) shouldBe false
        rule.hasTag(tag("read-only")) shouldBe true
    }

    @Test
    fun aViewerSeesTheCardButCannotAnswerAndTheReasonIsGiven() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.SuggestTasks,
                    tasks = listOf(choice("t1", "Add a retry queue", selected = true)),
                    hasReject = true,
                    readOnly = InteractionReadOnlyUi.ViewerRole,
                ),
            ),
        )

        rule.onNodeWithTag(tag("choice--t1")).assertIsNotEnabled()
        rule.hasTag(tag("primary")) shouldBe false
        rule.onNodeWithTag(tag("read-only")).assertTextEquals("Your role is Viewer, so you can look but not answer.")
    }

    @Test
    fun offlineSaysAnswersAreUnavailable() {
        show(interactionCard(CardSpec(readOnly = InteractionReadOnlyUi.Offline, prompt = "Proceed?")))

        rule.onNodeWithTag(tag("read-only")).assertTextEquals("You're offline. Answers are unavailable.")
    }

    @Test
    fun aHandledCardNamesItsOutcomeAndOffersNoButtons() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.AskUserQuestions,
                    status = InteractionStatusUi.Answered,
                    readOnly = InteractionReadOnlyUi.NotPending,
                    primary = InteractionPrimaryUi.Respond,
                ),
            ),
        )

        rule.onNodeWithTag(tag("kind")).assertTextEquals("QUESTIONS FOR YOU · ANSWERED")
        rule.hasTag(tag("primary")) shouldBe false
        rule.hasTag(tag("read-only")) shouldBe false
    }

    @Test
    fun whileAnotherChangeIsInFlightTheControlsAreDisabled() {
        show(
            interactionCard(
                CardSpec(
                    kind = InteractionKindUi.SuggestTasks,
                    tasks = listOf(choice("t1", "Add a retry queue", selected = true)),
                    hasReject = true,
                ),
            ),
            isBusy = true,
        )

        rule.onNodeWithTag(tag("choice--t1")).assertIsNotEnabled()
        rule.onNodeWithTag(tag("primary")).assertIsNotEnabled()
        rule.onNodeWithTag(tag("reject")).assertIsNotEnabled()
    }
}
