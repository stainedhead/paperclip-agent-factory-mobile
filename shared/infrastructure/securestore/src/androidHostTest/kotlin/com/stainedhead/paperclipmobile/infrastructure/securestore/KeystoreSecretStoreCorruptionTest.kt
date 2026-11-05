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

class KeystoreSecretStoreCorruptionTest {
    private val f = StoreFixture()
    private val backend = f.backend
    private val store = f.store
    private val key = f.key
    private val open = f.open
    private val protected = f.protected
    private val dir = f.dir

    private suspend fun SecretStore.read(key: SecretKey = f.key) = get(key, "test")

    private fun recordFiles() = f.recordFiles()

    @Test
    fun `AND-001 a record with flipped bytes reads as missing, never as plaintext`() =
        runTest {
            store.put(key, Secret.of(TestData.TOKEN), open)
            val file = recordFiles().single()
            val bytes = file.readBytes()
            bytes[bytes.size - TAIL] = (bytes[bytes.size - TAIL].toInt() xor FLIP).toByte()
            file.writeBytes(bytes)
            store.read() shouldBe SecretReadResult.Missing
        }

    @Test
    fun `AND-001 a truncated record reads as missing`() =
        runTest {
            store.put(key, Secret.of(TestData.TOKEN), open)
            val file = recordFiles().single()
            val whole = file.readBytes()
            for (length in LENGTHS) {
                file.writeBytes(whole.copyOf(length))
                store.read() shouldBe SecretReadResult.Missing
            }
        }

    @Test
    fun `AND-001 a record restored without its key reads as missing`() =
        runTest {
            store.put(key, Secret.of(TestData.TOKEN), open)
            backend.keys.clear()
            store.read() shouldBe SecretReadResult.Missing
        }

    @Test
    fun `AND-001 a record copied to another name does not decrypt`() =
        runTest {
            val other = SecretKey(InstanceId("b"), SecretName.BoardToken)
            store.put(key, Secret.of(TestData.TOKEN), open)
            store.put(other, Secret.of("other"), open)
            val (first, second) = recordFiles()
            val (from, to) = if (first.readBytes().size >= second.readBytes().size) first to second else second to first
            from.copyTo(to, overwrite = true)
            val readBack = listOf(store.read(), store.read(other))
            readBack.count { it is SecretReadResult.Found } shouldBe 1
            readBack.count { it == SecretReadResult.Missing } shouldBe 1
        }

    private companion object {
        const val TAIL = 3
        const val FLIP = 0x55
        val LENGTHS = listOf(0, 1, 3, 5, 12)
    }
}
