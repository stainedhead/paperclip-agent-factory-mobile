@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.AttestedReadResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** The board token is read from the device once per unlock session and per factory (ADR-145). */
class SessionCachingSecretStoreTest {
    private val a = InstanceId("a")
    private val b = InstanceId("b")
    private val tokenA = SecretKey(a, SecretName.BoardToken)
    private val tokenB = SecretKey(b, SecretName.BoardToken)
    private val certificate = SecretKey(a, SecretName.ClientCertificate)
    private val plain = SecretPolicy(requireUserPresence = false)

    /** The device store as the cache sees it: every read is counted, and a read can wait for a gate (a prompt). */
    private class CountingStore(
        val inner: FakeSecretStore = FakeSecretStore(),
    ) : SecretStore by inner {
        var reads = 0
        var gate: CompletableDeferred<Unit>? = null

        var attested = 0

        override suspend fun readAttested(
            key: SecretKey,
            reason: String,
        ): AttestedReadResult {
            attested += 1
            val answer = inner.readAttested(key, reason)
            gate?.await()
            return answer
        }

        override suspend fun get(
            key: SecretKey,
            reason: String,
        ): SecretReadResult {
            reads += 1
            // The device decides the answer when the read starts; the prompt (the gate) only delays delivering it.
            val answer = inner.get(key, reason)
            gate?.await()
            return answer
        }
    }

    private fun setup(): Pair<CountingStore, SessionCachingSecretStore> {
        val device = CountingStore()
        device.inner.seed(tokenA, "pcp_board_TESTONLYa")
        device.inner.seed(tokenB, "pcp_board_TESTONLYb")
        device.inner.seed(certificate, "cert")
        return device to SessionCachingSecretStore(device)
    }

    private suspend fun SecretStore.token(key: SecretKey): String? =
        (get(key, "test") as? SecretReadResult.Found)?.secret?.reveal()

    @Test
    fun `the second read is served from memory and each caller gets its own copy`() =
        runTest {
            val (device, cache) = setup()

            val first = cache.get(tokenA, "test") as SecretReadResult.Found
            first.secret.wipe()
            val second = cache.get(tokenA, "test") as SecretReadResult.Found
            second.secret.wipe()
            val third = cache.get(tokenA, "test") as SecretReadResult.Found

            third.secret.reveal() shouldBe "pcp_board_TESTONLYa"
            device.reads shouldBe 1
        }

    @Test
    fun `only the board token is cached`() =
        runTest {
            val (device, cache) = setup()

            cache.get(certificate, "test")
            cache.get(certificate, "test")

            device.reads shouldBe 2
        }

    @Test
    fun `one factory never gets another factory's token`() =
        runTest {
            val (device, cache) = setup()

            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            cache.token(tokenB) shouldBe "pcp_board_TESTONLYb"
            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            cache.token(tokenB) shouldBe "pcp_board_TESTONLYb"

            device.reads shouldBe 2
        }

    @Test
    fun `concurrent reads of one factory ask the device once`() =
        runTest {
            val (device, cache) = setup()
            device.gate = CompletableDeferred()

            val calls = (1..5).map { async { cache.token(tokenA) } }
            runCurrent()
            device.gate?.complete(Unit)

            calls.awaitAll() shouldBe List(5) { "pcp_board_TESTONLYa" }
            device.reads shouldBe 1
        }

    @Test
    fun `concurrent reads of two factories do not wait for each other's prompt`() =
        runTest {
            val (device, cache) = setup()
            device.gate = CompletableDeferred()

            val calls = listOf(async { cache.token(tokenA) }, async { cache.token(tokenB) })
            runCurrent()

            device.reads shouldBe 2
            device.gate?.complete(Unit)
            calls.awaitAll() shouldBe listOf("pcp_board_TESTONLYa", "pcp_board_TESTONLYb")
        }

    @Test
    fun `callers that waited on a declined prompt share the refusal and the next call asks again`() =
        runTest {
            val (device, cache) = setup()
            device.inner.denyReads = true
            device.gate = CompletableDeferred()

            val calls = (1..4).map { async { cache.get(tokenA, "test") } }
            runCurrent()
            device.gate?.complete(Unit)

            calls.awaitAll() shouldBe List(4) { SecretReadResult.Denied }
            device.reads shouldBe 1

            device.inner.denyReads = false
            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            device.reads shouldBe 2
        }

