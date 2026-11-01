package com.stainedhead.paperclipmobile.domain

data class AgentCounts(
    val active: Int,
    val running: Int,
    val paused: Int,
    val error: Int,
)

data class TaskCounts(
    val open: Int,
    val inProgress: Int,
    val blocked: Int,
    val done: Int,
)

/** The tiles of Factory home (PRD FR-078, research D1). */
data class DashboardSummary(
    val agents: AgentCounts,
    val tasks: TaskCounts,
    val monthSpend: Money,
    val monthBudget: Money,
    val pendingApprovals: Int,
    val activeBudgetIncidents: Int,
) {
    val hasBudget: Boolean get() = monthBudget.minorUnits > 0

    /** Whole percent of the month budget spent; 0 without a budget. */
    val monthPercentUsed: Int
        get() = if (hasBudget) (monthSpend.minorUnits * PERCENT / monthBudget.minorUnits).toInt() else 0

    private companion object {
        const val PERCENT = 100
    }
}

enum class SpendWindowKind(
    val serverName: String,
) {
    Last5Hours("5h"),
    Last24Hours("24h"),
    Last7Days("7d"),
}

data class SpendWindow(
    val window: SpendWindowKind,
    val cost: Money,
)

/** One row of `window-spend`: one per window and provider (research D1). */
data class SpendRow(
    val window: String,
    val costCents: Long,
)

object SpendWindows {
    /** Always the three windows, in order; windows with no rows are zero and unknown windows are ignored. */
    fun fromRows(rows: List<SpendRow>): List<SpendWindow> =
        SpendWindowKind.entries.map { kind ->
            SpendWindow(kind, Money.usdCents(rows.filter { it.window == kind.serverName }.sumOf { it.costCents }))
        }
}
