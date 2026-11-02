package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.AgentDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toRunDto
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Agents and their controls (AD-013). Only the list has the company in its path; every other call is addressed by the
 * agent id alone, so each one starts with a read that proves the agent belongs to the scope's company, and a reply
 * that names another company is not trusted (ADR-057, ADR-061). Mutations are sent once after that read and never
 * retried. An id that is not a uuid cannot exist, so it is NotFound without a request.
 */
class AgentGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
) : AgentGateway {
    private val calls = CompanyCalls(client, instances, secrets)

    override suspend fun list(scope: CompanyScope): DomainResult<List<Agent>> =
        calls
            .send(scope.instanceId, HttpMethod.Get, listOf("companies", scope.companyId.value, "agents"))
            .decode<List<AgentDto>>()
            .map { rows -> rows.map { it.toDomain() }.takeIf { mapped -> mapped.none { it == null } }?.filterNotNull() }

    override suspend fun get(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent> = existing(scope, id).map { it.toDomain() }

    override suspend fun pause(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent> = change(scope, id, HttpMethod.Post, "pause", null)

    override suspend fun resume(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<Agent> = change(scope, id, HttpMethod.Post, "resume", null)

    override suspend fun setBudget(
        scope: CompanyScope,
        id: AgentId,
        monthly: Money,
    ): DomainResult<Agent> {
        val refusal = budgetRefusal(monthly)
        if (refusal != null) return DomainResult.Failure(refusal)
        val body = buildJsonObject { put("budgetMonthlyCents", monthly.minorUnits) }
        return change(scope, id, HttpMethod.Patch, "budgets", body.toString())
    }

    override suspend fun invokeHeartbeat(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<InvokeOutcome> {
        val agent = existing(scope, id)
        if (agent is DomainResult.Failure) return agent
        val reply =
            calls
                .send(scope.instanceId, HttpMethod.Post, listOf("agents", id.value, "heartbeat", "invoke"))
                .decode<JsonObject>()
        return reply.map { outcomeOf(scope, it, (agent as DomainResult.Success).value.name) }
    }

    /** A wake answers either the run it started or `{"status":"skipped"}` (`routes/agents.js:4930-4934`, `4975`). */
    private fun outcomeOf(
        scope: CompanyScope,
        reply: JsonObject,
        agentName: String?,
    ): InvokeOutcome? {
        val skipped = (reply["status"] as? JsonPrimitive)?.content == SKIPPED && "id" !in reply
        val run = reply.toRunDto()
        return when {
            skipped -> InvokeOutcome.Skipped
            run?.companyId != scope.companyId.value -> null
            else -> run.toDomain(agentName = agentName)?.let { InvokeOutcome.Started(it) }
        }
    }

    private suspend fun existing(
        scope: CompanyScope,
        id: AgentId,
    ): DomainResult<AgentDto> =
        if (!isServerId(id.value)) {
            DomainResult.Failure(DomainError.NotFound)
        } else {
            calls
                .send(scope.instanceId, HttpMethod.Get, listOf("agents", id.value))
                .decode<AgentDto>()
                .ownedBy(scope) { it.companyId }
        }

    private suspend fun change(
        scope: CompanyScope,
        id: AgentId,
        method: HttpMethod,
        action: String,
        body: String?,
    ): DomainResult<Agent> {
        val before = existing(scope, id)
        if (before is DomainResult.Failure) return before
        return calls
            .send(scope.instanceId, method, listOf("agents", id.value, action), jsonBody = body)
            .decode<AgentDto>()
            .ownedBy(scope) { it.companyId }
            .map { it.toDomain() }
    }

    private fun budgetRefusal(monthly: Money): DomainError? =
        when {
            monthly.currency != Money.USD -> DomainError.Rejected("Only US dollars can be set as an agent budget")
            monthly.minorUnits > MAX_BUDGET -> DomainError.Rejected("The amount is too large")
            else -> null
        }

    private companion object {
        const val SKIPPED = "skipped"
        const val MAX_BUDGET = 2_147_483_647L
    }
}
