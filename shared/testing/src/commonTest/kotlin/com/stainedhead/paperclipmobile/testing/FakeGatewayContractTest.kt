package com.stainedhead.paperclipmobile.testing

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionInput
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.LiveEvent
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.ServerStatus
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.HealthGateway
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.testing.contract.AgentContract
import com.stainedhead.paperclipmobile.testing.contract.ApprovalContract
import com.stainedhead.paperclipmobile.testing.contract.AttentionFeedContract
import com.stainedhead.paperclipmobile.testing.contract.AttentionTriageContract
import com.stainedhead.paperclipmobile.testing.contract.AuthAccountContract
import com.stainedhead.paperclipmobile.testing.contract.AuthPairingContract
import com.stainedhead.paperclipmobile.testing.contract.BiometricsContract
import com.stainedhead.paperclipmobile.testing.contract.BudgetContract
import com.stainedhead.paperclipmobile.testing.contract.ClockContract
import com.stainedhead.paperclipmobile.testing.contract.DashboardContract
import com.stainedhead.paperclipmobile.testing.contract.DecisionContract
import com.stainedhead.paperclipmobile.testing.contract.DeviceTimeZoneContract
import com.stainedhead.paperclipmobile.testing.contract.HealthContract
import com.stainedhead.paperclipmobile.testing.contract.IdGeneratorContract
import com.stainedhead.paperclipmobile.testing.contract.InteractionContract
import com.stainedhead.paperclipmobile.testing.contract.IssueReadContract
import com.stainedhead.paperclipmobile.testing.contract.IssueWriteContract
import com.stainedhead.paperclipmobile.testing.contract.LiveEventsContract
import com.stainedhead.paperclipmobile.testing.contract.PairingBrowserContract
import com.stainedhead.paperclipmobile.testing.contract.RunReadContract
import com.stainedhead.paperclipmobile.testing.contract.RunWriteContract
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAuthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeBudgetGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeDashboardGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDeviceTimeZone
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeInteractionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIssueGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeLiveEvents
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeRunGateway
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private val acmeScope = CompanyScope(InstanceId("inst-1"), TestData.acme)

class FakeHealthContractTest : HealthContract() {
    override val authenticated: BaseUri = TestData.uri("https://ready.example.com")
    override val localTrusted: BaseUri = TestData.uri("https://trusted.example.com")
    override val bootstrapping: BaseUri = TestData.uri("https://bootstrap.example.com")
    override val unreachable: BaseUri = TestData.uri("https://nobody.example.com")

    override fun create(): HealthGateway =
        FakeHealthGateway().also { fake ->
            fake.responses[authenticated] = DomainResult.Success(FakeHealthGateway.healthy())
            fake.responses[localTrusted] =
                DomainResult.Success(FakeHealthGateway.healthy(mode = DeploymentMode.LocalTrusted))
            fake.responses[bootstrapping] =
                DomainResult.Success(
                    HealthReport(
                        ServerStatus.Ok,
                        DeploymentMode.Authenticated,
                        com.stainedhead.paperclipmobile.domain.Exposure.Private,
                        null,
                        bootstrapPending = true,
                    ),
                )
            fake.responses[unreachable] = DomainResult.Failure(DomainError.Unreachable)
        }

    @Test
    fun `the fake passes the health contract`() = runTest { runAll() }
}

class FakeAuthPairingContractTest : AuthPairingContract() {
    private var last = FakeAuthGateway()
    override val server: BaseUri = TestData.uri()
    override val validKey: Secret = Secret.of(TestData.TOKEN)
    override val invalidKey: Secret = Secret.of("pcp_board_TESTONLY_WRONG")

    override fun create(): AuthGateway =
        FakeAuthGateway().also { it.validKeys = setOf(TestData.TOKEN) }.also {
            last =
                it
        }

    override suspend fun approveInBrowser(challenge: PairingChallenge) {
        last.pollResults.add(DomainResult.Success(ChallengeStatus.Approved))
    }

    override suspend fun letExpire(challenge: PairingChallenge) {
        last.pollResults.add(DomainResult.Success(ChallengeStatus.Expired))
    }

