package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * R-002: the person's prompt can stay open for as long as they like. While it is, the store answers everything else,
 * and what the prompt was for is not applied to a secret that changed meanwhile.
 */
class KeystoreSecretStorePendingPromptTest {
    private val f = StoreFixture()

    /** The prompt is shown when [asked] completes and answers when [answer] does. */
    private val asked = CompletableDeferred<Unit>()
    private val answer = CompletableDeferred<Boolean>()

    private val store =
        KeystoreSecretStore(
            f.backend,
            RecordFiles(f.dir),
            { _, cipher ->
                asked.complete(Unit)
                val approved = answer.await()
                f.backend.authenticated = approved
                cipher.takeIf { approved }
            },
            "protect the key",
        )

    private suspend fun <T> quickly(block: suspend () -> T): T = withTimeout(TIMEOUT) { block() }

    private suspend fun seedProtected() {
        // Stored through the fixture's store, which approves at once.
        f.store.put(f.key, Secret.of("one"), f.protected) shouldBe SecretWriteResult.Stored
    }

    @Test
    fun `R-002 namespaces, delete and deleteNamespace complete while a protected read waits for the person`() =
        runBlocking<Unit> {
            seedProtected()
            val read = async { store.get(f.key, "unlock it") }
            quickly { asked.await() }
            quickly { store.namespaces() } shouldBe setOf(InstanceId("a"))
            quickly { store.delete(f.key) }
            quickly { store.deleteNamespace(InstanceId("a")) }
            answer.complete(false)
            quickly { read.await() } shouldBe SecretReadResult.Denied
        }

    @Test
    fun `R-002 a protected read does not return a secret that was replaced while the prompt was open`() =
        runBlocking<Unit> {
            seedProtected()
            val read = async { store.get(f.key, "unlock it") }
            quickly { asked.await() }
            quickly { store.put(f.key, Secret.of("two"), f.open) } shouldBe SecretWriteResult.Stored
            answer.complete(true)
            quickly { read.await() } shouldBe SecretReadResult.Denied
            (quickly { store.get(f.key, "again") } as SecretReadResult.Found).secret shouldBe Secret.of("two")
        }

    @Test
    fun `R-002 a protected write whose secret was deleted while the prompt was open does not bring it back`() =
        runBlocking<Unit> {
            val write = async { store.put(f.key, Secret.of("one"), f.protected) }
            quickly { asked.await() }
            quickly { store.delete(f.key) }
            answer.complete(true)
            quickly { write.await() } shouldBe SecretWriteResult.Failed
            quickly { store.get(f.key, "test") } shouldBe SecretReadResult.Missing
        }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