    @Test
    fun `a refusal produced before a re-pair is not handed to a caller that queued before it`() =
        runTest {
            val (device, cache) = setup()
            device.inner.invalidated = true
            device.gate = CompletableDeferred()

            val first = async { cache.get(tokenA, "test") }
            runCurrent()
            val queued = async { cache.get(tokenA, "test") }
            runCurrent()
            device.inner.invalidated = false
            cache.put(tokenA, Secret.of("pcp_board_TESTONLYnew"), plain)
            device.gate?.complete(Unit)
            advanceUntilIdle()

            first.await() shouldBe SecretReadResult.Invalidated
            device.gate = null
            (queued.await() as SecretReadResult.Found).secret.reveal() shouldBe "pcp_board_TESTONLYnew"
        }

    @Test
    fun `a refused or invalidated or missing key is never cached`() =
        runTest {
            val (device, cache) = setup()

            device.inner.denyReads = true
            cache.get(tokenA, "test") shouldBe SecretReadResult.Denied
            device.inner.denyReads = false
            device.inner.invalidated = true
            cache.get(tokenA, "test") shouldBe SecretReadResult.Invalidated
            device.inner.invalidated = false
            cache.get(SecretKey(InstanceId("none"), SecretName.BoardToken), "test") shouldBe SecretReadResult.Missing

            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            device.reads shouldBe 4
        }

    @Test
    fun `a cancelled read caches nothing and the next caller reads again`() =
        runTest {
            val (device, cache) = setup()
            device.gate = CompletableDeferred()

            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { cache.token(tokenA) }
            cancelled.cancel()
            runCurrent()

            device.gate = null
            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            device.reads shouldBe 2
        }

    @Test
    fun `a waiter behind a cancelled read performs its own read`() =
        runTest {
            val (device, cache) = setup()
            device.gate = CompletableDeferred()

            val first = async { cache.token(tokenA) }
            runCurrent()
            val second = async { cache.token(tokenA) }
            runCurrent()
            first.cancel()
            device.gate = null
            runCurrent()

            second.await() shouldBe "pcp_board_TESTONLYa"
            device.reads shouldBe 2
        }

    @Test
    fun `put delete and deleteNamespace each drop the cached token of that factory only`() =
        runTest {
            val (device, cache) = setup()
            cache.token(tokenA)
            cache.token(tokenB)

            cache.put(tokenA, Secret.of("pcp_board_TESTONLYnew"), plain) shouldBe SecretWriteResult.Stored
            cache.token(tokenA) shouldBe "pcp_board_TESTONLYnew"
            cache.token(tokenB) shouldBe "pcp_board_TESTONLYb"
            device.reads shouldBe 3

            cache.delete(tokenA)
            cache.get(tokenA, "test") shouldBe SecretReadResult.Missing
            device.reads shouldBe 4

            cache.token(tokenB)
            cache.deleteNamespace(b)
            cache.get(tokenB, "test") shouldBe SecretReadResult.Missing
            device.reads shouldBe 5
        }

    @Test
    fun `a failed put still drops the cached token`() =
        runTest {
            val (device, cache) = setup()
            cache.token(tokenA)
            device.inner.failWrites = true

            cache.put(tokenA, Secret.of("pcp_board_TESTONLYnew"), plain) shouldBe SecretWriteResult.Failed

            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            device.reads shouldBe 2
        }

    @Test
    fun `forget drops one factory and forgetAll drops every factory`() =
        runTest {
            val (device, cache) = setup()
            cache.token(tokenA)
            cache.token(tokenB)

            cache.forget(a)
            cache.token(tokenA)
            cache.token(tokenB)
            device.reads shouldBe 3

            cache.forgetAll()
            cache.token(tokenA)
            cache.token(tokenB)
            device.reads shouldBe 5
        }

    @Test
    fun `a read that started before a re-pair does not bring the old token back`() =
        runTest {
            val (device, cache) = setup()
            device.gate = CompletableDeferred()

            val stale = async { cache.token(tokenA) }
            runCurrent()
            device.inner.seed(tokenA, "pcp_board_TESTONLYnew")
            cache.put(tokenA, Secret.of("pcp_board_TESTONLYnew"), plain)
            device.gate?.complete(Unit)
            advanceUntilIdle()
            stale.await()

            device.gate = null
            cache.token(tokenA) shouldBe "pcp_board_TESTONLYnew"
            device.reads shouldBe 2
        }

