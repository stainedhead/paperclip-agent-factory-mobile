package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.ServerStatus
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.AttentionCache
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import com.stainedhead.paperclipmobile.domain.port.HealthGateway
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.demo.DemoAgents
import com.stainedhead.paperclipmobile.testing.demo.DemoAttentionDetails
import com.stainedhead.paperclipmobile.testing.demo.DemoFactories
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import com.stainedhead.paperclipmobile.testing.demo.DemoInbox
import com.stainedhead.paperclipmobile.testing.demo.DemoWork
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAppSettingsStore
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionCache
import com.stainedhead.paperclipmobile.testing.fakes.FakeAttentionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeAuthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeBiometrics
import com.stainedhead.paperclipmobile.testing.fakes.FakeBudgetGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeClock
import com.stainedhead.paperclipmobile.testing.fakes.FakeConnectivity
import com.stainedhead.paperclipmobile.testing.fakes.FakeDashboardGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDecisionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeDeviceTimeZone
import com.stainedhead.paperclipmobile.testing.fakes.FakeHealthGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIdGenerator
import com.stainedhead.paperclipmobile.testing.fakes.FakeInstanceRepository
import com.stainedhead.paperclipmobile.testing.fakes.FakeInteractionGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeIssueGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakePairingBrowser
import com.stainedhead.paperclipmobile.testing.fakes.FakeRunGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeSecretStore
import com.stainedhead.paperclipmobile.testing.fakes.approvePairings
import org.koin.dsl.module

/**
 * Every port backed by the shared fakes, seeded with the demo data set of the design mocks (ADR-026). The clock is
 * frozen at a mid-week instant; the Home factory holds a fake board key and every company-scoped read sees Acme Labs.
 */
internal fun demoPortsModule(seeded: Boolean = true) =
    module {
        single<Clock> { FakeClock(DemoFactories.now) }
        single<IdGenerator> { FakeIdGenerator("demo-") }
        single<InstanceRepository> { FakeInstanceRepository(if (seeded) DemoFactories.instances() else emptyList()) }
        single<SecretStore> {
            FakeSecretStore().apply {
                if (seeded) {
                    seed(SecretKey(InstanceId(DemoIds.HOME_FACTORY), SecretName.BoardToken), DemoFactories.HOME_TOKEN)
                }
            }
        }
        single<AppSettingsStore> {
            val selected = if (seeded) InstanceId(DemoIds.HOME_FACTORY) else null
            FakeAppSettingsStore(AppSettings(DemoFactories.DEVICE_NAME, selectedInstanceId = selected))
        }
        single { FakeBiometrics() }
        single<Biometrics> { get<FakeBiometrics>() }
        demoControls()
        single { FakeConnectivity() }
        single<Connectivity> { get<FakeConnectivity>() }
        single<PairingBrowser> { FakePairingBrowser() }
        single<HealthGateway> {
            FakeHealthGateway(
                DomainResult.Success(
                    HealthReport(
                        ServerStatus.Ok,
                        DeploymentMode.Authenticated,
                        Exposure.Private,
                        DemoFactories.SERVER_VERSION,
                        false,
                    ),
                ),
            )
        }
        single<DeviceTimeZone> { FakeDeviceTimeZone() }
        single<AuthGateway> {
            FakeAuthGateway().apply {
                meResult = DomainResult.Success(DemoFactories.access())
                meWithKeyResult = DomainResult.Success(DemoFactories.access())
                companiesResult = DomainResult.Success(DemoFactories.companies())
                keysResult = DomainResult.Success(DemoFactories.boardKeys())
                val clock = get<Clock>()
                val repository = get<InstanceRepository>() as FakeInstanceRepository
                hostOf = { id -> repository.current.firstOrNull { it.id == id }?.baseUri }
                approvePairings(from = { clock.now() })
            }
        }
        demoAttentionPorts()
        demoWorkPorts()
        signalPorts()
    }

private fun org.koin.core.module.Module.demoControls() {
    single {
        DemoControls(
            get(),
            get(),
            get<DecisionGateway>(RawPort) as FakeDecisionGateway,
            get<ApprovalGateway>(RawPort) as FakeApprovalGateway,
            get<BudgetGateway>(RawPort) as FakeBudgetGateway,
            DemoReadFailures(
                get<AgentGateway>(RawPort) as FakeAgentGateway,
                get<RunGateway>(RawPort) as FakeRunGateway,
                get<DashboardGateway>(RawPort) as FakeDashboardGateway,
            ),
        )
    }
}

private fun org.koin.core.module.Module.demoAttentionPorts() {
    single<AttentionGateway>(RawPort) {
        FakeAttentionGateway(get(), DemoInbox.items()).apply {
            firstFeedAge = DemoInbox.FIRST_READ_AGE
            emptyCompanies = setOf(DemoIds.SIDE_PROJECT)
        }
    }
    single<AttentionCache> { FakeAttentionCache() }
    single<DecisionGateway>(RawPort) {
        FakeDecisionGateway(DemoAttentionDetails.decisions()).apply {
            onResolved = { id -> (get<AttentionGateway>(RawPort) as FakeAttentionGateway).resolveSubject(id) }
        }
    }
    single<ApprovalGateway>(RawPort) {
        FakeApprovalGateway(DemoAttentionDetails.approvals()).apply {
            issues = DemoAttentionDetails.linkedIssues()
            commentList = DemoAttentionDetails.approvalComments().toMutableList()
            onResolved = { id -> (get<AttentionGateway>(RawPort) as FakeAttentionGateway).resolveSubject(id) }
        }
    }
    single<BudgetGateway>(RawPort) {
        FakeBudgetGateway(DemoAttentionDetails.incidents()).apply {
            onResolved = { id -> (get<AttentionGateway>(RawPort) as FakeAttentionGateway).resolveSubject(id) }
        }
    }
}

private fun org.koin.core.module.Module.demoWorkPorts() {
    single<IssueGateway>(RawPort) {
        FakeIssueGateway(
            DemoWork.issues(),
            DemoFactories.USER_ID,
            DemoWork.comments(),
            DemoWork.plans(),
            DemoWork.attachments(),
            "Billing v2",
            now = { get<Clock>().now() },
        )
    }
    single<InteractionGateway>(RawPort) {
        FakeInteractionGateway(listOf(DemoWork.confirmation()), DemoWork.interactionsByIssue()).apply {
            onAnswered = { id -> (get<AttentionGateway>(RawPort) as FakeAttentionGateway).resolveSubject(id) }
        }
    }
    single<AgentGateway>(RawPort) {
        FakeAgentGateway(DemoAgents.agents()).apply {
            // A run that was asked for appears like a server's: in the run store, in progress, started now.
            onRunStarted = { run ->
                val now = get<Clock>().now()
                (get<RunGateway>(RawPort) as FakeRunGateway).store[run.id] =
                    run.copy(status = RunStatus.Running, createdAt = now, startedAt = now)
            }
        }
    }
    single<RunGateway>(
        RawPort,
    ) { FakeRunGateway(DemoAgents.runs(), events = DemoAgents.events(), log = DemoAgents.LOG) }
    single<DashboardGateway>(RawPort) { FakeDashboardGateway(DemoAgents.summary(), DemoAgents.spendRows()) }
}