    @Test
    fun `the fake passes the pairing contract`() = runTest { runAll() }
}

class FakeAuthAccountContractTest : AuthAccountContract() {
    override val instance = InstanceId("inst-1")
    override val signedOutInstance = InstanceId("inst-signed-out")

    override fun create(): AuthGateway = FakeAuthGateway().also { it.signedOutInstances += signedOutInstance }

    @Test
    fun `the fake passes the account contract`() = runTest { runAll() }
}

class FakeAttentionFeedContractTest : AttentionFeedContract() {
    override val scope = acmeScope

    override fun create(): AttentionGateway =
        FakeAttentionGateway(
            FakeClock(),
            AttentionFixtures.feed().items + AttentionFixtures.approvalItem("ap-2") +
                AttentionFixtures.decisionItem("dec-2"),
        )

    @Test
    fun `the fake passes the attention feed contract`() = runTest { runAll() }
}

class FakeAttentionTriageContractTest : AttentionTriageContract() {
    override val scope = acmeScope
    override val now = FakeClock().now()

    override fun create(): AttentionGateway = FakeAttentionGateway(FakeClock())

    @Test
    fun `the fake passes the attention triage contract`() = runTest { runAll() }
}

class FakeDecisionContractTest : DecisionContract() {
    override val scope = acmeScope
    override val openIds = (1..5).map { DecisionId("d$it") }
    override val validOptionId = "ship"
    override val otherOptionId = "cancel"
    override val inputId = "note"
    override val effectLessOptionId = "hold"

    override fun create(): DecisionGateway =
        FakeDecisionGateway(
            openIds.mapIndexed { index, id ->
                val base = AttentionFixtures.decision(id.value)
                when (index) {
                    2 -> base.copy(inputs = listOf(DecisionInput(inputId, "Note")))
                    4 -> base.copy(options = base.options.filter { it.effects.isNotEmpty() })
                    else -> base
                }
            },
        )

    @Test
    fun `the fake passes the decision contract`() = runTest { runAll() }
}

class FakeApprovalContractTest : ApprovalContract() {
    override val scope = acmeScope
    override val pendingIds = (1..4).map { ApprovalId("ap-$it") }

    override fun create(): ApprovalGateway =
        FakeApprovalGateway(pendingIds.map { AttentionFixtures.approval(it.value) })

    @Test
    fun `the fake passes the approval contract`() = runTest { runAll() }
}

class FakeBudgetContractTest : BudgetContract() {
    override val scope = acmeScope
    override val openIds = listOf(BudgetIncidentId("inc-1"), BudgetIncidentId("inc-2"))
    override val observed = Money.usdCents(OBSERVED_CENTS)

    override fun create(): BudgetGateway =
        FakeBudgetGateway(openIds.map { AttentionFixtures.incident(it.value, OBSERVED_CENTS, OBSERVED_CENTS) })

    @Test
    fun `the fake passes the budget contract`() = runTest { runAll() }

    private companion object {
        const val OBSERVED_CENTS = 10_000L
    }
}

class FakeIssueReadContractTest : IssueReadContract() {
    override val scope = acmeScope
    override val searchableIdentifier = "PAP-1182"

    override fun create(): IssueGateway = FakeIssueGateway()

    @Test
    fun `the fake passes the issue read contract`() = runTest { runAll() }
}

class FakeIssueWriteContractTest : IssueWriteContract() {
    override val scope = acmeScope
    override val openId = WorkFixtures.pap1182
    override val closedId = IssueId("pap-1100")
    override val agent: AgentId = WorkFixtures.engineer1

    override fun create(): IssueGateway = FakeIssueGateway()

    @Test
    fun `the fake passes the issue write contract`() = runTest { runAll() }
}

class FakeInteractionContractTest : InteractionContract() {
    override val scope = acmeScope
    override val issue = WorkFixtures.pap1182
    override val pendingConfirmations = listOf(InteractionId("c1"), InteractionId("c2"))
    override val answeredId = InteractionId("int-questions")

