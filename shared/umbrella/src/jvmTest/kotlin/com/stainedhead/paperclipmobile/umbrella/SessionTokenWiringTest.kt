package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.auth.SessionCachingSecretStore
import com.stainedhead.paperclipmobile.application.auth.SignedOutMarker
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** ADR-145: the graph puts the session cache above the device secret store, and the app's signals empty it. */
class SessionTokenWiringTest {
    private val key = SecretKey(InstanceId("inst-1"), SecretName.BoardToken)

    private class CountingStore(
        val inner: FakeSecretStore = FakeSecretStore(),
    ) : SecretStore by inner {
        var reads = 0

        override suspend fun get(
            key: SecretKey,
            reason: String,
        ): SecretReadResult {
            reads += 1
            return inner.get(key, reason)
        }
    }

    private fun graphOn(device: CountingStore): PaperclipGraph {
        device.inner.seed(key, "pcp_board_TESTONLYa")
        return PaperclipCore.createLiveGraph(false, FakePairingBrowser(), LiveTestPlatform(secretStore = device))
    }

    @Test
    fun `the graph reads the device store once for repeated reads`() =
        runTest {
            val device = CountingStore()
            val graph = graphOn(device)
            val secrets = graph.resolve<SecretStore>()

            secrets.shouldBeInstanceOf<SessionCachingSecretStore>()
            secrets.get(key, "test")
            secrets.get(key, "test")

            device.reads shouldBe 1
            graph.close()
        }

    @Test
    fun `going to the background empties the cache and coming back does not refill it`() =
        runTest {
            val device = CountingStore()
            val graph = graphOn(device)
            val secrets = graph.resolve<SecretStore>()
            secrets.get(key, "test")

            graph.lifecycle.setForeground(false)
            graph.lifecycle.setForeground(true)
            device.reads shouldBe 1

            secrets.get(key, "test")
            device.reads shouldBe 2
            graph.close()
        }

    @Test
    fun `returning to the foreground drops a token read while the app was in the background`() =
        runTest {
            val device = CountingStore()
            val graph = graphOn(device)
            val secrets = graph.resolve<SecretStore>()

            graph.lifecycle.setForeground(false)
            secrets.get(key, "test")
            graph.lifecycle.setForeground(true)
            secrets.get(key, "test")

            device.reads shouldBe 2
            graph.close()
        }

    @Test
    fun `a foreground signal while already in the foreground keeps the cache, as when a system sheet closes`() =
        runTest {
            val device = CountingStore()
            val graph = graphOn(device)
            val secrets = graph.resolve<SecretStore>()
            secrets.get(key, "test")

            graph.lifecycle.setForeground(true)
            secrets.get(key, "test")

            device.reads shouldBe 1
            graph.close()
        }

    @Test
    fun `a new app shell starts locked, so it starts with an empty cache`() =
        runTest {
            val device = CountingStore()
            val graph = graphOn(device)
            val secrets = graph.resolve<SecretStore>()
            secrets.get(key, "test")

            val shell = graph.createAppStateHolder()
            secrets.get(key, "test")

            device.reads shouldBe 2
            shell.close()
            graph.close()
        }

    @Test
    fun `a 401 seen by the signed-out marker empties the cached token`() =
        runTest {
            val device = CountingStore()
            val graph = graphOn(device)
            val secrets = graph.resolve<SecretStore>()
            secrets.get(key, "test")

            graph.resolve<SignedOutMarker>().noteIfUnauthorized(
                key.instanceId,
                DomainResult.Failure(DomainError.Unauthorized),
            )
            secrets.get(key, "test")

            device.reads shouldBe 2
            graph.close()
        }
}
