package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.AttestedReadResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult

/**
 * The protected-secret cases of the secret store contract (FR-011, R-001, R-011): a protected secret is opened only by
 * an authentication made for that read. [create] gives a fresh observable store, or `null` where the platform asks the
 * person itself (the iOS Keychain), which skips the cases. These cases describe a DEVICE store (ADR-139). The session
 * cache above it (`SessionCachingSecretStore`, ADR-145) deliberately does not reuse approvals of the same kind: it
 * passes the contract with `createProtected` left `null`, and its prompt counts have their own tests.
 */
class ProtectedSecretStoreCases(
    private val create: () -> ProtectedStoreHarness?,
) {
    private val key = SecretKey(InstanceId("a"), SecretName.BoardToken)
    private val protected = SecretPolicy(requireUserPresence = true)

    suspend fun runAll() {
        create()?.let { twoConsecutiveReadsPromptTwice(it) }
        create()?.let { aRefusedPromptDeniesTheReadAndTheWrite(it) }
        create()?.let { anInvalidatedKeyReadsAsInvalidated(it) }
        create()?.let { anEarlierApprovalIsNeverReused(it) }
        create()?.let { aProtectedReadIsAttestedByExactlyOnePrompt(it) }
        create()?.let { aRefusedOrInvalidatedAttestedReadIsNeverAuthenticated(it) }
    }

    private suspend fun twoConsecutiveReadsPromptTwice(harness: ProtectedStoreHarness) {
        harness.approve = true
        check(harness.store.put(key, Secret.of("one"), protected) == SecretWriteResult.Stored) {
            "a protected put must store"
        }
        val before = harness.prompts
        repeat(2) {
            val read = harness.store.get(key, "contract test")
            check(read is SecretReadResult.Found && read.secret == Secret.of("one")) { "protected get must return it" }
        }
        check(harness.prompts - before == 2) { "every protected read asks the person, an approval is never reused" }
    }

    private suspend fun aRefusedPromptDeniesTheReadAndTheWrite(harness: ProtectedStoreHarness) {
        harness.approve = false
        check(harness.store.put(key, Secret.of("one"), protected) == SecretWriteResult.Failed) {
            "a protected put the person refuses must fail"
        }
        check(harness.store.get(key, "contract test") == SecretReadResult.Missing) { "a refused put stores nothing" }
        harness.approve = true
        check(harness.store.put(key, Secret.of("one"), protected) == SecretWriteResult.Stored) { "approved put" }
        harness.approve = false
        check(harness.store.get(key, "contract test") == SecretReadResult.Denied) { "a refused read is Denied" }
        harness.approve = true
        val read = harness.store.get(key, "contract test")
        check(read is SecretReadResult.Found && read.secret == Secret.of("one")) { "a refusal changes nothing" }
    }

    private suspend fun anInvalidatedKeyReadsAsInvalidated(harness: ProtectedStoreHarness) {
        harness.approve = true
        harness.store.put(key, Secret.of("one"), protected)
        harness.invalidate()
        check(harness.store.get(key, "contract test") == SecretReadResult.Invalidated) { "invalidated key" }
    }

    private suspend fun aProtectedReadIsAttestedByExactlyOnePrompt(harness: ProtectedStoreHarness) {
        harness.approve = true
        harness.store.put(key, Secret.of("one"), protected)
        val before = harness.prompts
        val read = harness.store.readAttested(key, "contract test")
        check(read is AttestedReadResult.Authenticated && read.secret == Secret.of("one")) {
            "a protected read the person approved is Authenticated"
        }
        check(harness.prompts - before == 1) { "the attested read is the one prompt" }
    }

    private suspend fun aRefusedOrInvalidatedAttestedReadIsNeverAuthenticated(harness: ProtectedStoreHarness) {
        harness.approve = true
        harness.store.put(key, Secret.of("one"), protected)
        harness.approve = false
        check(harness.store.readAttested(key, "contract test") == AttestedReadResult.Denied) { "refused is Denied" }
        harness.approve = true
        harness.invalidate()
        check(harness.store.readAttested(key, "contract test") == AttestedReadResult.Invalidated) {
            "an invalidated key is Invalidated"
        }
    }

    private suspend fun anEarlierApprovalIsNeverReused(harness: ProtectedStoreHarness) {
        harness.approve = true
        harness.store.put(key, Secret.of("one"), protected)
        check(harness.store.get(key, "contract test") is SecretReadResult.Found) { "approved read" }
        harness.approve = false
        check(harness.store.get(key, "contract test") == SecretReadResult.Denied) {
            "an earlier approval is not reused"
        }
    }
}
