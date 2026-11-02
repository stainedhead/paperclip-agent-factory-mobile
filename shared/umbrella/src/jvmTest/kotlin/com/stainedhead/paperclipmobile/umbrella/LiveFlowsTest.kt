package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.HealthMode
import com.stainedhead.paperclipmobile.infrastructure.api.ServerFixtures
import com.stainedhead.paperclipmobile.presentation.DecisionFinalKind
import com.stainedhead.paperclipmobile.presentation.DecisionIntent
import com.stainedhead.paperclipmobile.presentation.DetailPhase
import com.stainedhead.paperclipmobile.presentation.FactoriesIntent
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.FirstRunIntent
import com.stainedhead.paperclipmobile.presentation.InboxPhase
import com.stainedhead.paperclipmobile.presentation.ManageFactoriesIntent
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.presentation.SettingsIntent
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

/** AD-040, AD-041: real flows through the state holders of a live graph, on a real database and the pinned stub. */
class LiveFlowsTest {
    private fun newDatabaseFile(): File = File.createTempFile("pafm-live", ".db").also { it.deleteOnExit() }

    private fun graphOn(platform: LiveTestPlatform) =
        PaperclipCore.createLiveGraph(
            allowInsecureLocal = false,
            ApprovingBrowser(platform.stub),
            platform,
        )

    @Test
    fun `first run probe refuses a server in local_trusted mode and accepts an authenticated one`() =
        runTest {
            val platform = LiveTestPlatform()
            platform.stub.host(LIVE_HOST).health = HealthMode.LocalTrusted
            val graph = graphOn(platform)
            val firstRun = graph.createFirstRunStateHolder()

            firstRun.onIntent(FirstRunIntent.AddressChanged(LIVE_ADDRESS))
            firstRun.onIntent(FirstRunIntent.ProbeRequested)
            val refused = firstRun.state.awaitLive { it.probe == ProbeStatus.Refused }
            platform.stub.host(LIVE_HOST).health = HealthMode.Ready
            firstRun.onIntent(FirstRunIntent.AddressChanged(LIVE_ADDRESS))
            firstRun.onIntent(FirstRunIntent.ProbeRequested)
            val accepted = firstRun.state.awaitLive { it.probe == ProbeStatus.Accepted }

            refused.probeRefusal shouldBe ProbeRefusal.LocalTrusted
            accepted.exposure shouldNotBe null
            firstRun.close()
            graph.close()
        }

    @Test
    fun `the whole journey from first run to a decided decision and a removed factory`() =
        runTest {
            val platform = LiveTestPlatform(file = newDatabaseFile()).also { it.stub.withOneDecision() }
            val graph = graphOn(platform)
            val firstRun = graph.createFirstRunStateHolder()
            firstRun.completeFirstRun()

            val factories = graph.createFactoriesStateHolder()
            factories.state.awaitLive { it.rows.isNotEmpty() }
            factories.onIntent(FactoriesIntent.Refresh)
            val row =
                factories.state
                    .awaitLive {
                        it.rows
                            .singleOrNull()
                            ?.companies
                            ?.size == 2
                    }.rows
                    .single()
            row.name shouldBe LIVE_HOST
            row.status shouldBe FactoryStatusUi.SignedIn

            val inbox = graph.attention.createInboxStateHolder(row.id, ServerFixtures.COMPANY_ACME)
            val inboxState = inbox.state.awaitLive { it.phase == InboxPhase.Content }
            inboxState.sections.flatMap { it.items } shouldHaveSize 1

            val decision =
                graph.attention.createDecisionStateHolder(
                    row.id,
                    ServerFixtures.COMPANY_ACME,
                    LIVE_DECISION_ID,
                    DecisionHints(null, null, null),
                )
            decision.state.awaitLive { it.phase == DetailPhase.Content }
            decision.onIntent(DecisionIntent.OptionSelected("ship"))
            decision.onIntent(DecisionIntent.ConfirmTapped)
            val decided = decision.state.awaitLive { it.finalState != null }
            decided.finalState?.kind shouldBe DecisionFinalKind.Decided

            val settings = graph.createSettingsStateHolder()
            settings.state.awaitLive { it.factories.isNotEmpty() }
            settings.onIntent(SettingsIntent.SignOutRequested(row.id))
            settings.state.awaitLive { it.confirmation != null }
            settings.onIntent(SettingsIntent.ConfirmationAccepted)
            settings.state.awaitLive { s ->
                s.factories.any { it.id == row.id && it.status == FactoryStatusUi.SignedOut }
            }
            graph.resolve<SecretStore>().get(tokenKey(row.id), "test") shouldBe SecretReadResult.Missing
            graph.resolve<InstanceRepository>().get(InstanceId(row.id))?.status shouldBe InstanceStatus.SignedOut

            val manage = graph.createManageFactoriesStateHolder()
            manage.state.awaitLive { it.rows.isNotEmpty() }
            manage.onIntent(ManageFactoriesIntent.RemoveRequested(row.id))
            manage.state.awaitLive { it.confirmation != null }
            manage.onIntent(ManageFactoriesIntent.RemoveConfirmed)
            factories.state.awaitLive { it.isEmpty }
            graph.resolve<InstanceRepository>().get(InstanceId(row.id)) shouldBe null

            listOf(firstRun::close, factories::close, inbox::close, decision::close, settings::close, manage::close)
                .forEach { it() }
            graph.close()
        }

