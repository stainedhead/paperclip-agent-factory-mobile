package com.stainedhead.paperclipmobile.infrastructure.api

/**
 * Factory-home bodies in the shapes of the pinned release 2026.1023.0 (compiled dist, not recorded): `summary`
 * (`server/dist/services/dashboard.js:150-190`, `routes/dashboard.js:17-21`) and the window-spend rows
 * (`services/costs.js:502-538`). `monthSpendCents` is a JS number and a window row's `costCents` is a
 * `::double precision` sum, so either may print as a fraction or in exponent form; `costCentsExact` is the exact text.
 */
internal object DashboardFixtures {
    fun summary(row: StubDashboardRow) =
        """
        {"companyId":"${row.company}",
         "agents":{"active":${row.active},"running":${row.running},"paused":${row.paused},"error":${row.error},
                   "pending_approval":0},
         "tasks":{"open":${row.open},"inProgress":${row.inProgress},"blocked":${row.blocked},"done":${row.done}},
         "costs":{"monthSpendCents":${row.monthSpend},"monthBudgetCents":${row.monthBudget},
                  "monthUtilizationPercent":62.4},
         "pendingApprovals":${row.pendingApprovals},
         "budgets":{"activeIncidents":${row.activeIncidents},"pendingApprovals":0,"pausedAgents":1,"pausedProjects":0},
         "runActivity":[{"date":"2026-11-01","succeeded":3}],
         "somethingNew":true}
        """.trimIndent()

    fun spendRow(row: StubSpendRow) =
        """
        {"provider":"${row.provider}","biller":"${row.provider}","window":"${row.window}",
         "windowHours":${row.hours()},"costCents":${row.costCents},"costCentsExact":"${row.exact}",
         "inputTokens":10,"cachedInputTokens":0,"outputTokens":5}
        """.trimIndent()
}
