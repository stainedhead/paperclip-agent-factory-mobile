package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.AnswerIssue
import com.stainedhead.paperclipmobile.domain.InputIssue
import com.stainedhead.paperclipmobile.domain.ResolutionIssue

enum class FeedbackKind {
    /** The role does not allow this. */
    NotAllowed,
    Invalid,
    DeviceAuth,

    /** The call failed and was sent once. */
    Failed,

    /** The call failed, the screen re-read the entity and now shows its true state. */
    Reconciled,

    /** The call timed out and the screen cannot tell whether it took effect: refresh before trying again. */
    Unknown,
}

enum class InvalidKey {
    NoteRequired,
    BodyRequired,
    WrongStatus,
    UnknownOption,
    SnoozeNotInFuture,
    UndoWindowClosed,
    InputRequired,
    InputTooLong,
    AmountMustExceedObserved,
    WrongCurrency,
    TitleRequired,
    NotAddressedToYou,
    AnswerUnsupported,
    AnswerBlocked,
    AnswerWrongKind,
    AnswerNothingSelected,
    AnswerUnknownChoice,
    AnswerTooFewSelected,
    AnswerTooManySelected,
    AnswerQuestionRequired,
    AnswerVerdictNotEnabled,
    AnswerReasonRequired,
}

/** What happened to the last action, as keys. [inputId] names the decision input that was wrong. */
data class FeedbackUi(
    val kind: FeedbackKind,
    val invalid: InvalidKey? = null,
    val inputId: String? = null,
    val deviceAuth: DeviceAuthIssue? = null,
    val error: ErrorUi? = null,
)

internal fun InvalidInput.toKey(): InvalidKey =
    when (this) {
        InvalidInput.NoteRequired -> InvalidKey.NoteRequired
        InvalidInput.BodyRequired -> InvalidKey.BodyRequired
        InvalidInput.WrongStatus -> InvalidKey.WrongStatus
        InvalidInput.UnknownOption -> InvalidKey.UnknownOption
        InvalidInput.SnoozeNotInFuture -> InvalidKey.SnoozeNotInFuture
        InvalidInput.UndoWindowClosed -> InvalidKey.UndoWindowClosed
        InvalidInput.TitleRequired -> InvalidKey.TitleRequired
        InvalidInput.NotAddressedToYou -> InvalidKey.NotAddressedToYou
        is InvalidInput.Answer -> issue.toKey()
        is InvalidInput.Input -> issue.toKey()
        is InvalidInput.Amount -> issue.toKey()
    }

private fun InputIssue.toKey() =
    when (this) {
        is InputIssue.Required -> InvalidKey.InputRequired
        is InputIssue.TooLong -> InvalidKey.InputTooLong
    }

private fun ResolutionIssue.toKey() =
    when (this) {
        ResolutionIssue.MustExceedObserved -> InvalidKey.AmountMustExceedObserved
        ResolutionIssue.WrongCurrency -> InvalidKey.WrongCurrency
    }

/** The feedback for a mutation, or null when it succeeded. */
internal fun MutationResult<*>.toFeedback(): FeedbackUi? =
    when (this) {
        is MutationResult.Done -> {
            null
        }

        MutationResult.Denied -> {
            FeedbackUi(FeedbackKind.NotAllowed)
        }

        is MutationResult.Invalid -> {
            FeedbackUi(
                FeedbackKind.Invalid,
                invalid = reason.toKey(),
                inputId = reason.inputId(),
            )
        }

        is MutationResult.NotAuthorized -> {
            FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = result.toIssue())
        }

        is MutationResult.Failed -> {
            FeedbackUi(FeedbackKind.Failed, error = error.toErrorUi())
        }

        is MutationResult.Reconciled -> {
            FeedbackUi(FeedbackKind.Reconciled, error = error.toErrorUi())
        }

        is MutationResult.Unknown -> {
            FeedbackUi(FeedbackKind.Unknown, error = error.toErrorUi())
        }
    }

private fun InvalidInput.inputId(): String? =
    when (this) {
        is InvalidInput.Input -> issue.inputId
        is InvalidInput.Answer -> issue.targetId()
        else -> null
    }

internal fun AnswerIssue.targetId(): String? =
    when (this) {
        is AnswerIssue.UnknownChoice -> id
        is AnswerIssue.QuestionRequired -> questionId
        is AnswerIssue.VerdictNotEnabled -> itemId
        is AnswerIssue.ReasonRequired -> id
        else -> null
    }

internal fun AnswerIssue.toKey(): InvalidKey =
    when (this) {
        AnswerIssue.NotPending -> InvalidKey.WrongStatus
        AnswerIssue.Unsupported -> InvalidKey.AnswerUnsupported
        AnswerIssue.Blocked -> InvalidKey.AnswerBlocked
        AnswerIssue.WrongKind -> InvalidKey.AnswerWrongKind
        AnswerIssue.NothingSelected -> InvalidKey.AnswerNothingSelected
        is AnswerIssue.UnknownChoice -> InvalidKey.AnswerUnknownChoice
        is AnswerIssue.TooFewSelected -> InvalidKey.AnswerTooFewSelected
        is AnswerIssue.TooManySelected -> InvalidKey.AnswerTooManySelected
        is AnswerIssue.QuestionRequired -> InvalidKey.AnswerQuestionRequired
        is AnswerIssue.VerdictNotEnabled -> InvalidKey.AnswerVerdictNotEnabled
        is AnswerIssue.ReasonRequired -> InvalidKey.AnswerReasonRequired
    }
