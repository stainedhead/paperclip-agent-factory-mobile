package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.contract.SecretStoreContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import platform.Security.errSecAuthFailed
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecItemNotFound
import platform.Security.errSecNotAvailable
import platform.Security.errSecParam
import platform.Security.errSecUserCanceled
import kotlin.test.Test

/** The shared contract against the store's decisions on an in-memory Keychain (the real one: Swift host tests). */
class KeychainSecretStoreContractTest : SecretStoreContract() {
    override fun create(): SecretStore = KeychainSecretStore(FakeKeychainBackend())

    @Test
    fun `AD-030 the keychain store obeys the secret store contract`() = runTest { runAll() }
}

class KeychainSecretStoreTest {
    private val backend = FakeKeychainBackend()
    private val store = KeychainSecretStore(backend)
    private val key = SecretKey(InstanceId("a"), SecretName.BoardToken)
    private val open = SecretPolicy(requireUserPresence = false)
    private val protected = SecretPolicy(requireUserPresence = true)

    private suspend fun SecretStore.read(key: SecretKey = this@KeychainSecretStoreTest.key) = get(key, "test")

    @Test
    fun `AD-030 an unprotected secret is stored as its utf8 bytes under the namespaced path`() =
        runTest {
            store.put(key, Secret.of(TestData.TOKEN), open) shouldBe SecretWriteResult.Stored
            backend.items.keys.toList() shouldBe listOf("instance/a/boardToken")
            backend.items.getValue("instance/a/boardToken").let {
                it.data.decodeToString() shouldBe TestData.TOKEN
                it.protection shouldBe Protection.None
            }
        }

    @Test
    fun `AD-030 writing again replaces the value and the protection`() =
        runTest {
            store.put(key, Secret.of("one"), open)
            store.put(key, Secret.of("two"), open) shouldBe SecretWriteResult.Stored
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("two")
            store.put(key, Secret.of("three"), protected) shouldBe SecretWriteResult.Stored
            backend.items.getValue("instance/a/boardToken").protection shouldBe Protection.BiometryCurrentSet
        }

    @Test
    fun `AD-030 a protected secret asks for the current biometric set and records that it is protected`() =
        runTest {
            store.put(key, Secret.of("v"), protected) shouldBe SecretWriteResult.Stored
            backend.items.getValue("instance/a/boardToken").protection shouldBe Protection.BiometryCurrentSet
            backend.items.containsKey("instance/a/boardToken#protected") shouldBe true
            backend.items.getValue("instance/a/boardToken#protected").protection shouldBe Protection.None
        }

    @Test
    fun `AD-030 without enrolled biometrics a protected secret falls back to user presence`() =
        runTest {
            backend.addFailures[Protection.BiometryCurrentSet] = errSecNotAvailable
            store.put(key, Secret.of("v"), protected) shouldBe SecretWriteResult.Stored
            backend.items.getValue("instance/a/boardToken").protection shouldBe Protection.UserPresence
        }

    @Test
    fun `AD-030 a protected secret that cannot be protected is refused and never stored without protection`() =
        runTest {
            backend.addFailures[Protection.BiometryCurrentSet] = errSecNotAvailable
            backend.addFailures[Protection.UserPresence] = errSecParam
            store.put(key, Secret.of("v"), protected) shouldBe SecretWriteResult.Failed
            backend.items.isEmpty() shouldBe true
            backend.addAttempts.none { it.second == Protection.None } shouldBe true
        }

    @Test
    fun `AD-030 an unprotected write that stale-marked an earlier protected secret clears the marker`() =
        runTest {
            store.put(key, Secret.of("v"), protected)
            store.put(key, Secret.of("w"), open)
            backend.items.keys.toList() shouldBe listOf("instance/a/boardToken")
        }

    @Test
    fun `AD-030 a failed write reports Failed`() =
        runTest {
            backend.addFailures[Protection.None] = errSecInteractionNotAllowed
            store.put(key, Secret.of("v"), open) shouldBe SecretWriteResult.Failed
        }

    @Test
    fun `AD-030 a missing item is Missing`() = runTest { store.read() shouldBe SecretReadResult.Missing }

    @Test
    fun `AD-030 a protected item that vanished from the keychain is Invalidated not Missing`() =
        runTest {
            store.put(key, Secret.of("v"), protected)
            backend.items.remove("instance/a/boardToken") // what a changed biometric set does to the item
            store.read() shouldBe SecretReadResult.Invalidated
        }

