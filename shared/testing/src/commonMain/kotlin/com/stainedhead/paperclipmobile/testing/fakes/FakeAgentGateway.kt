package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.InvokeOutcome
import com.stainedhead.paperclipmobile.testing.AgentFixtures

/**
 * Agents with the server's controls (research A2): pause records a manual pause reason, resume goes idle, run now
 * starts a run, set budget stores the cents. [failures] scripts errors by operation name.
 */
class FakeAgentGateway(
    agents: List<Agent> = AgentFixtures.agents(),
) : AgentGateway {
    val store = agents.associateBy { it.id }.toMutableMap()
    val calls = mutableMapOf<String, Int>()
    val failures = mutableMapOf<String, DomainError>()

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun list(scope: CompanyScope): DomainResult<List<Agent>> {
        record("list")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(store.values.toList())
    }

    override suspend fun get(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent> {
        record("get")?.let { return DomainResult.Failure(it) }
        return store[id]?.let { DomainResult.Success(it) } ?: DomainResult.Failure(DomainError.NotFound)
    }

    override suspend fun pause(
        scope: CompanyScope,
        id: AgentId,
    ) = change("pause", id) { it.copy(status = AgentStatus.Paused, pauseReason = PauseReason.Manual) }

    override suspend fun resume(
        scope: CompanyScope,
        id: AgentId,
    ) = change("resume", id) { it.copy(status = AgentStatus.Idle, pauseReason = null) }

    override suspend fun setBudget(
        scope: CompanyScope,
        id: AgentId,
        monthly: Money,
    ) = change("setBudget", id) { it.copy(monthlyBudget = monthly) }

    override suspend fun invokeHeartbeat(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<InvokeOutcome> {
        record("invokeHeartbeat")?.let { return DomainResult.Failure(it) }
        val agent = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        val run = AgentFixtures.run("run-invoked-${id.value}", RunStatus.Queued, agent.id, null)
        return DomainResult.Success(InvokeOutcome.Started(run))
    }

    private fun change(
        name: String,
        id: AgentId,
        update: (Agent) -> Agent,
    ): DomainResult<Agent> {
        record(name)?.let { return DomainResult.Failure(it) }
        val agent = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        return DomainResult.Success(update(agent).also { store[id] = it })
    }
}
