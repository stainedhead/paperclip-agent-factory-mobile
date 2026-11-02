package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.AnswerIssue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class WorkFeedbackTest {
    private fun feedback(reason: InvalidInput) = MutationResult.Invalid(reason).toFeedback()

    @Test
    fun `a missing title and an interaction for someone else have their own keys`() {
        feedback(InvalidInput.TitleRequired) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.TitleRequired)
        feedback(InvalidInput.NotAddressedToYou) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.NotAddressedToYou)
    }

    @Test
    fun `FR-063 answer problems become keys and name the question or item that needs fixing`() {
        feedback(InvalidInput.Answer(AnswerIssue.NotPending)) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.WrongStatus)
        feedback(InvalidInput.Answer(AnswerIssue.Unsupported)) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerUnsupported)
        feedback(InvalidInput.Answer(AnswerIssue.Blocked)) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerBlocked)
        feedback(InvalidInput.Answer(AnswerIssue.WrongKind)) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerWrongKind)
        feedback(InvalidInput.Answer(AnswerIssue.NothingSelected)) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerNothingSelected)
        feedback(InvalidInput.Answer(AnswerIssue.TooFewSelected(2))) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerTooFewSelected)
        feedback(InvalidInput.Answer(AnswerIssue.TooManySelected(1))) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerTooManySelected)
        feedback(InvalidInput.Answer(AnswerIssue.QuestionRequired("q1"))) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerQuestionRequired, inputId = "q1")
        feedback(InvalidInput.Answer(AnswerIssue.UnknownChoice("zz"))) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerUnknownChoice, inputId = "zz")
        feedback(InvalidInput.Answer(AnswerIssue.VerdictNotEnabled("x"))) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerVerdictNotEnabled, inputId = "x")
        feedback(InvalidInput.Answer(AnswerIssue.ReasonRequired("x"))) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerReasonRequired, inputId = "x")
        feedback(InvalidInput.Answer(AnswerIssue.ReasonRequired(null))) shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.AnswerReasonRequired)
    }
}
