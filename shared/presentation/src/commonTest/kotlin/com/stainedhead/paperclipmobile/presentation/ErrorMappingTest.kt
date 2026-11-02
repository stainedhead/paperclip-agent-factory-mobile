package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** FR-097: a server error becomes a typed domain error and the screen shows a key, never raw JSON. */
class ErrorMappingTest {
    @Test
    fun `FR-097 every domain error maps to its own error key`() {
        val table =
            mapOf(
                DomainError.Unreachable to ErrorKey.Unreachable,
                DomainError.CertificateInvalid to ErrorKey.CertificateInvalid,
                DomainError.Timeout to ErrorKey.Timeout,
                DomainError.Unauthorized to ErrorKey.SignedOut,
                DomainError.Forbidden to ErrorKey.NoPermission,
                DomainError.NotFound to ErrorKey.NotFound,
                DomainError.Conflict to ErrorKey.Conflict,
                DomainError.ServerError to ErrorKey.ServerError,
                DomainError.RateLimited to ErrorKey.RateLimited,
                DomainError.Malformed to ErrorKey.Malformed,
            )

        table.forEach { (error, key) -> error.toErrorUi() shouldBe ErrorUi(key) }
    }

    @Test
    fun `FR-097 a rejection carries the server's own reason as plain text`() {
        DomainError.Rejected("New budget must exceed current observed spend").toErrorUi() shouldBe
            ErrorUi(ErrorKey.Rejected, "New budget must exceed current observed spend")
    }
}
