package com.stainedhead.paperclipmobile.infrastructure.securestore

import android.app.KeyguardManager
import android.content.Context
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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
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
class KeystoreSecretStoreDeviceTest {
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
    fun theRealStoreObeysTheSecretStoreContract() {
        val contract =
            object : SecretStoreContract() {
                override fun create(): SecretStore = newStore().first
            }
        runBlocking { contract.runAll() }
    }

    @Test
    fun aSecretRoundTripsThroughTheKeystore() =
        runBlocking {
            val (store, _) = newStore()
            assertEquals(SecretWriteResult.Stored, store.put(key, Secret.of(TestData.TOKEN), open))
            assertEquals(Secret.of(TestData.TOKEN), (store.get(key, "test") as SecretReadResult.Found).secret)
        }

    @Test
    fun theKeyIsAnAes256KeyThatCannotBeExported() =
        runBlocking {
            val (store, _) = newStore()
            store.put(key, Secret.of(TestData.TOKEN), open)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = keyStore.aliases().toList().single { alias -> prefixes.any { alias.startsWith(it) } }
            val aesKey = keyStore.getKey(alias, null) as JceSecretKey
            assertEquals("AES", aesKey.algorithm)
            assertNull(aesKey.encoded, "the key material must not be readable")
        }

    @Test
    fun noPlaintextReachesAnyFileOrDatabaseOfTheApp() =
        runBlocking {
            val directory = freshDirectory()
            val (store, _) = newStore(directory)
            store.put(key, Secret.of(TestData.TOKEN), open)
            val needle = TestData.TOKEN.encodeToByteArray()
            val areas = listOf(context.filesDir.parentFile, context.noBackupFilesDir, context.cacheDir).filterNotNull()
            val scanned = areas.flatMap { area -> area.walkTopDown().filter { it.isFile }.toList() }
            assertTrue(scanned.any { it.parentFile == directory }, "the record file must be among the scanned files")
            scanned.forEach { file ->
                assertFalse(file.readBytes().containsSubsequence(needle), "plaintext found in ${file.name}")
            }
        }

    @Test
    fun theRecordsLiveInNoBackupFilesDir() =
        runBlocking {
            val directory = freshDirectory()
            val (store, _) = newStore(directory)
            store.put(key, Secret.of("x"), open)
            assertTrue(directory.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
            assertEquals(1, directory.listFiles().orEmpty().size)
        }

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean =
        (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
}
