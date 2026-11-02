package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.HealthGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.presentation.FirstRunStep
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.io.path.createTempDirectory
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

    private fun orphanSecretStore() =
        FakeSecretStore().also { it.seed(SecretKey(InstanceId("lost"), SecretName.BoardToken), TestData.TOKEN) }

    @Test
    fun `F-B-2 a recreated database is announced and the secrets of the lost factories are swept`() {
        val file = createTempDirectory("pafm-live").toFile().resolve("paperclip.db")
        file.writeText("this is not a sqlite database ".repeat(200))
        val secrets = orphanSecretStore()

        val graph = graphOn(LiveTestPlatform(file = file, secretStore = secrets))

        graph.startupNotice shouldBe StartupNotice(databaseRecreated = true, orphanSecretsRemoved = 1)
        secrets.paths shouldBe emptySet()
        graph.close()
    }

    private fun markedDatabase(): java.io.File {
        val file = createTempDirectory("pafm-live").toFile().resolve("paperclip.db")
        file.writeText("this is not a sqlite database ".repeat(200))
        return file
    }

    @Test
    fun `F-R-1 a launch after a wipe whose sweep never ran still announces and sweeps, once`() {
        val file = markedDatabase()
        // First launch: the wipe happened, then the process died before the sweep (the platform is simply dropped).
        LiveTestPlatform(file = file).persistence.close()
        val secrets = orphanSecretStore()

        val second = graphOn(LiveTestPlatform(file = file, secretStore = secrets))
        second.startupNotice shouldBe StartupNotice(databaseRecreated = true, orphanSecretsRemoved = 1)
        secrets.paths shouldBe emptySet()
        second.close()

        val third = graphOn(LiveTestPlatform(file = file, secretStore = orphanSecretStore()))
        third.startupNotice shouldBe StartupNotice()
        third.close()
    }

    @Test
    fun `F-R-1 a sweep that failed keeps the marker so the next launch tries again`() {
        val file = markedDatabase()
        val secrets = orphanSecretStore().also { it.failNamespaceDeletes += InstanceId("lost") }

        graphOn(LiveTestPlatform(file = file, secretStore = secrets)).close()
        secrets.failNamespaceDeletes.clear()

        val next = graphOn(LiveTestPlatform(file = file, secretStore = secrets))
        next.startupNotice shouldBe StartupNotice(databaseRecreated = true, orphanSecretsRemoved = 1)
        secrets.paths shouldBe emptySet()
        next.close()
    }

    @Test
    fun `F-B-2 an intact database announces nothing and leaves the secret store alone`() {
        val secrets = orphanSecretStore()

        val graph = graphOn(LiveTestPlatform(secretStore = secrets))

        graph.startupNotice shouldBe StartupNotice(databaseRecreated = false, orphanSecretsRemoved = 0)
        secrets.paths.size shouldBe 1
        graph.close()
    }

    @Test
    fun `every network and persistence port the graph resolves is a real adapter, none is a fake`() {
        val graph = graphOn(LiveTestPlatform())

        // The device ports (clock, secret store, biometrics, connectivity) come from the test platform on purpose.
        val bound =
            listOf(
                graph.resolve<HealthGateway>(),
                graph.resolve<AuthGateway>(),
                graph.resolve<AttentionGateway>(),
                graph.resolve<DecisionGateway>(),
                graph.resolve<ApprovalGateway>(),
                graph.resolve<BudgetGateway>(),
                graph.resolve<IssueGateway>(),
                graph.resolve<InteractionGateway>(),
                graph.resolve<AgentGateway>(),
                graph.resolve<RunGateway>(),
                graph.resolve<DashboardGateway>(),
                graph.resolveRaw<AttentionGateway>(),
                graph.resolveRaw<DecisionGateway>(),
                graph.resolveRaw<ApprovalGateway>(),
                graph.resolveRaw<BudgetGateway>(),
                graph.resolveRaw<IssueGateway>(),
                graph.resolveRaw<InteractionGateway>(),
                graph.resolveRaw<AgentGateway>(),
                graph.resolveRaw<RunGateway>(),
                graph.resolveRaw<DashboardGateway>(),
                graph.resolve<InstanceRepository>(),
                graph.resolve<AppSettingsStore>(),
                graph.resolve<AttentionCache>(),
                graph.resolve<LiveEvents>(),
            ).map { it::class.qualifiedName.orEmpty() }

        bound.size shouldBe EXPECTED_PORTS
        bound.filter { it.contains(".testing.") || it.contains("Fake") } shouldBe emptyList()
        // Real adapters, the signed-out decorator on top of them, and the polling-only live events of the umbrella.
        bound.filterNot {
            it.contains(".infrastructure.") || it.contains(".umbrella.") ||
                it.contains(".application.auth.SignedOutAware")
        } shouldBe emptyList()
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

    @Test
    fun `F-B-4 closing the graph stops the dependency container before it lets go of the resources`() {
        val order = mutableListOf<String>()
        val koin =
            koinApplication {
                modules(
                    demoPortsModule(),
                    useCaseModule(false),
                    presentationModule(),
                    module { single { ClosingProbe() } onClose { order += "container" } },
                )
            }.koin
        koin.get<ClosingProbe>()

        PaperclipGraph(koin, AutoCloseable { order += "resources" }).close()

        order shouldBe listOf("container", "resources")
    }

    @Test
    fun `F-B-4 a graph that cannot be built lets go of the platform and its database`() {
        val platform = LiveTestPlatform().also { it.failHttpClient = true }

        shouldThrow<IllegalStateException> { graphOn(platform) }

        platform.releases shouldBe 1
        runCatching { runBlocking { platform.persistence.instances.get(InstanceId("any")) } }.isFailure shouldBe true
    }
}

private const val EXPECTED_PORTS = 24

private class ClosingProbe