    override fun create(): InteractionGateway =
        FakeInteractionGateway(
            WorkFixtures.interactions().drop(1) +
                pendingConfirmations.map {
                    com.stainedhead.paperclipmobile.domain
                        .RequestConfirmation(WorkFixtures.meta(it.value), "Proceed?")
                },
        )

    @Test
    fun `the fake passes the interaction contract`() = runTest { runAll() }
}

class FakeAgentContractTest : AgentContract() {
    override val scope = acmeScope
    override val runningAgent = WorkFixtures.engineer1
    override val unknownAgent = AgentId("no-such-agent")

    override fun create(): AgentGateway = FakeAgentGateway()

    @Test
    fun `the fake passes the agent contract`() = runTest { runAll() }
}

class FakeRunReadContractTest : RunReadContract() {
    override val scope = acmeScope
    override val liveRunId =
        com.stainedhead.paperclipmobile.domain
            .RunId("run-live")
    override val issueWithRuns = WorkFixtures.pap1182
    override val agentWithRuns = WorkFixtures.engineer1

    override fun create(): RunGateway = FakeRunGateway(chunkSize = CHUNK)

    @Test
    fun `the fake passes the run read contract`() = runTest { runAll() }

    private companion object {
        const val CHUNK = 16
    }
}

class FakeRunWriteContractTest : RunWriteContract() {
    override val scope = acmeScope
    override val liveRunId =
        com.stainedhead.paperclipmobile.domain
            .RunId("run-live")
    override val finishedRunId =
        com.stainedhead.paperclipmobile.domain
            .RunId("run-done")

    override fun create(): RunGateway = FakeRunGateway()

    @Test
    fun `the fake passes the run write contract`() = runTest { runAll() }
}

class FakeDashboardContractTest : DashboardContract() {
    override val scope = acmeScope

    override fun create(): DashboardGateway = FakeDashboardGateway()

    @Test
    fun `the fake passes the dashboard contract`() = runTest { runAll() }
}

class FakeBiometricsContractTest : BiometricsContract() {
    override fun create(): Biometrics = FakeBiometrics()

    override fun willAnswer(
        biometrics: Biometrics,
        result: BiometricResult,
    ) {
        (biometrics as FakeBiometrics).result = result
    }

    @Test
    fun `the fake passes the biometrics contract`() = runTest { runAll() }
}

class FakeClockContractTest : ClockContract() {
    override fun create(): Clock = FakeClock()

    override suspend fun advance(clock: Clock) {
        (clock as FakeClock).advance(1.seconds)
    }

    @Test
    fun `the fake passes the clock contract`() = runTest { runAll() }
}

class FakeIdGeneratorContractTest : IdGeneratorContract() {
    override fun create(): IdGenerator = FakeIdGenerator("id-")

    @Test
    fun `the fake passes the id generator contract`() = runAll()
}

class FakeDeviceTimeZoneContractTest : DeviceTimeZoneContract() {
    override fun create(): DeviceTimeZone = FakeDeviceTimeZone()

    @Test
    fun `the fake passes the time zone contract`() = runAll()
}

class FakePairingBrowserContractTest : PairingBrowserContract() {
    override fun create(): PairingBrowser = FakePairingBrowser()

    override fun openedUrls(browser: PairingBrowser): List<String> = (browser as FakePairingBrowser).openedUrls

    @Test
    fun `the fake passes the pairing browser contract`() = runTest { runAll() }
}

class FakeLiveEventsContractTest : LiveEventsContract() {
    override fun create(): LiveEvents = FakeLiveEvents()

    override suspend fun emit(
        events: LiveEvents,
        event: LiveEvent,
    ) {
        (events as FakeLiveEvents).emit(event)
    }

    override suspend fun collectWhile(
        events: LiveEvents,
        count: Int,
        send: suspend () -> Unit,
    ): List<LiveEvent> =
        coroutineScope {
            val received = async(start = CoroutineStart.UNDISPATCHED) { events.events.take(count).toList() }
            send()
            received.await()
        }

    @Test
    fun `the fake passes the live events contract`() = runTest { runAll() }
}
