package com.stainedhead.paperclipmobile.infrastructure.api

/**
 * Budget bodies in the shapes of the pinned server, written from source (not recorded): `BudgetOverview` and
 * `BudgetIncident` (`packages/shared/src/types/budget.ts:61-88`, built at `server/src/services/budgets.ts:604-626` and
 * `947-957`). `amountObserved` is a `numeric(24,7)` column read as a JS number (`budget_incidents.ts:21`), so it may be
 * fractional; `amountLimit` is an integer. Dates are ISO strings.
 *
 * Re-verified in 1b-4b against the pinned release 2026.1023.0 (compiled dist): `server/dist/services/
 * budgets.js:395-428,597-626,697-790`, `db/dist/schema/budget_incidents.js`,
 * `server/dist/routes/costs.js:139-148,296-302,318-340,363-377`.
 */
internal object BudgetFixtures {
    fun incident(row: StubIncident): String {
        val approval = if (row.approvalId == null) "null" else "\"${row.approvalId}\""
        return """
            {"id":"${row.id}","companyId":"${row.company}","policyId":"${row.policyId}","scopeType":"${row.scopeType}",
             "scopeId":"scope-${row.id}","scopeName":"${row.scopeName}","metric":"billed_cents",
             "windowKind":"calendar_month_utc","windowStart":"2026-11-01T00:00:00.000Z",
             "windowEnd":"2026-12-01T00:00:00.000Z","thresholdType":"${row.threshold}","amountLimit":${row.limit},
             "amountObserved":${row.observed},"status":"${row.status}","approvalId":$approval,"approvalStatus":null,
             "resolvedAt":null,"createdAt":"2026-11-01T09:00:00.000Z","updatedAt":"2026-11-01T11:00:00.000Z",
             "somethingNew":{"a":1}}
            """.trimIndent()
    }

    fun overview(
        company: String,
        incidents: List<String>,
    ) = """
        {"companyId":"$company","policies":[],"activeIncidents":[${incidents.joinToString(",")}],
         "pausedAgentCount":0,"pausedProjectCount":0,"pendingApprovalCount":0}
        """.trimIndent()
}
