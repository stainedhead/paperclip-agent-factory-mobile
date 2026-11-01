@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InteractionItem
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.InteractionOption
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.Question
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.RequestItemVerdicts
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import com.stainedhead.paperclipmobile.domain.SuggestedTask
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun Env.add(vararg more: Interaction) {
    interactions.store.getValue(WorkFixtures.pap1182).addAll(more)
}

private fun IssueState.interaction(id: String) = thread.first { it.id == id }.interaction!!

private suspend fun TestScope.loaded(env: Env): IssueStateHolder {
    val holder = issueHolder(env)
    advanceUntilIdle()
    return holder
}

private val questions =
    AskUserQuestions(
        WorkFixtures.meta("q-set"),
        listOf(
            Question(
                "region",
                "Which region?",
                false,
                true,
                false,
                listOf(InteractionOption("eu", "EU"), InteractionOption("us", "US")),
            ),
            Question("notes", "Anything else?", true, false, true, emptyList()),
        ),
    )
private val tasks =
    SuggestTasks(
        WorkFixtures.meta("tasks"),
        listOf(SuggestedTask("a", "Write tests", null), SuggestedTask("b", "Ship it", null)),
    )
private val checkbox =
    RequestCheckboxConfirmation(
        WorkFixtures.meta("boxes"),
        "Which environments?",
        listOf(InteractionOption("s", "Staging"), InteractionOption("p", "Production")),
        defaultSelected = setOf("s"),
        minSelected = 1,
        maxSelected = 1,
    )
private val verdicts =
    RequestItemVerdicts(
        WorkFixtures.meta("verdicts"),
        "Review",
        listOf(InteractionItem("x", "Item X"), InteractionItem("y", "Item Y")),
    )

class InteractionRenderingTest {
    @Test
    fun `FR-063 a pending confirmation can be answered and a resolved one is read only`() =
        runTest {
            val holder = loaded(workEnv())
            val pending = holder.state.value.interaction("int-confirm")
            val done = holder.state.value.interaction("int-questions")

            pending.canAnswer shouldBe true
            pending.readOnlyReason shouldBe null
            pending.primary shouldBe InteractionPrimaryUi.Accept
            pending.hasReject shouldBe true
            pending.rejectRequiresReason shouldBe true
            pending.prompt shouldBe "Switch traffic to the new billing service?"
            done.canAnswer shouldBe false
            done.readOnlyReason shouldBe InteractionReadOnlyUi.NotPending
            done.status shouldBe InteractionStatusUi.Answered
        }

    @Test
    fun `FR-063 an interaction addressed to someone else is read only with that reason`() =
        runTest {
            val env = workEnv()
            env.add(RequestConfirmation(WorkFixtures.meta("other", addresseeUserId = "user-2"), "Merge?"))
            val holder = loaded(env)

            val card = holder.state.value.interaction("other")
            card.canAnswer shouldBe false
            card.readOnlyReason shouldBe InteractionReadOnlyUi.NotForYou
            holder.onIntent(IssueIntent.InteractionPrimaryTapped("other"))
            advanceUntilIdle()
            env.interactions.count("answer") shouldBe 0
        }

    @Test
    fun `acceptance 8 a viewer sees the form read only and acceptance 11 offline says so`() =
        runTest {
            val viewer = loaded(workEnv(Role.Viewer))
            viewer.state.value
                .interaction("int-confirm")
                .readOnlyReason shouldBe InteractionReadOnlyUi.ViewerRole

            val env = workEnv()
            val holder = loaded(env)
            env.connectivity.setOnline(false)
            advanceUntilIdle()
            holder.state.value
                .interaction("int-confirm")
                .readOnlyReason shouldBe InteractionReadOnlyUi.Offline
            holder.onIntent(IssueIntent.InteractionPrimaryTapped("int-confirm"))
            advanceUntilIdle()
            env.interactions.count("answer") shouldBe 0
        }

    @Test
    fun `FR-063 an unsupported kind says open in the web app and is never sent`() =
        runTest {
            val env = workEnv()
            env.add(UnsupportedInteraction(WorkFixtures.meta("ci"), InteractionKind.ConnectionIntent))
            val holder = loaded(env)

            val card = holder.state.value.interaction("ci")
            card.kind shouldBe InteractionKindUi.Unsupported
            card.canAnswer shouldBe false
            card.readOnlyReason shouldBe InteractionReadOnlyUi.Unsupported
            card.openInWebUrl shouldBe "https://home.example.com/issues/PAP-1182"
            holder.onIntent(IssueIntent.InteractionPrimaryTapped("ci"))
            holder.onIntent(IssueIntent.InteractionSkipTapped("ci"))
            advanceUntilIdle()
            env.interactions.count("answer") shouldBe 0
        }
}

