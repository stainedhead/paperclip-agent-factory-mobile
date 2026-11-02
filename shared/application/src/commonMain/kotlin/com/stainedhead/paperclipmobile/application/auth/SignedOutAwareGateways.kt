package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueDetail
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import com.stainedhead.paperclipmobile.domain.LogChunk
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.SpendRow
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.domain.port.InteractionGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import kotlin.time.Instant

/*
 * The one place a 401 from a company-scoped call becomes "Signed out" (PRD FR-023, ADR-031). The composition root hands
 * use cases these decorators instead of the raw gateways, so no use case repeats the check. Real adapters (Phase 1b)
 * must map HTTP 401 to DomainError.Unauthorized for this to see it.
 */
internal suspend fun <T> SignedOutMarker.guarded(
    scope: CompanyScope,
    call: suspend () -> DomainResult<T>,
): DomainResult<T> = call().also { noteIfUnauthorized(scope.instanceId, it) }

/** [AttentionGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareAttentionGateway(
    private val delegate: AttentionGateway,
    private val marker: SignedOutMarker,
) : AttentionGateway {
    override suspend fun feed(
        scope: CompanyScope,
        query: FeedQuery,
    ): DomainResult<AttentionFeed> = marker.guarded(scope) { delegate.feed(scope, query) }

    override suspend fun dismiss(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Dismissal> = marker.guarded(scope) { delegate.dismiss(scope, itemKey) }

    override suspend fun snooze(
        scope: CompanyScope,
        itemKey: String,
        until: Instant,
    ): DomainResult<Dismissal> = marker.guarded(scope) { delegate.snooze(scope, itemKey, until) }

    override suspend fun restore(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Unit> = marker.guarded(scope) { delegate.restore(scope, itemKey) }

    override suspend fun setDecideBy(
        scope: CompanyScope,
        sourceKind: AttentionSourceKind,
        sourceId: String,
        decideBy: DecideBy,
    ): DomainResult<Unit> = marker.guarded(scope) { delegate.setDecideBy(scope, sourceKind, sourceId, decideBy) }
}

/** [DecisionGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareDecisionGateway(
    private val delegate: DecisionGateway,
    private val marker: SignedOutMarker,
) : DecisionGateway {
    override suspend fun get(
        scope: CompanyScope,
        id: DecisionId,
    ): DomainResult<Decision> = marker.guarded(scope) { delegate.get(scope, id) }

    override suspend fun decide(
        scope: CompanyScope,
        id: DecisionId,
        optionId: String,
        inputValues: Map<String, String>,
        idempotencyKey: String,
    ): DomainResult<Decision> =
        marker.guarded(scope) { delegate.decide(scope, id, optionId, inputValues, idempotencyKey) }

    override suspend fun dismiss(
        scope: CompanyScope,
        id: DecisionId,
        reason: String?,
    ): DomainResult<Decision> = marker.guarded(scope) { delegate.dismiss(scope, id, reason) }
}

/** [ApprovalGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareApprovalGateway(
    private val delegate: ApprovalGateway,
    private val marker: SignedOutMarker,
) : ApprovalGateway {
    override suspend fun get(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<Approval> = marker.guarded(scope) { delegate.get(scope, id) }

    override suspend fun linkedIssues(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<LinkedIssue>> = marker.guarded(scope) { delegate.linkedIssues(scope, id) }

    override suspend fun comments(
        scope: CompanyScope,
        id: ApprovalId,
    ): DomainResult<List<ApprovalComment>> = marker.guarded(scope) { delegate.comments(scope, id) }

    override suspend fun approve(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval> = marker.guarded(scope) { delegate.approve(scope, id, note) }

    override suspend fun reject(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval> = marker.guarded(scope) { delegate.reject(scope, id, note) }

    override suspend fun requestRevision(
        scope: CompanyScope,
        id: ApprovalId,
        note: String?,
    ): DomainResult<Approval> = marker.guarded(scope) { delegate.requestRevision(scope, id, note) }

    override suspend fun addComment(
        scope: CompanyScope,
        id: ApprovalId,
        body: String,
    ): DomainResult<ApprovalComment> = marker.guarded(scope) { delegate.addComment(scope, id, body) }
}

/** [BudgetGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareBudgetGateway(
    private val delegate: BudgetGateway,
    private val marker: SignedOutMarker,
) : BudgetGateway {
    override suspend fun incident(
        scope: CompanyScope,
        id: BudgetIncidentId,
    ): DomainResult<BudgetIncident> = marker.guarded(scope) { delegate.incident(scope, id) }

    override suspend fun resolve(
        scope: CompanyScope,
        id: BudgetIncidentId,
        resolution: BudgetResolution,
        note: String?,
    ): DomainResult<BudgetIncident> = marker.guarded(scope) { delegate.resolve(scope, id, resolution, note) }
}

/** [IssueGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareIssueGateway(
    private val delegate: IssueGateway,
    private val marker: SignedOutMarker,
) : IssueGateway {
    override suspend fun list(
        scope: CompanyScope,
        query: IssueQuery,
    ): DomainResult<List<Issue>> = marker.guarded(scope) { delegate.list(scope, query) }

    override suspend fun get(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<IssueDetail> = marker.guarded(scope) { delegate.get(scope, id) }

    override suspend fun comments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Comment>> = marker.guarded(scope) { delegate.comments(scope, id) }

    override suspend fun addComment(
        scope: CompanyScope,
        id: IssueId,
        body: String,
        options: CommentOptions,
    ): DomainResult<Comment> = marker.guarded(scope) { delegate.addComment(scope, id, body, options) }

    override suspend fun setStatus(
        scope: CompanyScope,
        id: IssueId,
        status: IssueStatus,
    ): DomainResult<Issue> = marker.guarded(scope) { delegate.setStatus(scope, id, status) }

    override suspend fun assign(
        scope: CompanyScope,
        id: IssueId,
        assignee: Assignee,
    ): DomainResult<Issue> = marker.guarded(scope) { delegate.assign(scope, id, assignee) }

    override suspend fun create(
        scope: CompanyScope,
        issue: NewIssue,
    ): DomainResult<Issue> = marker.guarded(scope) { delegate.create(scope, issue) }

    override suspend fun attachments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Attachment>> = marker.guarded(scope) { delegate.attachments(scope, id) }
}

/** [InteractionGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareInteractionGateway(
    private val delegate: InteractionGateway,
    private val marker: SignedOutMarker,
) : InteractionGateway {
    override suspend fun list(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<Interaction>> = marker.guarded(scope) { delegate.list(scope, issueId) }

    override suspend fun answer(
        scope: CompanyScope,
        issueId: IssueId,
        interactionId: InteractionId,
        answer: InteractionAnswer,
    ): DomainResult<Interaction> = marker.guarded(scope) { delegate.answer(scope, issueId, interactionId, answer) }
}

/** [AgentGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareAgentGateway(
    private val delegate: AgentGateway,
    private val marker: SignedOutMarker,
) : AgentGateway {
    override suspend fun list(scope: CompanyScope): DomainResult<List<Agent>> =
        marker.guarded(scope) { delegate.list(scope) }

    override suspend fun get(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent> = marker.guarded(scope) { delegate.get(scope, id) }

    override suspend fun pause(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent> = marker.guarded(scope) { delegate.pause(scope, id) }

    override suspend fun resume(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent> = marker.guarded(scope) { delegate.resume(scope, id) }

    override suspend fun invokeHeartbeat(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<InvokeOutcome> = marker.guarded(scope) { delegate.invokeHeartbeat(scope, id) }

    override suspend fun setBudget(
        scope: CompanyScope,
        id: AgentId,
        monthly: Money,
    ): DomainResult<Agent> = marker.guarded(scope) { delegate.setBudget(scope, id, monthly) }
}

/** [RunGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareRunGateway(
    private val delegate: RunGateway,
    private val marker: SignedOutMarker,
) : RunGateway {
    override suspend fun get(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun> = marker.guarded(scope) { delegate.get(scope, id) }

    override suspend fun events(
        scope: CompanyScope,
        id: RunId,
        afterSeq: Long,
    ): DomainResult<List<RunEvent>> = marker.guarded(scope) { delegate.events(scope, id, afterSeq) }

    override suspend fun log(
        scope: CompanyScope,
        id: RunId,
        offset: Long,
    ): DomainResult<LogChunk> = marker.guarded(scope) { delegate.log(scope, id, offset) }

    override suspend fun cancel(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun> = marker.guarded(scope) { delegate.cancel(scope, id) }

    override suspend fun forIssue(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<HeartbeatRun>> = marker.guarded(scope) { delegate.forIssue(scope, issueId) }

    override suspend fun forAgent(
        scope: CompanyScope,
        agentId: AgentId,
    ): DomainResult<List<HeartbeatRun>> = marker.guarded(scope) { delegate.forAgent(scope, agentId) }

    override suspend fun live(scope: CompanyScope): DomainResult<List<HeartbeatRun>> =
        marker.guarded(scope) { delegate.live(scope) }
}

/** [DashboardGateway] that marks the factory Signed out when a call answers 401 (PRD FR-023, ADR-031). */
class SignedOutAwareDashboardGateway(
    private val delegate: DashboardGateway,
    private val marker: SignedOutMarker,
) : DashboardGateway {
    override suspend fun dashboard(scope: CompanyScope): DomainResult<DashboardSummary> =
        marker.guarded(scope) { delegate.dashboard(scope) }

    override suspend fun windowSpend(scope: CompanyScope): DomainResult<List<SpendRow>> =
        marker.guarded(scope) { delegate.windowSpend(scope) }
}
