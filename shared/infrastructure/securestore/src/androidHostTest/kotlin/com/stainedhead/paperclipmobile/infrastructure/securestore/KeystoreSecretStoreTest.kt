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
import com.stainedhead.paperclipmobile.testing.contract.ProtectedStoreHarness
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

class KeystoreSecretStoreContractTest : SecretStoreContract() {
    override fun createProtected(): ProtectedStoreHarness {
        val fixture = StoreFixture()
        return object : ProtectedStoreHarness() {
            override val store: SecretStore = fixture.store
            override val prompts: Int get() = fixture.prompts.size
            override var approve: Boolean
                get() = fixture.answer == BiometricResult.Approved
                set(value) {
                    fixture.answer = if (value) BiometricResult.Approved else BiometricResult.Cancelled
                }
        }
    }

    override fun create(): SecretStore =
        KeystoreSecretStore(FakeKeystoreBackend(), RecordFiles(tempDir()), { BiometricResult.Unavailable }, "write")

    @Test
    fun `AND-001 the keystore store obeys the secret store contract`() = runTest { runAll() }
}

class KeystoreSecretStoreTest {
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
    fun `AND-001 the value is stored encrypted and never as plaintext in a file`() =
        runTest {
            store.put(key, Secret.of(TestData.TOKEN), open) shouldBe SecretWriteResult.Stored
            recordFiles().size shouldBe 1
            recordFiles().forEach { String(it.readBytes(), Charsets.ISO_8859_1) shouldNotContain TestData.TOKEN }
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of(TestData.TOKEN)
        }

    @Test
    fun `AND-001 writing again replaces the value`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            store.put(key, Secret.of("two"), open) shouldBe SecretWriteResult.Stored
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("two")
            recordFiles().size shouldBe 1
        }

    @Test
    fun `AND-001 namespaces is unchanged by a failed put`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            backend.failNextSeal = true
            store.put(SecretKey(InstanceId("b"), SecretName.BoardToken), Secret.of("two"), open) shouldBe
                SecretWriteResult.Failed
            store.namespaces() shouldBe setOf(InstanceId("a"))
        }

    @Test
    fun `AND-001 a failed replacement keeps the old secret readable`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            backend.failNextSeal = true
            store.put(key, Secret.of("two"), open) shouldBe SecretWriteResult.Failed
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("one")
        }

    @Test
    fun `AND-001 namespaces lists names only and never uses a key`() =
        runTest {
            store.put(key, Secret.of("one"), protected)
            val calls = backend.openCalls to backend.sealCalls
            prompts.clear()
            store.namespaces() shouldBe setOf(InstanceId("a"))
            (backend.openCalls to backend.sealCalls) shouldBe calls
            prompts shouldBe emptyList()
        }

    @Test
    fun `AND-001 concurrent puts to one key leave one complete record`() =
        runTest {
            val values = (0 until WRITERS).map { "value-$it" }
            withContext(Dispatchers.Default) {
                values.map { value -> async { store.put(key, Secret.of(value), open) } }.awaitAll()
            }
            recordFiles().size shouldBe 1
            values shouldContain (store.read() as SecretReadResult.Found).secret.reveal()
        }

    @Test
    fun `AND-001 delete removes the record and the key`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            store.delete(key)
            store.read() shouldBe SecretReadResult.Missing
            recordFiles() shouldBe emptyList()
            backend.keys shouldBe emptyMap()
        }

    @Test
    fun `AND-001 deleteNamespace removes only that factory including a key left without a record`() =
        runTest {
            val ab = SecretKey(InstanceId("ab"), SecretName.BoardToken)
            store.put(key, Secret.of("one"), open)
            store.put(SecretKey(InstanceId("a"), SecretName.ClientCertificatePassword), Secret.of("two"), open)
            store.put(ab, Secret.of("three"), open)
            backend.keys["instance/a/clientCertificate:u"] = FakeKeystoreBackend.Key(false)
            store.deleteNamespace(InstanceId("a"))
            store.namespaces() shouldBe setOf(InstanceId("ab"))
            (store.read(ab) as SecretReadResult.Found).secret shouldBe Secret.of("three")
            backend.keys.keys.none { it.startsWith("instance/a/") } shouldBe true
            recordFiles().size shouldBe 1
        }

    private companion object {
        const val WRITERS = 24
    }
}
