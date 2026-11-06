package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.delay

private const val MAX_RETRIES = 3
private const val FIRST_BACKOFF_MS = 500L
private const val MAX_TOTAL_WAIT_MS = 3_500L

/**
 * How long one use case may spend waiting between retries, across all of its reads (PRD FR-096). A use case that reads
 * three things shares one budget, so an offline or failing server costs one backoff series, not three. With no
 * [connectivity] the phone is assumed online.
 */
internal class RetryBudget(
    private val connectivity: Connectivity? = null,
) {
    private var remainingMs = MAX_TOTAL_WAIT_MS

    /** False when offline (waiting cannot help) or when [ms] would exceed what is left; otherwise spends it. */
    fun mayWait(ms: Long): Boolean {
        if (connectivity?.isOnline?.value == false || ms > remainingMs) return false
        remainingMs -= ms
        return true
    }
}

/**
 * Runs a READ and retries it with exponential backoff (500 ms, 1 s, 2 s) when the failure may be transient
 * (PRD FR-096). Never use this for a mutation. A certificate failure is not transient, an offline phone is not
 * retried, and the waits come out of [budget].
 */
internal suspend fun <T> retryingRead(
    budget: RetryBudget = RetryBudget(),
    read: suspend () -> DomainResult<T>,
): DomainResult<T> {
    var backoff = FIRST_BACKOFF_MS
    var result = read()
    var retries = 0
    while (retries < MAX_RETRIES && result.isTransientFailure() && budget.mayWait(backoff)) {
        delay(backoff)
        backoff *= 2
        retries += 1
        result = read()
    }
    return result
}

internal fun DomainResult<*>.isTransientFailure(): Boolean =
    this is DomainResult.Failure &&
        (
            error == DomainError.Unreachable ||
                error == DomainError.ConnectionLost ||
                error == DomainError.Timeout ||
                error == DomainError.ServerError
        )
