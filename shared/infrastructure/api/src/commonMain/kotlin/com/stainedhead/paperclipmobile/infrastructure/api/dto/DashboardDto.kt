package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.AgentCounts
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.SpendRow
import com.stainedhead.paperclipmobile.domain.TaskCounts
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Shapes from the pinned release 2026.1023.0 (compiled dist): `server/dist/services/dashboard.js:150-195` for the
// summary and `server/dist/services/costs.js:502-538` for the window rows (research, 1b-4b D1).

/** `GET /companies/{c}/dashboard`. The groups are read by hand so one odd field cannot hide the others' meaning. */
@Serializable
internal data class DashboardDto(
    val agents: JsonObject? = null,
    val tasks: JsonObject? = null,
    val costs: JsonObject? = null,
    val pendingApprovals: Int? = null,
    val budgets: JsonObject? = null,
)

/**
 * Null (so Malformed) when a tile cannot be read exactly: a missing, negative or non-integer count, or money that is
 * not a plain number. A tile is never guessed (principle 2). `idle` agents are already folded into `active` by the
 * server, and unknown extra keys such as `pending_approval` or `runActivity` are ignored.
 */
internal fun DashboardDto.toDomain(): DashboardSummary? {
    val cost = costs ?: return null
    val spend = cost.cents("monthSpendCents")?.let(Money::usdCents)
    val budget = cost.cents("monthBudgetCents")?.let(Money::usdCents)
    val incidents = budgets?.count("activeIncidents")
    val pending = pendingApprovals?.takeIf { it >= 0 }
    val agentCounts = agents?.toAgentCounts()
    val taskCounts = tasks?.toTaskCounts()
    return if (spend == null || budget == null) {
        null
    } else if (incidents == null || pending == null) {
        null
    } else if (agentCounts == null || taskCounts == null) {
        null
    } else {
        DashboardSummary(agentCounts, taskCounts, spend, budget, pending, incidents)
    }
}

private fun JsonObject.toAgentCounts(): AgentCounts? =
    AgentCounts(
        active = count("active") ?: return null,
        running = count("running") ?: return null,
        paused = count("paused") ?: return null,
        error = count("error") ?: return null,
    )

private fun JsonObject.toTaskCounts(): TaskCounts? =
    TaskCounts(
        open = count("open") ?: return null,
        inProgress = count("inProgress") ?: return null,
        blocked = count("blocked") ?: return null,
        done = count("done") ?: return null,
    )

private fun JsonObject.count(name: String): Int? = whole(name)?.takeIf { it >= 0 }

/**
 * One row per provider and window. The exact text sum is preferred over the double next to it, which can print in
 * exponent form. A row whose window or cost cannot be read makes the list Malformed: a missing row would understate
 * what was spent.
 */
internal fun spendRows(rows: List<JsonObject>): List<SpendRow>? {
    val mapped =
        rows.map { row ->
            val window = row.text("window")
            val cost = row.text("costCentsExact")?.let(::ceilToCents) ?: row.cents("costCents")
            if (window == null || cost == null) null else SpendRow(window, cost)
        }
    return if (mapped.any { it == null }) null else mapped.filterNotNull()
}
