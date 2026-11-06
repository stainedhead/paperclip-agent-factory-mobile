package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.auth.SessionCachingSecretStore
import com.stainedhead.paperclipmobile.application.auth.SessionTokens
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareAgentGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareApprovalGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareAttentionGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareBudgetGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareDashboardGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareDecisionGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareInteractionGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareIssueGateway
import com.stainedhead.paperclipmobile.application.auth.SignedOutAwareRunGateway
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import org.koin.core.module.Module
import org.koin.core.qualifier.named

/** The qualifier of a company-scoped gateway as an adapter (or fake) provides it, before the signed-out decorator. */
internal val RawPort = named("raw")

/**
 * The board token cache for the foreground unlock session (ADR-145). The device store is bound under [RawPort]; what
 * everything else receives as [SecretStore] is the cache above it, so the Keystore and Keychain stores stay per-use
 * (ADR-139). The same instance is the [SessionTokens] the lifecycle bridge and the signed-out marker clear.
 */
internal fun Module.sessionTokens() {
    single { SessionCachingSecretStore(get(RawPort)) }
    single<SecretStore> { get<SessionCachingSecretStore>() }
    single<SessionTokens> { get<SessionCachingSecretStore>() }
}

/**
 * What use cases receive: each raw company-scoped gateway behind a decorator that marks the factory Signed out on a
 * 401 (PRD FR-023, ADR-031). Adapters bind their gateway under [RawPort]; nothing else is needed to get the behaviour.
 */
internal fun Module.guardedGateways() {
    single<AttentionGateway> { SignedOutAwareAttentionGateway(get(RawPort), get()) }
    single<DecisionGateway> { SignedOutAwareDecisionGateway(get(RawPort), get()) }
    single<ApprovalGateway> { SignedOutAwareApprovalGateway(get(RawPort), get()) }
    single<BudgetGateway> { SignedOutAwareBudgetGateway(get(RawPort), get()) }
    single<IssueGateway> { SignedOutAwareIssueGateway(get(RawPort), get()) }
    single<InteractionGateway> { SignedOutAwareInteractionGateway(get(RawPort), get()) }
    single<AgentGateway> { SignedOutAwareAgentGateway(get(RawPort), get()) }
    single<RunGateway> { SignedOutAwareRunGateway(get(RawPort), get()) }
    single<DashboardGateway> { SignedOutAwareDashboardGateway(get(RawPort), get()) }
}
