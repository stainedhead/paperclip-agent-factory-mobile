package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult

/** The value of a call that must succeed; fails the contract with [what] and the actual result otherwise. */
internal fun <T> DomainResult<T>.mustSucceed(what: String): T =
    (this as? DomainResult.Success)?.value ?: error("$what must succeed but was $this")

/** The call must fail with exactly [expected]. */
internal fun DomainResult<*>.mustFailWith(
    expected: DomainError,
    what: String,
) {
    val actual = (this as? DomainResult.Failure)?.error
    check(actual == expected) { "$what must fail with $expected but was $this" }
}

/** The call must fail with a server reason (422). */
internal fun DomainResult<*>.mustBeRejected(what: String) {
    val actual = (this as? DomainResult.Failure)?.error
    check(actual is DomainError.Rejected) { "$what must be rejected with a reason but was $this" }
}
