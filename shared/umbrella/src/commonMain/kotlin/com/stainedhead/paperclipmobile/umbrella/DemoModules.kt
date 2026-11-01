package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
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
import org.koin.dsl.module

private const val DEMO_DEVICE_NAME = "Demo phone"

/**
 * Every port backed by the shared fakes, seeded with the demo data set of the design mocks (ADR-026). The clock is
 * frozen at a mid-week instant; the Home factory holds a fake board key and every company-scoped read sees Acme Labs.
 */
internal fun demoPortsModule() =
    module {
        single<Clock> { FakeClock(DemoFactories.now) }
        single<IdGenerator> { FakeIdGenerator("demo-") }
        single<InstanceRepository> { FakeInstanceRepository(DemoFactories.instances()) }
        single<SecretStore> {
            FakeSecretStore().apply {
                seed(SecretKey(InstanceId(DemoIds.HOME_FACTORY), SecretName.BoardToken), DemoFactories.HOME_TOKEN)
            }
        }
        single<AppSettingsStore> {
            FakeAppSettingsStore(AppSettings(DEMO_DEVICE_NAME, selectedInstanceId = InstanceId(DemoIds.HOME_FACTORY)))
        }
        single<Biometrics> { FakeBiometrics() }
        single<Connectivity> { FakeConnectivity() }
        single<PairingBrowser> { FakePairingBrowser() }
        single<HealthGateway> { FakeHealthGateway() }
        single<DeviceTimeZone> { FakeDeviceTimeZone() }
        single<AuthGateway> {
            FakeAuthGateway().apply {
                meResult = DomainResult.Success(DemoFactories.access())
                meWithKeyResult = DomainResult.Success(DemoFactories.access())
                companiesResult = DomainResult.Success(DemoFactories.companies())
            }
        }
        demoAttentionPorts()
        demoWorkPorts()
    }

private fun org.koin.core.module.Module.demoAttentionPorts() {
    single<AttentionGateway> { FakeAttentionGateway(get(), DemoInbox.items()) }
    single<AttentionCache> { FakeAttentionCache() }
    single<DecisionGateway> { FakeDecisionGateway(DemoAttentionDetails.decisions()) }
    single<ApprovalGateway> {
        FakeApprovalGateway(DemoAttentionDetails.approvals()).apply { issues = DemoAttentionDetails.linkedIssues() }
    }
    single<BudgetGateway> { FakeBudgetGateway(DemoAttentionDetails.incidents()) }
}

private fun org.koin.core.module.Module.demoWorkPorts() {
    single<IssueGateway> {
        FakeIssueGateway(
            DemoWork.issues(),
            DemoFactories.USER_ID,
            DemoWork.comments(),
            DemoWork.plans(),
            DemoWork.attachments(),
            "Billing v2",
        )
    }
    single<InteractionGateway> {
        FakeInteractionGateway(listOf(DemoWork.confirmation()), DemoWork.interactionsByIssue())
    }
    single<AgentGateway> { FakeAgentGateway(DemoAgents.agents()) }
    single<RunGateway> { FakeRunGateway(DemoAgents.runs(), events = DemoAgents.events(), log = DemoAgents.LOG) }
    single<DashboardGateway> { FakeDashboardGateway(DemoAgents.summary(), DemoAgents.spendRows()) }
}
