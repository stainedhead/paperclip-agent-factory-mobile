package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** AD-040, AD-041: the live graph on a real database, with nothing from the fakes behind it. */
class LiveGraphTest {
    private fun graphOn(platform: LivePlatform) =
        PaperclipCore.createLiveGraph(allowInsecureLocal = false, FakePairingBrowser(), platform)

    @Test
    fun `AD-041 an empty database yields an empty factory list and the first-run flow, not demo data`() =
        runTest {
            val platform = LiveTestPlatform()
            val graph = graphOn(platform)

            val settings = graph.createSettingsStateHolder()
            val factories = graph.createFactoriesStateHolder()

            // The database answered (the default device name arrived) and it holds no factory: nothing was seeded.
            val loaded = settings.state.awaitLive { it.deviceName.isNotEmpty() }
            loaded.factories shouldBe emptyList()
            factories.state.value.isEmpty shouldBe true
            factories.state.value.rows shouldBe emptyList()
            platform.stub.requests shouldBe emptyList()
            graph
                .createFirstRunStateHolder()
                .state.value.step shouldBe FirstRunStep.Address
            settings.close()
            factories.close()
            graph.close()
        }

    @Test
    fun `every port the graph resolves is a real adapter, none comes from the testing module`() {
        val graph = graphOn(LiveTestPlatform())

        val bound =
            listOf(
                graph.resolve<AttentionGateway>(),
                graph.resolveRaw<AttentionGateway>(),
                graph.resolve<LiveEvents>(),
            ).map { it::class.qualifiedName.orEmpty() }

        bound.filter { it.contains(".testing.") } shouldBe emptyList()
        graph.close()
    }

    @Test
    fun `closing the graph closes the HTTP client and the database, and the platform once`() =
        runTest {
            val platform = LiveTestPlatform()
            val graph = graphOn(platform)
            platform.engines.size shouldBe 1
            val engineJob = platform.engines.single().coroutineContext[Job]!!
            engineJob.isActive shouldBe true

            graph.close()
            graph.close()

            engineJob.join()
            engineJob.isActive shouldBe false
            platform.clients.size shouldBe 1
            platform.releases shouldBe 1
            shouldThrow<Exception> { platform.persistence.instances.get(InstanceId("any")) }
        }

    @Test
    fun `closing the graph twice lets go of the platform once`() {
        val platform = LiveTestPlatform()
        val graph = graphOn(platform)

        graph.close()
        graph.close()

        platform.releases shouldBe 1
    }
}