    private fun tokenKey(factory: String) = SecretKey(InstanceId(factory), SecretName.BoardToken)

    @Test
    fun `relaunch on the same database shows the factory and the cached Inbox with its stored time, offline`() =
        runTest {
            val file = newDatabaseFile()
            val secrets = FakeSecretStore()
            val first = LiveTestPlatform(file = file, secretStore = secrets).also { it.stub.withOneDecision() }
            val firstGraph = graphOn(first)
            firstGraph.createFirstRunStateHolder().completeFirstRun()
            val factories = firstGraph.createFactoriesStateHolder()
            val factoryId =
                factories.state
                    .awaitLive { it.rows.isNotEmpty() }
                    .rows
                    .single()
                    .id
            val inbox = firstGraph.attention.createInboxStateHolder(factoryId, ServerFixtures.COMPANY_ACME)
            val stored = inbox.state.awaitLive { it.phase == InboxPhase.Content && !it.isRefreshing }
            val storedAt = stored.updatedAtEpochMillis
            storedAt shouldNotBe null
            listOf(factories::close, inbox::close).forEach { it() }
            firstGraph.close()

            val offline = FakeConnectivity(online = false)
            val clock = FakeClock().also { it.advance(2.hours) }
            val second =
                LiveTestPlatform(clock, file, first.stub, secrets, connectivity = offline)
            val requestsBefore = first.stub.requests.size
            val secondGraph = graphOn(second)
            val restored = secondGraph.createFactoriesStateHolder().state.awaitLive { it.rows.isNotEmpty() }
            val cached = secondGraph.attention.createInboxStateHolder(factoryId, ServerFixtures.COMPANY_ACME)
            val shown = cached.state.awaitLive { it.phase == InboxPhase.Content }

            restored.rows.single().name shouldBe LIVE_HOST
            restored.isOffline shouldBe true
            shown.isFromCache shouldBe true
            shown.updatedAtEpochMillis shouldBe storedAt
            shown.sections.flatMap { it.items } shouldHaveSize 1
            first.stub.requests.size shouldBe requestsBefore
            cached.close()
            secondGraph.close()
        }

    @Test
    fun `an address saved in the repository is the one the next call goes to`() =
        runTest {
            val platform = LiveTestPlatform().also { it.stub.withOneDecision() }
            val graph = graphOn(platform)
            graph.createFirstRunStateHolder().completeFirstRun()
            val repository = graph.resolve<InstanceRepository>()
            val instance = repository.observeAll().first().single()
            val moved = TestData.uri("https://moved.example.com")
            repository.update(instance.copy(baseUri = moved))
            platform.stub.requests.clear()

            graph.resolve<AttentionGateway>().feed(
                CompanyScope(instance.id, CompanyId(ServerFixtures.COMPANY_ACME)),
                FeedQuery(),
            )

            platform.stub.requests
                .map { it.host }
                .distinct() shouldBe listOf("moved.example.com")
            graph.close()
        }

    @Test
    fun `a release graph refuses a stored http address before any request, a debug graph tries it`() =
        runTest {
            val file = newDatabaseFile()
            val platform = LiveTestPlatform(file = file)
            platform.stub.host("localhost")
            val localUri =
                (
                    BaseUri.parse(
                        "http://localhost:3100",
                        allowInsecureLocal = true,
                    ) as BaseUriResult.Valid
                ).uri
            val local = TestData.instance("local-1").copy(baseUri = localUri)
            platform.persistence.instances.add(local)
            platform.secretStore.put(
                tokenKey("local-1"),
                Secret.of(TestData.TOKEN),
                SecretPolicy(requireUserPresence = false),
            )
            val scope = CompanyScope(local.id, CompanyId(ServerFixtures.COMPANY_ACME))

            val release = graphOn(platform)
            val refused = release.resolve<AttentionGateway>().feed(scope, FeedQuery())
            val requestsAfterRelease = platform.stub.requests.size
            release.close()

            val debugPlatform = LiveTestPlatform(file = newDatabaseFile(), stub = platform.stub)
            debugPlatform.persistence.instances.add(local)
            debugPlatform.secretStore.put(
                tokenKey("local-1"),
                Secret.of(TestData.TOKEN),
                SecretPolicy(requireUserPresence = false),
            )
            val debug = PaperclipCore.createLiveGraph(true, FakePairingBrowser(), debugPlatform)
            debug.resolve<AttentionGateway>().feed(scope, FeedQuery())

            refused shouldBe DomainResult.Failure(DomainError.CertificateInvalid)
            requestsAfterRelease shouldBe 0
            platform.stub.requests.map { it.host } shouldBe listOf("localhost")
            debug.close()
        }
}
