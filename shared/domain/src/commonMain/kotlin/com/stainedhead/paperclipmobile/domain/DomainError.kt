package com.stainedhead.paperclipmobile.domain

/** Why a call to a Paperclip instance failed (PRD FR-095 to FR-097). Raw server bodies never cross this type. */
sealed interface DomainError {
    /** No network path to the host, or its certificate or name could not be verified. */
    data object Unreachable : DomainError

    data object Timeout : DomainError

    /** 401: the key is missing, revoked or expired. The factory shows as Signed out and keeps its configuration. */
    data object Unauthorized : DomainError

    /** 403: the role cannot do this in this company. */
    data object Forbidden : DomainError

    data object NotFound : DomainError

    /** The thing changed or was already resolved by someone else. */
    data object Conflict : DomainError

    /** 422: the server refused with a reason, for example a budget incident that cannot be raised yet. */
    data class Rejected(
        val reason: String,
    ) : DomainError

    data object ServerError : DomainError

    /** The body could not be understood. */
    data object Malformed : DomainError
}

/** The outcome of a call that can fail with a [DomainError]. Failures are values, never thrown. */
sealed interface DomainResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : DomainResult<T>

    data class Failure(
        val error: DomainError,
    ) : DomainResult<Nothing>
}

fun <T> DomainResult<T>.valueOrNull(): T? = (this as? DomainResult.Success)?.value
