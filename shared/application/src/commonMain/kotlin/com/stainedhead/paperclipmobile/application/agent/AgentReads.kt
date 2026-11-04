package com.stainedhead.paperclipmobile.application.agent

import com.stainedhead.paperclipmobile.application.issue.LoadIssueRefs
import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentGrouping
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentSection
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueRef
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.domain.valueOrNull

/** The Agents tab: grouped sections, and every agent so a screen can name a manager (PRD FR-070). */
data class AgentRoster(
    val sections: List<AgentSection>,
    val agents: List<Agent>,
    /** The runs in progress, so a row can show what the agent is doing now. Best effort: empty when unreadable. */
    val liveRuns: List<HeartbeatRun>,
    val issueRefs: Map<IssueId, IssueRef>,
)

class ListAgents(
    private val gateway: AgentGateway,
    private val runs: RunGateway,
    private val refs: LoadIssueRefs,
) {
    suspend operator fun invoke(scope: CompanyScope): DomainResult<AgentRoster> =
        when (val result = retryingRead { gateway.list(scope) }) {
            is DomainResult.Failure -> {
                result
            }

            is DomainResult.Success -> {
                val live = runs.live(scope).valueOrNull().orEmpty()
                DomainResult.Success(
                    AgentRoster(
                        AgentGrouping.group(result.value),
                        result.value,
                        live,
                        refs(scope, live.map { it.issueId }),
                    ),
                )
            }
        }
}

/** Agent detail (PRD FR-072). Everything except the agent itself is best effort. */
data class AgentView(
    val agent: Agent,
    val reportsToName: String?,
    val currentRun: HeartbeatRun?,
    val currentIssue: IssueRef?,
    val recentRuns: List<HeartbeatRun>,
    /** The issues of the current and recent runs, by id. */
    val issueRefs: Map<IssueId, IssueRef>,
)

class LoadAgent(
    private val agents: AgentGateway,
    private val runs: RunGateway,
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<AgentView> {
        val agent =
            when (val read = retryingRead { agents.get(scope, id) }) {
                is DomainResult.Failure -> return read
                is DomainResult.Success -> read.value
            }
        val manager = agent.reportsToId?.let { boss -> agents.list(scope).valueOrNull()?.firstOrNull { it.id == boss } }
        val current = runs.live(scope).valueOrNull()?.firstOrNull { it.agentId == id }
        val issue =
            current?.issueId?.let { issues.get(scope, it).valueOrNull() }?.let { detail ->
                IssueRef(detail.issue.id, detail.issue.identifier, detail.issue.title, detail.issue.status)
            }
        val recent =
            runs
                .forAgent(scope, id)
                .valueOrNull()
                .orEmpty()
                .sortedByDescending { it.createdAt }
        val refs = LoadIssueRefs(issues)(scope, recent.map { it.issueId })
        return DomainResult.Success(AgentView(agent, manager?.name, current, issue, recent, refs))
    }
}
