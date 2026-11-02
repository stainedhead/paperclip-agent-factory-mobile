package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.auth.SignInResult
import com.stainedhead.paperclipmobile.domain.DomainError
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** F-B-3: one mapping from a sign-in outcome to what the first run and edit screens show. */
class SignInResultMappingTest {
    @Test
    fun `every SignInResult maps to the same pairing status and error key for both screens`() {
        val table =
            mapOf(
                SignInResult.Cancelled to (PairingStatus.Cancelled to null),
                SignInResult.Expired to (PairingStatus.Expired to null),
                SignInResult.NotFound to (PairingStatus.Failed to ErrorUi(ErrorKey.NotFound)),
                SignInResult.InvalidKey to (PairingStatus.Failed to ErrorUi(ErrorKey.InvalidKey)),
                SignInResult.StorageFailed to (PairingStatus.Failed to ErrorUi(ErrorKey.StorageFailed)),
                SignInResult.Failed(DomainError.Unauthorized) to (PairingStatus.Failed to ErrorUi(ErrorKey.SignedOut)),
            )

        table.forEach { (result, expected) ->
            val failure = result.toFailure()
            (failure?.pairing to failure?.error) shouldBe expected
        }
        SignInResult.SignedIn("key", null).toFailure() shouldBe null
    }
}
