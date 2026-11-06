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

/** Real-Keystore stores with their own record directory and key aliases, so tests never see each other. */
internal class DeviceStoreFixture {
    val context: Context = InstrumentationRegistry.getInstrumentation().context
    val prefixes = mutableListOf<String>()
    private val roots = mutableListOf<File>()
    val key = SecretKey(InstanceId("a"), SecretName.BoardToken)
    val open = SecretPolicy(requireUserPresence = false)

    fun freshDirectory(): File = File(context.noBackupFilesDir, "test-${UUID.randomUUID()}").also { roots += it }

    fun newStore(directory: File = freshDirectory()): Pair<KeystoreSecretStore, AndroidKeystoreBackend> {
        val prefix = "pafm.test.${UUID.randomUUID()}/"
        prefixes += prefix
        val backend = AndroidKeystoreBackend(prefix)
        val store = KeystoreSecretStore(backend, RecordFiles(directory), { _, _ -> null }, "protect")
        return store to backend
    }

    fun deleteTestKeys() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore
            .aliases()
            .toList()
            .filter { alias ->
                prefixes.any { alias.startsWith(it) }
            }.forEach { keyStore.deleteEntry(it) }
    }

    fun cleanUp() {
        deleteTestKeys()
        roots.forEach { it.deleteRecursively() }
    }
}
