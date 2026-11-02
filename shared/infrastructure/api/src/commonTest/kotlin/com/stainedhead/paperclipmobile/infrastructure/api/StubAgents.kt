package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

internal class StubAgent(
    val id: String,
    val company: String,
    val name: String = "Engineer 1",
    val role: String = "engineer",
    var status: String = "running",
) {
    var title: String? = null
    var lifecycle = "ready"
    var reportsTo: String? = null
    var budget = 100_000L
    var spent = "25000"
    var pauseReason: String? = null
    var lastHeartbeat = "2026-11-01T11:00:00.000Z"
    var invalidOrgChain = false
}

/**
 * The agent endpoints of the pinned release (`routes/agents.js:2980,3189,4379,4401,4864`, `routes/costs.js:381`).
 * Pause, resume and wake need a member who is not a viewer; a viewer is refused by `assertCompanyAccess` before the
 * permission is looked at. A pause cancels the agent's active runs. A wake of an agent that cannot run answers
 * `202 {status:"skipped"}`, like a wake the scheduler declines.
 */
internal class StubAgents(
    private val runs: StubRuns,
    private val clock: () -> Instant,
) {
    val agents = mutableListOf<StubAgent>()

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        return when {
            s.size == COMPANY_AGENTS && s[0] == "companies" && s[2] == "agents" -> list(request, s[1])
            s.size >= 2 && s[0] == "agents" -> byId(request, s)
            else -> null
        }
    }

    private fun list(
        request: StubRequest,
        company: String,
    ): StubReply? {
        if (request.method != HttpMethod.Get) return null
        val extra = request.query.names().sorted()
        if (extra.isNotEmpty()) {
            val noun = if (extra.size == 1) "parameter" else "parameters"
            return StubReply.error(400, "Unsupported query $noun: ${extra.joinToString(", ")}")
        }
        val rows = agents.filter { it.company == company && it.status != "terminated" }
        return StubReply.ok(rows.joinToString(",", "[", "]") { AgentRowFixtures.agent(it) })
    }

    private fun byId(
        request: StubRequest,
        s: List<String>,
    ): StubReply? {
        if (!isServerId(s[1])) return StubReply.error(500, "Internal server error")
        val agent = agents.firstOrNull { it.id == s[1] } ?: return StubReply.notFound(NOT_FOUND)
        request.resourceRefusal(agent.company, NOT_FOUND)?.let { return it }
        return if (s.size == 2) {
            if (request.method == HttpMethod.Get) StubReply.ok(AgentRowFixtures.agent(agent)) else null
        } else {
            act(request, agent, s.drop(2))
        }
    }

    private fun act(
        request: StubRequest,
        agent: StubAgent,
        rest: List<String>,
    ): StubReply? =
        when {
            request.method == HttpMethod.Post && rest == listOf("pause") -> pause(agent)
            request.method == HttpMethod.Post && rest == listOf("resume") -> resume(agent)
            request.method == HttpMethod.Post && rest == listOf("heartbeat", "invoke") -> invoke(agent)
            request.method == HttpMethod.Patch && rest == listOf("budgets") -> budget(request, agent)
            else -> null
        }

    private fun pause(agent: StubAgent): StubReply {
        agent.status = "paused"
        agent.pauseReason = "manual"
        runs.runs.filter { it.agentId == agent.id && it.status in ACTIVE }.forEach { it.status = "cancelled" }
        return StubReply.ok(AgentRowFixtures.agent(agent))
    }

    private fun resume(agent: StubAgent): StubReply {
        if (agent.invalidOrgChain) return StubReply.error(409, "Repair this agent's reporting chain before resuming it")
        agent.status = "idle"
        agent.pauseReason = null
        return StubReply.ok(AgentRowFixtures.agent(agent))
    }

    private fun invoke(agent: StubAgent): StubReply {
        if (agent.invalidOrgChain) {
            return StubReply.error(409, "Repair this agent's reporting chain before starting runs")
        }
        if (agent.status in CANNOT_RUN) return StubReply(202, """{"status":"skipped"}""")
        val run = runs.start(agent.company, agent.id, agent.name)
        agent.lastHeartbeat = clock().toString()
        return StubReply(202, RunFixtures.run(run))
    }

    private fun budget(
        request: StubRequest,
        agent: StubAgent,
    ): StubReply {
        val amount = (request.jsonBody()?.get("budgetMonthlyCents") as? JsonPrimitive)?.takeIf { !it.isString }
        val cents = amount?.content?.toLongOrNull()
        if (cents == null || cents < 0) return StubReply.invalid()
        agent.budget = cents
        return StubReply.ok(AgentRowFixtures.agent(agent))
    }

    private companion object {
        const val COMPANY_AGENTS = 3
        const val NOT_FOUND = "Agent not found"
        val ACTIVE = setOf("queued", "running", "scheduled_retry")
        val CANNOT_RUN = setOf("paused", "terminated", "pending_approval")
    }
}
