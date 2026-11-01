package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import kotlinx.coroutines.delay

private const val MAX_RETRIES = 3
private const val FIRST_BACKOFF_MS = 500L

/**
 * Runs a READ and retries it with exponential backoff (500 ms, 1 s, 2 s) when the failure may be transient
 * (PRD FR-096). Never use this for a mutation.
 */
internal suspend fun <T> retryingRead(read: suspend () -> DomainResult<T>): DomainResult<T> {
    var backoff = FIRST_BACKOFF_MS
    var result = read()
    var retries = 0
    while (retries < MAX_RETRIES && result.isTransientFailure()) {
        delay(backoff)
        backoff *= 2
        retries += 1
        result = read()
    }
    return result
}

internal fun DomainResult<*>.isTransientFailure(): Boolean =
    this is DomainResult.Failure &&
        (error == DomainError.Unreachable || error == DomainError.Timeout || error == DomainError.ServerError)
