package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.AttestedReadResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.TestData

/**
 * A store that can protect a secret behind the device owner (FR-011), seen from the contract: every request for the
 * person's authentication is counted and answered by [approve].
 */
abstract class ProtectedStoreHarness {
    abstract val store: SecretStore

    /** How many times the person has been asked since the harness was made. */
    abstract val prompts: Int

    /** Whether the person approves the next request. */
    abstract var approve: Boolean

    /** The system invalidates the protected key (a new fingerprint was enrolled). */
    abstract fun invalidate()
}

/** Behaviour every [SecretStore] must have (PRD FR-010). Real adapters subclass this in Phase 1b. */
abstract class SecretStoreContract {
    abstract fun create(): SecretStore

    /** A fresh store whose prompt is observable, or `null` where the platform asks the person itself (Keychain). */
    open fun createProtected(): ProtectedStoreHarness? = null

    private val policy = SecretPolicy(requireUserPresence = false)
    private val a = InstanceId("a")
    private val b = InstanceId("b")

    suspend fun runAll() {
        aStoredSecretIsReadBack()
        aMissingSecretIsReportedAsMissing()
        deleteRemovesOnlyThatKey()
        deleteNamespaceRemovesOnlyThatInstance()
        namespacesListsEveryInstanceThatHoldsASecret()
        anUnprotectedOrMissingSecretIsNeverAttested()
        ProtectedSecretStoreCases(::createProtected).runAll()
    }

    private suspend fun SecretStore.read(key: SecretKey): SecretReadResult = get(key, "contract test")

    private suspend fun aStoredSecretIsReadBack() {
        val store = create()
        val key = SecretKey(a, SecretName.BoardToken)
        check(store.put(key, Secret.of(TestData.TOKEN), policy) == SecretWriteResult.Stored) { "put must store" }
        val found = store.read(key)
        check(found is SecretReadResult.Found && found.secret == Secret.of(TestData.TOKEN)) { "get must return it" }
    }

    private suspend fun aMissingSecretIsReportedAsMissing() {
        check(create().read(SecretKey(a, SecretName.BoardToken)) == SecretReadResult.Missing) { "missing key" }
    }

    /**
     * A read attests that the person was authenticated only when this very read opened a protected secret. An
     * unprotected secret (app lock turned on after pairing) and a missing one attest nothing, so the app lock never
     * accepts them as an unlock (ADR-146).
     */
    private suspend fun anUnprotectedOrMissingSecretIsNeverAttested() {
        val store = create()
        val token = SecretKey(a, SecretName.BoardToken)
        check(store.readAttested(token, "contract test") == AttestedReadResult.NotAttested) { "missing attests nothing" }
        store.put(token, Secret.of("one"), policy)
        check(store.readAttested(token, "contract test") == AttestedReadResult.NotAttested) {
            "an unprotected secret attests nothing"
        }
        check(store.read(token) is SecretReadResult.Found) { "an attested read changes nothing" }
    }

    private suspend fun deleteRemovesOnlyThatKey() {
        val store = create()
        val token = SecretKey(a, SecretName.BoardToken)
        val password = SecretKey(a, SecretName.ClientCertificatePassword)
        store.put(token, Secret.of("one"), policy)
        store.put(password, Secret.of("two"), policy)
        store.delete(token)
        check(store.read(token) == SecretReadResult.Missing) { "delete must remove the key" }
        check(store.read(password) is SecretReadResult.Found) { "delete must keep the other keys" }
    }

    private suspend fun namespacesListsEveryInstanceThatHoldsASecret() {
        val store = create()
        check(store.namespaces().isEmpty()) { "an empty store lists no namespace" }
        store.put(SecretKey(a, SecretName.BoardToken), Secret.of("x"), policy)
        store.put(SecretKey(a, SecretName.ClientCertificatePassword), Secret.of("x"), policy)
        store.put(SecretKey(b, SecretName.BoardToken), Secret.of("y"), policy)
        check(store.namespaces() == setOf(a, b)) { "namespaces must list each instance once" }
        store.deleteNamespace(a)
        check(store.namespaces() == setOf(b)) { "a deleted namespace is no longer listed" }
        store.delete(SecretKey(b, SecretName.BoardToken))
        check(store.namespaces().isEmpty()) { "a namespace without secrets is not listed" }
    }

    private suspend fun deleteNamespaceRemovesOnlyThatInstance() {
        val store = create()
        SecretName.entries.forEach { store.put(SecretKey(a, it), Secret.of("x"), policy) }
        store.put(SecretKey(b, SecretName.BoardToken), Secret.of("y"), policy)
        store.deleteNamespace(a)
        SecretName.entries.forEach { check(store.read(SecretKey(a, it)) == SecretReadResult.Missing) { "ns delete" } }
        check(store.read(SecretKey(b, SecretName.BoardToken)) is SecretReadResult.Found) { "other instance kept" }
    }
}
