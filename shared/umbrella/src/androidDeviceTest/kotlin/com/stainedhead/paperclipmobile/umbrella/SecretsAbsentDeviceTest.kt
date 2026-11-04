package com.stainedhead.paperclipmobile.umbrella

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
import com.stainedhead.paperclipmobile.infrastructure.persistence.openAndroidPersistence
import com.stainedhead.paperclipmobile.infrastructure.securestore.KeystoreSecretStore
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The real Keystore store and the real SQLite file side by side: after a board key is stored for a factory that is also
 * in the database, the token appears in no file of the app (AND-001, acceptance criterion 5), the database included.
 */
@RunWith(AndroidJUnit4::class)
class SecretsAbsentDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val secretKey = SecretKey(InstanceId("a"), SecretName.BoardToken)

    @After
    fun cleanUp() {
        runBlocking {
            KeystoreSecretStore(
                context,
                { BiometricResult.Unavailable },
                "x",
            ).deleteNamespace(InstanceId("a"))
        }
        listOf("", "-wal", "-shm", "-journal").forEach { File(context.noBackupFilesDir, "paperclip.db$it").delete() }
        File(context.noBackupFilesDir, "secrets").deleteRecursively()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore
            .aliases()
            .toList()
            .filter { it.startsWith("pafm.secret/") }
            .forEach { keyStore.deleteEntry(it) }
    }

    @Test
    fun theBoardKeyIsInNoFileOfTheAppNotEvenTheDatabase() =
        runBlocking {
            val store = KeystoreSecretStore(context, { BiometricResult.Unavailable }, "protect")
            val persistence = openAndroidPersistence(context, FakeClock())
            persistence.instances.add(TestData.instance("a", "Home factory"))
            assertEquals(
                SecretWriteResult.Stored,
                store.put(secretKey, Secret.of(TestData.TOKEN), SecretPolicy(requireUserPresence = false)),
            )
            persistence.close()

            val needle = TestData.TOKEN.encodeToByteArray()
            val files =
                context.filesDir.parentFile!!
                    .walkTopDown()
                    .filter { it.isFile }
                    .toList()
            assertTrue(files.any { it.name == "paperclip.db" }, "the database must have been scanned")
            assertTrue(files.any { it.parentFile?.name == "secrets" }, "the Keystore record must have been scanned")
            files.forEach { file ->
                assertFalse(file.readBytes().containsSubsequence(needle), "the token is in ${file.path}")
            }
            assertTrue((store.get(secretKey, "test") as SecretReadResult.Found).secret == Secret.of(TestData.TOKEN))
        }

    @Test
    fun theBoardKeyIsNotInTheLogAfterItIsStoredAndRead() =
        runBlocking {
            val store = KeystoreSecretStore(context, { BiometricResult.Unavailable }, "protect")
            assertEquals(
                SecretWriteResult.Stored,
                store.put(secretKey, Secret.of(TestData.TOKEN), SecretPolicy(requireUserPresence = false)),
            )
            store.get(secretKey, "test")

            val log =
                Runtime
                    .getRuntime()
                    .exec(arrayOf("logcat", "-d"))
                    .inputStream
                    .readBytes()
            assertTrue(log.isNotEmpty(), "the log must have been read")
            assertFalse(log.containsSubsequence(TestData.TOKEN.encodeToByteArray()), "the token is in the log")
        }

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean =
        (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
}
