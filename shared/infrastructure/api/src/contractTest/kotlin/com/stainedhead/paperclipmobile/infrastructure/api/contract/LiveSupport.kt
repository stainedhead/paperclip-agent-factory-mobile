package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LogChunk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val TEST_TIMEOUT_MS = 180_000L

/**
 * Runs a test body against the live world on a real dispatcher (virtual time would skip the poll delays). It returns
 * Unit on purpose: JUnit silently skips a test method that returns a value.
 */
internal fun live(block: suspend LiveContext.() -> Unit) {
    val context = LiveContext.get()
    runBlocking { withTimeout(TEST_TIMEOUT_MS) { context.block() } }
}

/** True when a decoded reply carries something to compare: a non-empty list, map or text, a log chunk with text. */
internal fun hasData(value: Any?): Boolean =
    when (value) {
        null -> false
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        is CharSequence -> value.isNotBlank()
        is LogChunk -> value.content.isNotBlank()
        else -> true
    }

/**
 * The value of a successful call. The operation counts as verified with data only when the reply was non-empty
 * ([hasData]); an empty reply is recorded as reachable but empty. A failure is recorded and fails the test.
 */
internal fun <T> DomainResult<T>.verified(
    operation: String,
    detail: String = "",
    into: LiveLedger = LiveReport,
): T =
    when (this) {
        is DomainResult.Success -> {
            value.also { into.record(operation, detail, hasData(it)) }
        }

        is DomainResult.Failure -> {
            into.failed(operation, error.toString())
            error("$operation failed with $error")
        }
    }

/** A call that must fail with exactly [expected]; recorded as an error path, which is not verification with data. */
internal fun DomainResult<*>.verifiedError(
    operation: String,
    expected: DomainError,
    into: LiveLedger = LiveReport,
) {
    check(this is DomainResult.Failure) { "$operation was expected to fail with $expected but succeeded" }
    check(error == expected) { "$operation failed with $error, expected $expected" }
    into.errorPath(operation, "(error path $expected)")
}

/**
 * Checks one fact about the live server that the adapters assume. The block asserts and returns a short description
 * for the report; a failed assertion is counted as a MISMATCH, which fails the run at its end (AD-050), so one run
 * shows every difference at once instead of stopping at the first.
 */
internal inline fun observe(
    label: String,
    block: () -> String,
) {
    try {
        LiveReport.note(label, block())
    } catch (failure: AssertionError) {
        LiveReport.mismatch(label, (failure.message ?: failure.toString()).take(MAX_REASON))
    } catch (failure: IllegalStateException) {
        LiveReport.mismatch(label, (failure.message ?: failure.toString()).take(MAX_REASON))
    }
}

internal const val MAX_REASON = 400

private const val POLL_DELAY_MS = 1000L

/**
 * Calls [step] once a second until it returns a value (at most [seconds]); the failure message names [what] and the
 * last thing seen. The live server is asynchronous (runs start, events and logs arrive after the reply).
 */
internal suspend fun <T : Any> eventually(
    what: String,
    seconds: Int = 45,
    step: suspend () -> T?,
): T {
    repeat(seconds) {
        step()?.let { return it }
        kotlinx.coroutines.delay(POLL_DELAY_MS)
    }
    error("Gave up after ${seconds}s waiting for $what")
}
