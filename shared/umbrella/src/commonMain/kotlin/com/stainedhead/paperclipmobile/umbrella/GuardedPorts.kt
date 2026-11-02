package com.stainedhead.paperclipmobile.umbrella

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
import org.koin.core.module.Module
import org.koin.core.qualifier.named

/** The qualifier of a company-scoped gateway as an adapter (or fake) provides it, before the signed-out decorator. */
internal val RawPort = named("raw")

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