    @Test
    fun `AD-030 a locked device and a cancelled prompt and a failed prompt are Denied and change no state`() =
        runTest {
            store.put(key, Secret.of("v"), open)
            listOf(errSecInteractionNotAllowed, errSecUserCanceled, errSecAuthFailed, errSecParam).forEach {
                backend.readFailure = it
                store.read() shouldBe SecretReadResult.Denied
            }
            backend.readFailure = null
            (store.read() as SecretReadResult.Found).secret shouldBe Secret.of("v")
        }

    @Test
    fun `AD-030 when the invalidation marker cannot be checked the result is Denied never a guess`() =
        runTest {
            backend.existsFailure = errSecInteractionNotAllowed
            store.read() shouldBe SecretReadResult.Denied
        }

    @Test
    fun `AD-030 delete removes the secret and its marker`() =
        runTest {
            store.put(key, Secret.of("v"), protected)
            store.delete(key)
            backend.items.isEmpty() shouldBe true
            store.delete(key) // deleting what is not there is fine
        }

    @Test
    fun `AD-030 a delete the keychain refuses is not swallowed and its message holds no secret`() =
        runTest {
            store.put(key, Secret.of(TestData.TOKEN), open)
            backend.deleteFailure = errSecInteractionNotAllowed
            val error = shouldThrow<IllegalStateException> { store.delete(key) }
            error.message.orEmpty() shouldNotContain TestData.TOKEN
        }

    @Test
    fun `AD-030 deleteNamespace lists by prefix without reading values and removes only that instance`() =
        runTest {
            SecretName.entries.forEach { store.put(SecretKey(InstanceId("a"), it), Secret.of("x"), protected) }
            store.put(SecretKey(InstanceId("ab"), SecretName.BoardToken), Secret.of("y"), open)
            store.deleteNamespace(InstanceId("a"))
            backend.items.keys.toList() shouldBe listOf("instance/ab/boardToken")
            backend.listedPrefixes shouldBe listOf("instance/a/")
        }

    @Test
    fun `F-B-2 namespaces lists every instance with an item or a marker by names only`() =
        runTest {
            store.put(SecretKey(InstanceId("a"), SecretName.BoardToken), Secret.of("x"), protected)
            store.put(SecretKey(InstanceId("ab"), SecretName.BoardToken), Secret.of("y"), open)
            backend.items.remove("instance/a/boardToken")
            store.namespaces() shouldBe setOf(InstanceId("a"), InstanceId("ab"))
            backend.listedPrefixes.last() shouldBe "instance/"
        }

    @Test
    fun `F-B-2 a namespaces listing failure is not swallowed`() =
        runTest {
            backend.listFailure = errSecInteractionNotAllowed
            shouldThrow<IllegalStateException> { store.namespaces() }
        }

    @Test
    fun `AD-030 a deleteNamespace failure is not swallowed`() =
        runTest {
            store.put(key, Secret.of("v"), open)
            backend.listFailure = errSecInteractionNotAllowed
            shouldThrow<IllegalStateException> { store.deleteNamespace(InstanceId("a")) }
            backend.listFailure = null
            backend.deleteFailure = errSecInteractionNotAllowed
            shouldThrow<IllegalStateException> { store.deleteNamespace(InstanceId("a")) }
        }

    @Test
    fun `AD-030 the byte buffers that held a secret are zeroed after a write and after a read`() =
        runTest {
            store.put(key, Secret.of(TestData.TOKEN), open)
            store.read()
            backend.buffersHandedOut.size shouldBe 2
            backend.buffersHandedOut.all { buffer -> buffer.all { it == 0.toByte() } } shouldBe true
        }

    @Test
    fun `AD-030 no error and no printed form of the store contains the secret`() =
        runTest {
            backend.addFailures[Protection.None] = errSecParam
            store.put(key, Secret.of(TestData.TOKEN), open)
            store.toString() shouldNotContain TestData.TOKEN
            Secret.of(TestData.TOKEN).toString() shouldNotContain TestData.TOKEN
            backend.deleteFailure = errSecParam
            val error = shouldThrow<IllegalStateException> { store.delete(key) }
            error.message.orEmpty() shouldNotContain TestData.TOKEN
            backend.items.keys.none { TestData.TOKEN in it } shouldBe true
        }

    @Test
    fun `AD-030 only item not found counts as missing`() {
        mapReadFailure(errSecItemNotFound) shouldBe ReadFailure.NotFound
        listOf(errSecInteractionNotAllowed, errSecUserCanceled, errSecAuthFailed, errSecParam, errSecNotAvailable)
            .forEach { mapReadFailure(it) shouldBe ReadFailure.Unreadable }
    }
}
