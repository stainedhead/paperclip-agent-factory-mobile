package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.domain.LiveEvent
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
import com.stainedhead.paperclipmobile.domain.port.LiveEvents
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.AgentGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.ApprovalGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.AttentionGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.AuthGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.BudgetGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.DashboardGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.DecisionGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.HealthGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.InteractionGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.IssueGatewayAdapter
import com.stainedhead.paperclipmobile.infrastructure.api.PaperclipHttpClient
import com.stainedhead.paperclipmobile.infrastructure.api.RunGatewayAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Every port of the live graph, backed by the real adapters (AD-040). Nothing here may come from the testing module:
 * a Konsist rule reads this file and a graph test resolves every port (ADR-068). The company-scoped gateways are bound
 * under [RawPort]; `guardedGateways()` in the use case module puts the signed-out decorator on top.
 */
internal fun livePortsModule(
    platform: LivePlatform,
    client: PaperclipHttpClient,
    pairingBrowser: PairingBrowser,
) = module {
    single<Clock> { platform.clock }
    single<IdGenerator> { platform.idGenerator }
    single<DeviceTimeZone> { platform.timeZone }
    single<SecretStore> { platform.secretStore }
    single<Biometrics> { platform.biometrics }
    single<Connectivity> { platform.connectivity }
    single<PairingBrowser> { pairingBrowser }
    single<InstanceRepository> { platform.persistence.instances }
    single<AppSettingsStore> { platform.persistence.settings }
    single<AttentionCache> { platform.persistence.attentionCache }
    single<HealthGateway> { HealthGatewayAdapter(client, get()) }
    single<AuthGateway> { AuthGatewayAdapter(client, get(), get()) }
    liveCompanyGateways(client)
    // Board keys are rejected on the pinned server's WebSocket, so screens poll and no events arrive (ADR-068).
    single<LiveEvents> { PollingOnlyLiveEvents }
    lifecyclePorts()
}

private fun Module.liveCompanyGateways(client: PaperclipHttpClient) {
    single<AttentionGateway>(RawPort) { AttentionGatewayAdapter(client, get(), get()) }
    single<DecisionGateway>(RawPort) { DecisionGatewayAdapter(client, get(), get()) }
    single<ApprovalGateway>(RawPort) { ApprovalGatewayAdapter(client, get(), get()) }
    single<BudgetGateway>(RawPort) { BudgetGatewayAdapter(client, get(), get()) }
    single<IssueGateway>(RawPort) { IssueGatewayAdapter(client, get(), get()) }
    single<InteractionGateway>(RawPort) { InteractionGatewayAdapter(client, get(), get()) }
    single<AgentGateway>(RawPort) { AgentGatewayAdapter(client, get(), get()) }
    single<RunGateway>(RawPort) { RunGatewayAdapter(client, get(), get()) }
    single<DashboardGateway>(RawPort) { DashboardGatewayAdapter(client, get(), get()) }
}

/** No live events: the pinned server's WebSocket refuses board keys, so the refresh policy polls instead. */
private object PollingOnlyLiveEvents : LiveEvents {
    override val events: Flow<LiveEvent> = emptyFlow()
}
