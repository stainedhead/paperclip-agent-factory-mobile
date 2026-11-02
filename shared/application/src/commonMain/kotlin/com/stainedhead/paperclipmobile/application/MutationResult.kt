package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.AnswerIssue
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InputIssue
import com.stainedhead.paperclipmobile.domain.ResolutionIssue
import com.stainedhead.paperclipmobile.domain.port.BiometricResult

/** Why a mutation was refused before the gateway was called. */
sealed interface InvalidInput {
    data object NoteRequired : InvalidInput

    data object BodyRequired : InvalidInput

    /** The thing is not in a state this action works from, for example request revision on an approved approval. */
    data object WrongStatus : InvalidInput

    data object UnknownOption : InvalidInput

    data object SnoozeNotInFuture : InvalidInput

    data object UndoWindowClosed : InvalidInput

    data object TitleRequired : InvalidInput

    /** The interaction is addressed to someone else (`resolverAudience`). */
    data object NotAddressedToYou : InvalidInput

    data class Answer(
        val issue: AnswerIssue,
    ) : InvalidInput

    data class Input(
        val issue: InputIssue,
    ) : InvalidInput

    data class Amount(
        val issue: ResolutionIssue,
    ) : InvalidInput
}

/**
 * What a mutation use case reports (ADR-019). Everything except [Done], [Reconciled], [Unknown] and [Failed] means no
 * gateway call was made.
 */
sealed interface MutationResult<out T> {
    data class Done<out T>(
        val value: T,
    ) : MutationResult<T>

    /** The role does not allow this action. */
    data object Denied : MutationResult<Nothing>

    data class Invalid(
        val reason: InvalidInput,
    ) : MutationResult<Nothing>

    /** Device authentication was cancelled, failed, locked out or unavailable. */
    data class NotAuthorized(
        val result: BiometricResult,
    ) : MutationResult<Nothing>

    /** The call failed. It was sent once and is not retried. */
    data class Failed(
        val error: DomainError,
    ) : MutationResult<Nothing>

    /**
     * The call failed in a way that leaves the real state unknown (timeout) or shows it changed elsewhere (conflict).
     * The entity was read again; [current] is the truth (PRD FR-096).
     */
    data class Reconciled<out T>(
        val error: DomainError,
        val current: T,
    ) : MutationResult<T>

    /**
     * The call timed out and what could be read afterwards does not show whether it took effect (F-A-6). The caller
     * must show that, must not report success or "skipped", and must not send the call again without looking.
     */
    data class Unknown(
        val error: DomainError,
    ) : MutationResult<Nothing>
}

internal fun <T> DomainResult<T>.toMutation(): MutationResult<T> =
    when (this) {
        is DomainResult.Success -> MutationResult.Done(value)
        is DomainResult.Failure -> MutationResult.Failed(error)
    }

internal fun DomainError.needsReread(): Boolean = this == DomainError.Timeout || this == DomainError.Conflict

/**
 * Turns a failed mutation into [MutationResult.Reconciled] when [error] says the state may have changed, by reading the
 * entity once. If that read fails too, the original error is reported.
 */
internal suspend fun <T> failed(
    error: DomainError,
    rereadWhen: (DomainError) -> Boolean = DomainError::needsReread,
    reread: suspend () -> DomainResult<T>,
): MutationResult<T> {
    if (!rereadWhen(error)) return MutationResult.Failed(error)
    return when (val current = reread()) {
        is DomainResult.Success -> MutationResult.Reconciled(error, current.value)
        is DomainResult.Failure -> MutationResult.Failed(error)
    }
}
