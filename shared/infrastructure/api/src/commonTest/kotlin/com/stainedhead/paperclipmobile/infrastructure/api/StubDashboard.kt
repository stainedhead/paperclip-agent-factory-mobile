package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod

/** One company's dashboard numbers; the spend and budget are the literals the server would print. */
internal data class StubDashboardRow(
    val company: String,
    val active: Int = 4,
    val running: Int = 1,
    val paused: Int = 2,
    val error: Int = 1,
    val open: Int = 5,
    val inProgress: Int = 1,
    val blocked: Int = 1,
    val done: Int = 1,
    val monthSpend: String = "312000",
    val monthBudget: String = "500000",
    val pendingApprovals: Int = 2,
    val activeIncidents: Int = 1,
)

/** One `window-spend` row: [exact] is the text sum, [costCents] the double the server prints for the same sum. */
internal data class StubSpendRow(
    val provider: String,
    val window: String,
    val costCents: String,
    val exact: String = costCents,
) {
    fun hours() =
        when (window) {
            "5h" -> 5
            "24h" -> 24
            else -> 168
        }
}

/**
 * `GET /companies/{c}/dashboard` and `.../costs/window-spend` (`routes/dashboard.js:17`, `routes/costs.js:296`). The
 * dashboard route only checks company access; window-spend also needs `company_scope:read`, which every member holds,
 * a viewer included (`services/authorization.js:1452-1477`).
 */
internal class StubDashboard {
    val rows = mutableListOf<StubDashboardRow>()
    val spend = mutableListOf<StubSpendRow>()

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        if (request.method != HttpMethod.Get || s.size < MIN || s[0] != "companies") return null
        return when {
            s.size == MIN && s[2] == "dashboard" -> {
                rows.firstOrNull { it.company == s[1] }?.let { StubReply.ok(DashboardFixtures.summary(it)) }
                    ?: StubReply.ok(DashboardFixtures.summary(StubDashboardRow(s[1])))
            }

            s.size == SPEND && s[2] == "costs" && s[3] == "window-spend" -> {
                StubReply.ok(spend.joinToString(",", "[", "]") { DashboardFixtures.spendRow(it) })
            }

            else -> {
                null
            }
        }
    }

    private companion object {
        const val MIN = 3
        const val SPEND = 4
    }
}
