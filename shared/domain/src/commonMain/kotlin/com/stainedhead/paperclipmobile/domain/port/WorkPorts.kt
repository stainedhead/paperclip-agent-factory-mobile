package com.stainedhead.paperclipmobile.domain.port

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DashboardSummary
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
import com.stainedhead.paperclipmobile.domain.LogChunk
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.SpendRow

/** Issues and their thread (PRD FR-060 to FR-062; research W1 to W5). Names are not joined here: use cases do that. */
interface IssueGateway {
    suspend fun list(
        scope: CompanyScope,
        query: IssueQuery,
    ): DomainResult<List<Issue>>

    suspend fun get(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<IssueDetail>

    suspend fun comments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Comment>>

    suspend fun addComment(
        scope: CompanyScope,
        id: IssueId,
        body: String,
        options: CommentOptions,
    ): DomainResult<Comment>

    suspend fun setStatus(
        scope: CompanyScope,
        id: IssueId,
        status: IssueStatus,
    ): DomainResult<Issue>

    suspend fun assign(
        scope: CompanyScope,
        id: IssueId,
        assignee: Assignee,
    ): DomainResult<Issue>

    suspend fun create(
        scope: CompanyScope,
        issue: NewIssue,
    ): DomainResult<Issue>

    suspend fun attachments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Attachment>>
}

/** Issue-thread interactions (PRD FR-063; research W7). One call per answer. */
interface InteractionGateway {
    suspend fun list(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<Interaction>>

    /** Sends [answer] to the endpoint for its kind. Never called with an unsupported interaction. */
    suspend fun answer(
        scope: CompanyScope,
        issueId: IssueId,
        interactionId: InteractionId,
        answer: InteractionAnswer,
    ): DomainResult<Interaction>
}

/** Agents and their controls (PRD FR-070 to FR-072; research A1, A2). */
interface AgentGateway {
    suspend fun list(scope: CompanyScope): DomainResult<List<Agent>>

    suspend fun get(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent>

    suspend fun pause(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent>

    suspend fun resume(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent>

    suspend fun invokeHeartbeat(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<InvokeOutcome>

    suspend fun setBudget(
        scope: CompanyScope,
        id: AgentId,
        monthly: Money,
    ): DomainResult<Agent>
}

/** Heartbeat runs, their events and log (PRD FR-075; research R1). */
interface RunGateway {
    suspend fun get(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun>

    suspend fun events(
        scope: CompanyScope,
        id: RunId,
        afterSeq: Long,
    ): DomainResult<List<RunEvent>>

    suspend fun log(
        scope: CompanyScope,
        id: RunId,
        offset: Long,
    ): DomainResult<LogChunk>

    suspend fun cancel(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun>

    suspend fun forIssue(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<HeartbeatRun>>

    suspend fun forAgent(
        scope: CompanyScope,
        agentId: AgentId,
    ): DomainResult<List<HeartbeatRun>>

    /** Runs that are queued or running now, with the agent name and the issue they work on. */
    suspend fun live(scope: CompanyScope): DomainResult<List<HeartbeatRun>>
}

/** Factory home (PRD FR-078; research D1). */
interface DashboardGateway {
    suspend fun dashboard(scope: CompanyScope): DomainResult<DashboardSummary>

    suspend fun windowSpend(scope: CompanyScope): DomainResult<List<SpendRow>>
}
