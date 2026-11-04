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

internal fun tempDir(): File = createTempDirectory("pafm-secrets").toFile().also { it.deleteOnExit() }

/** A store over the fake backend with a scripted authentication prompt, shared by the store tests. */
internal class StoreFixture {
    val backend = FakeKeystoreBackend()
    val dir = tempDir()
    var answer = BiometricResult.Approved
    val prompts = mutableListOf<String>()
    val key = SecretKey(InstanceId("a"), SecretName.BoardToken)
    val open = SecretPolicy(requireUserPresence = false)
    val protected = SecretPolicy(requireUserPresence = true)
    val store =
        KeystoreSecretStore(
            backend,
            RecordFiles(dir),
            { reason ->
                prompts += reason
                backend.authenticated = answer == BiometricResult.Approved
                answer
            },
            "protect the key",
        )

    suspend fun read(key: SecretKey = this.key) = store.get(key, "test")

    fun recordFiles(): List<File> = dir.listFiles().orEmpty().toList()
}
