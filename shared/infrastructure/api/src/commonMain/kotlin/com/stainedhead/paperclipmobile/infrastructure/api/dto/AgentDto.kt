package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

// Shape from the pinned release 2026.1023.0 (compiled dist): the raw `agents` row (`db/dist/schema/agents.js:5-35`)
// after `hydrateAgentSpend` (`server/dist/lib/agent-records.js:269-279`). Config blobs, permissions and the rest are
// not declared, so they are never read.

/**
 * One agent as `GET /companies/{c}/agents`, `GET /agents/{id}` and the mutations send it. The two money fields are
 * kept as the JSON number so the text can be rounded without a float: `budgetMonthlyCents` is an integer column and
 * `spentMonthlyCents` a `::double precision` sum that may print as `5e-7`.
 */
@Serializable
internal data class AgentDto(
    val id: String? = null,
    val companyId: String? = null,
    val name: String? = null,
    val role: String? = null,
    val title: String? = null,
    val status: String? = null,
    val lifecycleState: String? = null,
    val reportsTo: String? = null,
    val budgetMonthlyCents: JsonPrimitive? = null,
    val spentMonthlyCents: JsonPrimitive? = null,
    val pauseReason: String? = null,
    val lastHeartbeatAt: String? = null,
)

/** Null (so Malformed) when the identity, status or money cannot be read; an unknown status is `Unknown`. */
internal fun AgentDto.toDomain(): Agent? {
    val agentId = id?.takeIf { it.isNotBlank() } ?: return null
    val label = name?.takeIf { it.isNotBlank() } ?: return null
    val budget = budgetMonthlyCents?.let { ceilToCents(it.content) }
    val spent = spentMonthlyCents?.let { ceilToCents(it.content) }
    if (status == null || budget == null || spent == null) return null
    return Agent(
        id = AgentId(agentId),
        name = label,
        role = role.orEmpty(),
        title = title?.takeIf { it.isNotBlank() },
        status = AgentStatus.fromServer(status),
        lifecycleState = lifecycleState?.takeIf { it.isNotBlank() },
        reportsToId = reportsTo?.takeIf { it.isNotBlank() }?.let(::AgentId),
        pauseReason = PauseReason.fromServer(pauseReason?.takeIf { it.isNotBlank() }),
        monthlyBudget = Money.usdCents(budget),
        monthSpend = Money.usdCents(spent),
        lastHeartbeatAt = parseInstantOrNull(lastHeartbeatAt),
    )
}
