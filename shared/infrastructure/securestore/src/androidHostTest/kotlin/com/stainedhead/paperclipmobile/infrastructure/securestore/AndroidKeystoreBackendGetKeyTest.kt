package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import kotlin.test.Test

/** R-010: `KeyStore.getKey` can throw; the read mapping of the port still holds (ADR-123, ADR-139). */
class AndroidKeystoreBackendGetKeyTest {
    private val iv = ByteArray(IV_BYTES)
    private val ciphertext = ByteArray(TAG_BYTES)

    private fun refusing(failure: GeneralSecurityException) = AndroidKeystoreBackend("t/", loadKey = { throw failure })

    @Test
    fun `R-010 open maps an unrecoverable key to invalidated`() {
        refusing(UnrecoverableKeyException()).open("a:u", iv, ciphertext, byteArrayOf()) shouldBe
            OpenOutcome.Invalidated
    }

    @Test
    fun `R-010 open maps a key store failure to unreadable`() {
        refusing(KeyStoreException()).open("a:u", iv, ciphertext, byteArrayOf()) shouldBe OpenOutcome.Unreadable
    }

    @Test
    fun `R-010 open maps a missing algorithm to unreadable`() {
        refusing(NoSuchAlgorithmException()).open("a:u", iv, ciphertext, byteArrayOf()) shouldBe OpenOutcome.Unreadable
    }

    @Test
    fun `R-010 seal fails closed when the key cannot be loaded`() {
        val failures = listOf(UnrecoverableKeyException(), KeyStoreException(), NoSuchAlgorithmException())
        for (failure in failures) {
            refusing(failure).seal("a:u", byteArrayOf(1), byteArrayOf(2), protection = false) shouldBe
                SealOutcome.Failed
        }
    }

    @Test
    fun `R-010 the store reads a refused key as invalidated or denied, never as an exception`() =
        runTest {
            val f = StoreFixture()
            f.store.put(f.key, Secret.of("one"), f.open) shouldBe SecretWriteResult.Stored
            val unrecoverable =
                KeystoreSecretStore(refusing(UnrecoverableKeyException()), RecordFiles(f.dir), { _, _ -> null }, "w")
            val broken = KeystoreSecretStore(refusing(KeyStoreException()), RecordFiles(f.dir), { _, _ -> null }, "w")

            unrecoverable.get(f.key, "test") shouldBe SecretReadResult.Invalidated
            broken.get(f.key, "test") shouldBe SecretReadResult.Denied
        }

    private companion object {
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