class InteractionAnswerTest {
    @Test
    fun `FR-063 accepting a confirmation sends one answer and shows the final state`() =
        runTest {
            val env = workEnv(Role.Operator)
            val holder = loaded(env)

            holder.onIntent(IssueIntent.InteractionPrimaryTapped("int-confirm"))
            advanceUntilIdle()

            env.interactions.answers shouldBe listOf(InteractionAnswer.Accept)
            val card = holder.state.value.interaction("int-confirm")
            card.status shouldBe InteractionStatusUi.Accepted
            card.canAnswer shouldBe false
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a required reject reason is checked before anything is sent`() =
        runTest {
            val env = workEnv()
            val holder = loaded(env)
            holder.state.value
                .interaction("int-confirm")
                .rejectIssue shouldBe InvalidKey.AnswerReasonRequired

            holder.onIntent(IssueIntent.InteractionRejectTapped("int-confirm"))
            advanceUntilIdle()
            holder.state.value.feedback shouldBe
                FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerReasonRequired)
            env.interactions.count("answer") shouldBe 0

            holder.onIntent(IssueIntent.InteractionTextChanged("int-confirm", "", "not now"))
            advanceUntilIdle()
            holder.state.value
                .interaction("int-confirm")
                .rejectIssue shouldBe null
            holder.onIntent(IssueIntent.InteractionRejectTapped("int-confirm"))
            advanceUntilIdle()
            env.interactions.answers shouldBe listOf(InteractionAnswer.Reject("not now"))
            holder.state.value
                .interaction("int-confirm")
                .status shouldBe InteractionStatusUi.Rejected
        }

    @Test
    fun `FR-063 questions need the required answer then respond once`() =
        runTest {
            val env = workEnv()
            env.add(questions)
            val holder = loaded(env)
            holder.state.value
                .interaction("q-set")
                .primary shouldBe InteractionPrimaryUi.Respond
            holder.state.value
                .interaction("q-set")
                .submitIssue shouldBe InvalidKey.AnswerQuestionRequired
            holder.state.value
                .interaction("q-set")
                .submitIssueTargetId shouldBe "region"

            holder.onIntent(IssueIntent.InteractionPrimaryTapped("q-set"))
            advanceUntilIdle()
            env.interactions.count("answer") shouldBe 0
            holder.state.value.feedback
                ?.inputId shouldBe "region"

            holder.onIntent(IssueIntent.InteractionChoiceToggled("q-set", "region", "eu"))
            holder.onIntent(IssueIntent.InteractionTextChanged("q-set", "notes", "after the freeze"))
            advanceUntilIdle()
            holder.state.value
                .interaction("q-set")
                .questions
                .first { it.id == "region" }
                .options
                .first { it.id == "eu" }
                .selected shouldBe true
            holder.state.value
                .interaction("q-set")
                .submitIssue shouldBe null

            holder.onIntent(IssueIntent.InteractionPrimaryTapped("q-set"))
            advanceUntilIdle()
            val sent = env.interactions.answers.single() as InteractionAnswer.Respond
            sent.answers.first { it.questionId == "region" }.optionIds shouldBe listOf("eu")
            sent.answers.first { it.questionId == "notes" }.otherText shouldBe "after the freeze"
            holder.state.value
                .interaction("q-set")
                .status shouldBe InteractionStatusUi.Answered
        }

    @Test
    fun `a single choice replaces the previous one`() =
        runTest {
            val env = workEnv()
            env.add(questions)
            val holder = loaded(env)

            holder.onIntent(IssueIntent.InteractionChoiceToggled("q-set", "region", "eu"))
            holder.onIntent(IssueIntent.InteractionChoiceToggled("q-set", "region", "us"))
            advanceUntilIdle()

            val options =
                holder.state.value
                    .interaction("q-set")
                    .questions
                    .first { it.id == "region" }
                    .options
            options.filter { it.selected }.map { it.id } shouldBe listOf("us")
        }

    @Test
    fun `FR-063 suggested tasks start selected and accept sends the selected keys`() =
        runTest {
            val env = workEnv()
            env.add(tasks)
            val holder = loaded(env)
            holder.state.value
                .interaction("tasks")
                .tasks
                .map { it.selected } shouldBe listOf(true, true)

            holder.onIntent(IssueIntent.InteractionChoiceToggled("tasks", "", "a"))
            holder.onIntent(IssueIntent.InteractionChoiceToggled("tasks", "", "b"))
            advanceUntilIdle()
            holder.state.value
                .interaction("tasks")
                .submitIssue shouldBe InvalidKey.AnswerNothingSelected

            holder.onIntent(IssueIntent.InteractionChoiceToggled("tasks", "", "b"))
            holder.onIntent(IssueIntent.InteractionPrimaryTapped("tasks"))
            advanceUntilIdle()

            env.interactions.answers shouldBe listOf(InteractionAnswer.AcceptTasks(setOf("b")))
            holder.state.value
                .interaction("tasks")
                .status shouldBe InteractionStatusUi.Accepted
        }

    @Test
    fun `FR-063 a checkbox confirmation enforces its range and starts on the defaults`() =
        runTest {
            val env = workEnv()
            env.add(checkbox)
            val holder = loaded(env)
            holder.state.value
                .interaction("boxes")
                .options
                .filter { it.selected }
                .map { it.id } shouldBe listOf("s")

            holder.onIntent(IssueIntent.InteractionChoiceToggled("boxes", "", "p"))
            advanceUntilIdle()
            holder.state.value
                .interaction("boxes")
                .submitIssue shouldBe InvalidKey.AnswerTooManySelected

            holder.onIntent(IssueIntent.InteractionChoiceToggled("boxes", "", "s"))
            holder.onIntent(IssueIntent.InteractionPrimaryTapped("boxes"))
            advanceUntilIdle()
            env.interactions.answers shouldBe listOf(InteractionAnswer.AcceptOptions(setOf("p")))
        }

    @Test
    fun `FR-063 item verdicts need a reason for reject and then submit`() =
        runTest {
            val env = workEnv()
            env.add(verdicts)
            val holder = loaded(env)
            holder.state.value
                .interaction("verdicts")
                .primary shouldBe InteractionPrimaryUi.SubmitVerdicts
            holder.state.value
                .interaction("verdicts")
                .submitIssue shouldBe InvalidKey.AnswerNothingSelected

            holder.onIntent(IssueIntent.InteractionVerdictChosen("verdicts", "x", VerdictUi.Reject))
            holder.onIntent(IssueIntent.InteractionVerdictChosen("verdicts", "y", VerdictUi.Approve))
            advanceUntilIdle()
            holder.state.value
                .interaction("verdicts")
                .submitIssue shouldBe InvalidKey.AnswerReasonRequired
            holder.state.value
                .interaction("verdicts")
                .submitIssueTargetId shouldBe "x"

            holder.onIntent(IssueIntent.InteractionTextChanged("verdicts", "x", "wrong table"))
            holder.onIntent(IssueIntent.InteractionPrimaryTapped("verdicts"))
            advanceUntilIdle()

            val sent = env.interactions.answers.single() as InteractionAnswer.SubmitVerdicts
            sent.verdicts.map { it.itemId to it.verdict.serverName } shouldBe listOf("x" to "reject", "y" to "approve")
            holder.state.value
                .interaction("verdicts")
                .status shouldBe InteractionStatusUi.Answered
        }

    @Test
    fun `skip is sent for a pending interaction`() =
        runTest {
            val env = workEnv()
            val holder = loaded(env)

            holder.onIntent(IssueIntent.InteractionSkipTapped("int-confirm"))
            advanceUntilIdle()

            env.interactions.answers shouldBe listOf(InteractionAnswer.Skip(null))
            holder.state.value
                .interaction("int-confirm")
                .status shouldBe InteractionStatusUi.Cancelled
        }

    @Test
    fun `acceptance 10 a timeout on an answer is sent once and the interaction shows the server's state`() =
        runTest {
            val env = workEnv()
            env.interactions.failures["answer"] = DomainError.Timeout
            val holder = loaded(env)

            holder.onIntent(IssueIntent.InteractionPrimaryTapped("int-confirm"))
            holder.onIntent(IssueIntent.InteractionPrimaryTapped("int-confirm"))
            advanceUntilIdle()

            env.interactions.count("answer") shouldBe 1
            holder.state.value.feedback shouldBe FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Timeout))
            holder.state.value
                .interaction("int-confirm")
                .status shouldBe InteractionStatusUi.Pending
        }

    @Test
    fun `acceptance 12 an interaction resolved elsewhere shows its final state with no actions`() =
        runTest {
            val env = workEnv()
            val holder = loaded(env)
            env.interactions.answer(
                env.scope,
                WorkFixtures.pap1182,
                InteractionId("int-confirm"),
                InteractionAnswer.Reject("later"),
            )

            holder.onIntent(IssueIntent.InteractionPrimaryTapped("int-confirm"))
            advanceUntilIdle()

            val card = holder.state.value.interaction("int-confirm")
            card.status shouldBe InteractionStatusUi.Rejected
            card.canAnswer shouldBe false
            holder.state.value.feedback
                ?.kind shouldBe FeedbackKind.Reconciled
            InteractionStatus.Rejected.isOpen shouldBe false
        }
}
