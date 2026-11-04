package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKeyFactory
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import javax.crypto.SecretKey as JceSecretKey

/** The real Android Keystore and real record files on a device or emulator (AND-001). */
@RunWith(AndroidJUnit4::class)
class KeystoreRecordDeviceTest {
    private val f = DeviceStoreFixture()
    private val context = f.context
    private val prefixes = f.prefixes
    private val key = f.key
    private val open = f.open

    private fun newStore(directory: File = f.freshDirectory()) = f.newStore(directory)

    private fun freshDirectory() = f.freshDirectory()

    @After
    fun cleanUp() = f.cleanUp()

    @Test
    fun aRecordWithFlippedBytesReadsAsMissing() =
        runBlocking {
            val directory = freshDirectory()
            val (store, _) = newStore(directory)
            store.put(key, Secret.of(TestData.TOKEN), open)
            val file = directory.listFiles().orEmpty().single()
            val bytes = file.readBytes()
            for (index in listOf(bytes.size - 1, bytes.size / 2, HEADER_BYTE)) {
                val damaged = bytes.copyOf().also { it[index] = (it[index].toInt() xor FLIP).toByte() }
                file.writeBytes(damaged)
                assertEquals(SecretReadResult.Missing, store.get(key, "test"), "flipped byte $index")
            }
            file.writeBytes(bytes.copyOf(bytes.size / 2))
            assertEquals(SecretReadResult.Missing, store.get(key, "test"), "truncated record")
        }

    @Test
    fun aRecordRestoredWithoutItsKeyReadsAsMissing() =
        runBlocking {
            val (store, _) = newStore()
            store.put(key, Secret.of(TestData.TOKEN), open)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore
                .aliases()
                .toList()
                .filter { alias ->
                    prefixes.any { alias.startsWith(it) }
                }.forEach { keyStore.deleteEntry(it) }
            assertEquals(SecretReadResult.Missing, store.get(key, "test"))
        }

    @Test
    fun namespacesListsTheFactoriesFromTheRecordFilesWithoutUsingAnyKey() =
        runBlocking {
            val (store, _) = newStore()
            store.put(key, Secret.of("one"), open)
            store.put(SecretKey(InstanceId("b"), SecretName.BoardToken), Secret.of("two"), open)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore
                .aliases()
                .toList()
                .filter { alias ->
                    prefixes.any { alias.startsWith(it) }
                }.forEach { keyStore.deleteEntry(it) }
            assertEquals(setOf(InstanceId("a"), InstanceId("b")), store.namespaces())
        }

    @Test
    fun deleteNamespaceRemovesOnlyThatFactorysFilesAndKeys() =
        runBlocking {
            val directory = freshDirectory()
            val (store, backend) = newStore(directory)
            val other = SecretKey(InstanceId("ab"), SecretName.BoardToken)
            store.put(key, Secret.of("one"), open)
            store.put(other, Secret.of("two"), open)
            store.deleteNamespace(InstanceId("a"))
            assertEquals(SecretReadResult.Missing, store.get(key, "test"))
            assertEquals(Secret.of("two"), (store.get(other, "test") as SecretReadResult.Found).secret)
            assertEquals(1, directory.listFiles().orEmpty().size)
            assertEquals(emptyList(), backend.aliases("instance/a/"))
            assertEquals(1, backend.aliases("instance/ab/").size)
        }

    @Test
    fun aKeyThatIsAskedForAUserAuthenticationKeyWithoutALockScreenFailsClosed() =
        runBlocking {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            assumeFalse("needs a device without a secure lock screen", keyguard.isDeviceSecure)
            val (store, _) = newStore()
            assertEquals(
                SecretWriteResult.Failed,
                store.put(key, Secret.of("x"), SecretPolicy(requireUserPresence = true)),
            )
            assertEquals(SecretReadResult.Missing, store.get(key, "test"))
        }

    @Test
    fun aProtectedKeyIsAuthorisedForOneUseAndNotForAWindowOfSeconds() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assumeTrue("needs a secure lock screen", keyguard.isDeviceSecure)
        val prefix = "pafm.test.${UUID.randomUUID()}/".also { prefixes += it }
        val backend = AndroidKeystoreBackend(prefix)
        val sealed = backend.seal("k", byteArrayOf(1), byteArrayOf(2), protection = true)
        assertTrue(sealed is SealOutcome.Sealed || sealed is SealOutcome.NeedsAuthentication, "got $sealed")
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aesKey = keyStore.getKey(prefix + "k", null) as JceSecretKey
        val info =
            SecretKeyFactory
                .getInstance(
                    aesKey.algorithm,
                    "AndroidKeyStore",
                ).getKeySpec(aesKey, KeyInfo::class.java)
        assertEquals(
            0,
            (info as KeyInfo).userAuthenticationValidityDurationSeconds,
            "a validity window lets any recent unlock or other app's authentication satisfy a protected read",
        )
    }

    @Test
    fun theBackendSealsAndOpensAndRejectsAWrongAssociatedData() {
        val backend = AndroidKeystoreBackend("pafm.test.${UUID.randomUUID()}/".also { prefixes += it })
        val sealed = assertIs<SealOutcome.Sealed>(backend.seal("k", "hello".encodeToByteArray(), byteArrayOf(1), false))
        val opened = assertIs<OpenOutcome.Opened>(backend.open("k", sealed.iv, sealed.ciphertext, byteArrayOf(1)))
        assertContentEquals("hello".encodeToByteArray(), opened.plaintext)
        assertEquals(OpenOutcome.Undecryptable, backend.open("k", sealed.iv, sealed.ciphertext, byteArrayOf(2)))
        assertEquals(OpenOutcome.KeyMissing, backend.open("absent", sealed.iv, sealed.ciphertext, byteArrayOf(1)))
        assertNotNull(sealed.iv)
    }

    private companion object {
        const val FLIP = 0x55
        const val HEADER_BYTE = 8
    }
}
