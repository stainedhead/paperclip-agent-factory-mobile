package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.contract.SecretStoreContract
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class KeystoreSecretStoreProtectionTest {
    private val f = StoreFixture()
    private val backend = f.backend
    private val store = f.store
    private val key = f.key
    private val open = f.open
    private val protected = f.protected
    private val dir = f.dir
    private val prompts = f.prompts

    private suspend fun SecretStore.read(key: SecretKey = f.key) = get(key, "test")

    private fun recordFiles() = f.recordFiles()

    @Test
    fun `AND-001 an invalidated key reads as invalidated and a new put heals it`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            backend.keys.values.forEach { it.invalidated = true }
            store.read() shouldBe SecretReadResult.Invalidated
            store.put(key, Secret.of("two"), open) shouldBe SecretWriteResult.Stored
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("two")
        }

    @Test
    fun `AND-001 a key the system cannot use right now is denied, not missing`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            backend.unreadable = true
            store.read() shouldBe SecretReadResult.Denied
            backend.unreadable = false
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("one")
        }

    @Test
    fun `AND-001 a protected read asks the person and returns the secret once approved`() =
        runTest {
            store.put(key, Secret.of("one"), protected) shouldBe SecretWriteResult.Stored
            backend.authenticated = false
            prompts.clear()
            (store.get(key, "unlock it") as SecretReadResult.Found).secret shouldBe Secret.of("one")
            prompts shouldBe listOf("unlock it")
        }

    @Test
    fun `R-001 an authentication another app just made does not satisfy a protected read`() =
        runTest {
            store.put(key, Secret.of("one"), protected) shouldBe SecretWriteResult.Stored
            prompts.clear()
            backend.authenticated = true // the person unlocked the phone or another app a moment ago
            f.answer = BiometricResult.Cancelled
            store.read() shouldBe SecretReadResult.Denied
            prompts.size shouldBe 1
        }

    @Test
    fun `R-001 two consecutive protected reads prompt twice`() =
        runTest {
            store.put(key, Secret.of("one"), protected) shouldBe SecretWriteResult.Stored
            prompts.clear()
            repeat(2) { (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("one") }
            prompts.size shouldBe 2
        }

    @Test
    fun `AND-001 every refusal of the prompt is denied`() =
        runTest {
            store.put(key, Secret.of("one"), protected)
            for (refusal in BiometricResult.entries.filter { it != BiometricResult.Approved }) {
                f.answer = refusal
                backend.authenticated = false
                store.read() shouldBe SecretReadResult.Denied
            }
        }

    @Test
    fun `AND-001 a protected write that the person refuses is failed and leaves the old value`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            f.answer = BiometricResult.Cancelled
            store.put(key, Secret.of("two"), protected) shouldBe SecretWriteResult.Failed
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("one")
        }

    @Test
    fun `AND-001 changing the protection moves to the other key and drops the old one`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            store.put(key, Secret.of("two"), protected) shouldBe SecretWriteResult.Stored
            backend.keys.values.map { it.protection } shouldContainExactly listOf(true)
            store.put(key, Secret.of("three"), open) shouldBe SecretWriteResult.Stored
            backend.keys.values.map { it.protection } shouldContainExactly listOf(false)
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("three")
        }

    @Test
    fun `AND-001 the store describes itself without a secret`() {
        store.toString() shouldBe "KeystoreSecretStore"
    }
}