    @Test
    fun `a read that started before the app went to the background is not kept`() =
        runTest {
            val (device, cache) = setup()
            device.gate = CompletableDeferred()

            val inFlight = async { cache.token(tokenA) }
            runCurrent()
            cache.forgetAll()
            device.gate?.complete(Unit)
            advanceUntilIdle()
            inFlight.await()

            device.gate = null
            cache.token(tokenA)
            device.reads shouldBe 2
        }

    private suspend fun SecretStore.attestedToken(key: SecretKey): String? =
        (readAttested(key, "unlock") as? AttestedReadResult.Authenticated)?.secret?.reveal()

    @Test
    fun `an attested read goes to the device even when the token is cached and seeds the cache`() =
        runTest {
            val (device, cache) = setup()
            device.inner.seed(tokenA, "pcp_board_TESTONLYa", protected = true)
            cache.token(tokenA)
            device.reads shouldBe 1

            cache.attestedToken(tokenA) shouldBe "pcp_board_TESTONLYa"
            device.attested shouldBe 1

            cache.forgetAll()
            cache.attestedToken(tokenA) shouldBe "pcp_board_TESTONLYa"
            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            device.reads shouldBe 1
            device.attested shouldBe 2
        }

    @Test
    fun `a warm cache never attests an unprotected device token`() =
        runTest {
            val (device, cache) = setup()
            cache.token(tokenA)

            cache.readAttested(tokenA, "unlock") shouldBe AttestedReadResult.NotAttested
            device.attested shouldBe 1
        }

    @Test
    fun `a denied or invalidated or unattested read seeds nothing`() =
        runTest {
            val (device, cache) = setup()
            device.inner.seed(tokenA, "pcp_board_TESTONLYa", protected = true)

            device.inner.denyReads = true
            cache.readAttested(tokenA, "unlock") shouldBe AttestedReadResult.Denied
            device.inner.denyReads = false
            device.inner.invalidated = true
            cache.readAttested(tokenA, "unlock") shouldBe AttestedReadResult.Invalidated
            device.inner.invalidated = false
            cache.readAttested(tokenB, "unlock") shouldBe AttestedReadResult.NotAttested

            cache.token(tokenA) shouldBe "pcp_board_TESTONLYa"
            cache.token(tokenB) shouldBe "pcp_board_TESTONLYb"
            device.reads shouldBe 2
        }

    @Test
    fun `a read that waits behind an attested read shares its prompt`() =
        runTest {
            val (device, cache) = setup()
            device.inner.seed(tokenA, "pcp_board_TESTONLYa", protected = true)
            device.gate = CompletableDeferred()

            val unlock = async { cache.attestedToken(tokenA) }
            runCurrent()
            val refresh = (1..3).map { async { cache.token(tokenA) } }
            runCurrent()
            device.gate?.complete(Unit)

            unlock.await() shouldBe "pcp_board_TESTONLYa"
            refresh.awaitAll() shouldBe List(3) { "pcp_board_TESTONLYa" }
            device.attested shouldBe 1
            device.reads shouldBe 0
        }

    @Test
    fun `an attested read that began before a clear is not kept`() =
        runTest {
            val (device, cache) = setup()
            device.inner.seed(tokenA, "pcp_board_TESTONLYa", protected = true)
            device.gate = CompletableDeferred()

            val inFlight = async { cache.attestedToken(tokenA) }
            runCurrent()
            cache.forgetAll()
            device.gate?.complete(Unit)
            advanceUntilIdle()
            inFlight.await()

            device.gate = null
            cache.token(tokenA)
            device.reads shouldBe 1
        }

    /** A store written before the attested read existed: it overrides nothing, so it can never attest. */
    private class BareStore : SecretStore {
        override suspend fun put(
            key: SecretKey,
            value: Secret,
            policy: SecretPolicy,
        ) = SecretWriteResult.Failed

        override suspend fun get(
            key: SecretKey,
            reason: String,
        ) = SecretReadResult.Found(Secret.of("x"))

        override suspend fun delete(key: SecretKey) = Unit

        override suspend fun deleteNamespace(instanceId: InstanceId) = Unit

        override suspend fun namespaces(): Set<InstanceId> = emptySet()
    }

    @Test
    fun `a store that does not implement the attested read never attests`() =
        runTest {
            SessionCachingSecretStore(BareStore()).readAttested(tokenA, "unlock") shouldBe
                AttestedReadResult.NotAttested
        }

    @Test
    fun `namespaces is passed through`() =
        runTest {
            val (_, cache) = setup()

            cache.namespaces() shouldBe setOf(a, b)
        }
}
