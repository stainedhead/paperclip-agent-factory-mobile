package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** One incident row of the stub: [observed] and [limit] are the literals the server would send. */
internal data class StubIncident(
    val id: String,
    val company: String,
    val policyId: String,
    var status: String = "open",
    val observed: String = "10000",
    val limit: String = "10000",
    val scopeName: String = "Engineer 2",
    val scopeType: String = "agent",
    val threshold: String = "hard",
    val approvalId: String? = null,
)

/**
 * `budgets/overview` and `budget-incidents/{id}/resolve` as the pinned server does them (`routes/costs.ts:346,366`,
 * `services/budgets.ts:947-1052`). Deliberately as permissive as the server: resolving an incident that is no longer
 * open is a 200 there (keep_paused has no status filter), so the adapter's own guard is what the tests prove.
 */
internal class StubBudgets {
    val incidents = mutableListOf<StubIncident>()

    /** Makes a raise fail the way `budgets.ts:976-978` does. */
    var unpricedUsage = false

    /** Makes a raise fail the way `budgets.ts:979` does. */
    var pendingRuns = false

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        return when {
            request.method == HttpMethod.Get && s.size == OVERVIEW_SIZE && s[0] == "companies" && s[2] == "budgets" &&
                s[3] == "overview" -> overview(s[1])

            request.method == HttpMethod.Post && s.size == RESOLVE_SIZE && s[2] == "budget-incidents" &&
                s[4] == "resolve" -> resolve(request, s[1], s[3])

            else -> null
        }
    }

    private fun overview(company: String): StubReply =
        StubReply.ok(
            BudgetFixtures.overview(
                company,
                incidents.filter { it.company == company && it.status == "open" }.map { BudgetFixtures.incident(it) },
            ),
        )

    private fun resolve(
        request: StubRequest,
        company: String,
        id: String,
    ): StubReply {
        val incident = incidents.firstOrNull { it.id == id && it.company == company }
        val body = request.jsonBody()
        val action = body?.string("action")
        val amount = (body?.get("amount") as? JsonPrimitive)?.takeIf { !it.isString }
        val invalid =
            body == null || action !in ACTIONS || (action == RAISE && amount == null) ||
                (amount != null && (amount.intOrNull == null || amount.intOrNull!! < 0))
        return when {
            invalid -> {
                StubReply.invalid()
            }

            incident == null -> {
                StubReply.notFound("Budget incident not found")
            }

            action == RAISE -> {
                raise(incident, amount!!.intOrNull!!)
            }

            else -> {
                incident.status = "dismissed"
                StubReply.ok(BudgetFixtures.incident(incident))
            }
        }
    }

    private fun raise(
        incident: StubIncident,
        amount: Int,
    ): StubReply {
        val observedWhole = incident.observed.substringBefore('.').toLong()
        return when {
            unpricedUsage -> {
                StubReply.unprocessable(
                    "Resolve unpriced usage or explicitly allow it in the budget policy before resuming",
                )
            }

            pendingRuns -> {
                StubReply.unprocessable("Completed runs must finish accounting before resuming")
            }

            observedWhole >= amount -> {
                StubReply.unprocessable("New budget must exceed current observed spend")
            }

            else -> {
                incidents.filter { it.policyId == incident.policyId && it.status == "open" }.forEach {
                    it.status = "resolved"
                }
                incident.status = "resolved"
                StubReply.ok(BudgetFixtures.incident(incident))
            }
        }
    }

    private companion object {
        const val OVERVIEW_SIZE = 4
        const val RESOLVE_SIZE = 5
        const val RAISE = "raise_budget_and_resume"
        val ACTIONS = setOf("keep_paused", RAISE)
    }
}
