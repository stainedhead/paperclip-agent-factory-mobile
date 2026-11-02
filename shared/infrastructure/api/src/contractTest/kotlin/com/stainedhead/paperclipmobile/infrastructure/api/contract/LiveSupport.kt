package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.opentest4j.TestAbortedException

private const val TEST_TIMEOUT_MS = 180_000L

/**
 * Runs a test body against the live world on a real dispatcher (virtual time would skip the poll delays). It returns
 * Unit on purpose: JUnit silently skips a test method that returns a value.
 */
internal fun live(block: suspend LiveContext.() -> Unit) {
    val context = LiveContext.get()
    runBlocking { withTimeout(TEST_TIMEOUT_MS) { context.block() } }
}

/** The value of a successful call; marks [operation] as verified live. A failure is a test failure. */
internal fun <T> DomainResult<T>.verified(
    operation: String,
    detail: String = "",
): T =
    when (this) {
        is DomainResult.Success -> {
            value.also { LiveReport.verified(operation, detail) }
        }

        is DomainResult.Failure -> {
            LiveReport.failed(operation, error.toString())
            error("$operation failed with $error")
        }
    }

/** A call that must fail with exactly [expected]; marks [operation] as verified live (its error path). */
internal fun DomainResult<*>.verifiedError(
    operation: String,
    expected: DomainError,
) {
    check(this is DomainResult.Failure) { "$operation was expected to fail with $expected but succeeded" }
    check(error == expected) { "$operation failed with $error, expected $expected" }
    LiveReport.verified(operation, "(error path $expected)")
}

/**
 * Tries a step the server may refuse for a reason this test cannot know in advance (a seed it rejects, an operation
 * whose behaviour was only read from source). A failure is recorded in the report and the test goes on.
 */
internal inline fun <T> soft(
    operation: String,
    block: () -> T,
): T? =
    try {
        block()
    } catch (aborted: TestAbortedException) {
        throw aborted
    } catch (failure: AssertionError) {
        record(operation, failure)
    } catch (failure: IllegalStateException) {
        record(operation, failure)
    }

internal fun record(
    operation: String,
    failure: Throwable,
): Nothing? {
    val text = (failure.message ?: failure.toString()).take(MAX_REASON)
    if (LiveReport.OPERATIONS.contains(
            operation,
        )
    ) {
        LiveReport.failed(operation, text)
    } else {
        LiveReport.note(operation, text)
    }
    return null
}

internal const val MAX_REASON = 400

/**
 * Checks one fact about the live server that the adapters assume but the source did not settle. The block asserts and
 * returns a short description; a mismatch is recorded in the report as MISMATCH instead of failing the run, so one run
 * shows every difference at once (AD-050).
 */
internal inline fun observe(
    label: String,
    block: () -> String,
) {
    soft(label) { LiveReport.note(label, block()) }
}
