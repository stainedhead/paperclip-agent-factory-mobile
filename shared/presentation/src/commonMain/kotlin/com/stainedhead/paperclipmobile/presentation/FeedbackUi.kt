package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
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
        InvalidInput.NoteRequired -> {
            InvalidKey.NoteRequired
        }

        InvalidInput.BodyRequired -> {
            InvalidKey.BodyRequired
        }

        InvalidInput.WrongStatus -> {
            InvalidKey.WrongStatus
        }

        InvalidInput.UnknownOption -> {
            InvalidKey.UnknownOption
        }

        InvalidInput.SnoozeNotInFuture -> {
            InvalidKey.SnoozeNotInFuture
        }

        InvalidInput.UndoWindowClosed -> {
            InvalidKey.UndoWindowClosed
        }

        is InvalidInput.Input -> {
            when (issue) {
                is InputIssue.Required -> InvalidKey.InputRequired
                is InputIssue.TooLong -> InvalidKey.InputTooLong
            }
        }

        is InvalidInput.Amount -> {
            when (issue) {
                ResolutionIssue.MustExceedObserved -> InvalidKey.AmountMustExceedObserved
                ResolutionIssue.WrongCurrency -> InvalidKey.WrongCurrency
            }
        }
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
                inputId = (reason as? InvalidInput.Input)?.issue?.inputId,
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
    }
