package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * F-3 (ADR-151) on the live graph: the text the device secret store is given when an API call reads the board key is
 * the platform's `keyReadReason`, whatever the adapter, and the default is the English sentence, not an internal word.
 */
class LiveKeyReadReasonTest {
    private suspend fun readReasonsAfterOneCall(platformReason: String?): List<String> {
        val secrets = FakeSecretStore()
        val platform = LiveTestPlatform(secretStore = secrets).also { it.stub.withOneDecision() }
        platformReason?.let { platform.keyReadReason = it }
        val graph = PaperclipCore.createLiveGraph(false, ApprovingBrowser(platform.stub), platform)
        graph.createFirstRunStateHolder().completeFirstRun()
        val factory =
            graph
                .createFactoriesStateHolder()
                .state
                .awaitLive { it.rows.isNotEmpty() }
                .rows
                .single()
                .id
        // Leaving the foreground empties the session cache, so the next call reads the device store again.
        graph.lifecycle.setForeground(false)
        secrets.readReasons.clear()

        graph.resolve<AuthGateway>().me(InstanceId(factory))

        graph.close()
        return secrets.readReasons.toList()
    }

    @Test
    fun `the reason the secret store receives is the text the platform supplied`() =
        runTest {
            readReasonsAfterOneCall("Read the board key (injected)") shouldBe listOf("Read the board key (injected)")
        }

    @Test
    fun `a platform that supplies none gets the English default and never the internal literal`() =
        runTest {
            val reasons = readReasonsAfterOneCall(null)

            reasons shouldBe listOf("Read the board key to talk to the factory")
        }
}
