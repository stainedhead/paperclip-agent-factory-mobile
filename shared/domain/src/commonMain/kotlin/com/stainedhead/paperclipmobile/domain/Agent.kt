package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

enum class PauseReason {
    Manual,
    Budget,
    System,
    Other,
    ;

    companion object {
        fun fromServer(raw: String?): PauseReason? =
            when (raw) {
                null -> null
                "manual" -> Manual
                "budget" -> Budget
                "system" -> System
                else -> Other
            }
    }
}

/** The sections of the Agents tab, in display order (PRD FR-070). */
enum class AgentGroup {
    NeedsAttention,
    Running,
    Paused,
    Idle,
}

data class Agent(
    val id: AgentId,
    val name: String,
    val role: String,
    val title: String?,
    val status: AgentStatus,
    val lifecycleState: String?,
    val reportsToId: AgentId?,
    val pauseReason: PauseReason?,
    val monthlyBudget: Money,
    val monthSpend: Money,
    val lastHeartbeatAt: Instant?,
) {
    /** Null for a terminated agent, which the tab does not list. */
    val group: AgentGroup?
        get() =
            when {
                status == AgentStatus.Terminated -> null
                needsAttention -> AgentGroup.NeedsAttention
                status == AgentStatus.Running -> AgentGroup.Running
                status == AgentStatus.Paused -> AgentGroup.Paused
                else -> AgentGroup.Idle
            }

    private val needsAttention: Boolean
        get() = status == AgentStatus.Error || status == AgentStatus.PendingApproval || isBudgetPaused

    /** `status == paused && pauseReason == budget`, as the web app decides (research A1). */
    val isBudgetPaused: Boolean get() = status == AgentStatus.Paused && pauseReason == PauseReason.Budget

    /** The lifecycle state, unless it is the normal `ready` (PRD FR-070). */
    val visibleLifecycle: String? get() = lifecycleState?.takeUnless { it == "ready" }

    /** A monthly budget of zero means none (research A2). */
    val hasBudget: Boolean get() = monthlyBudget.minorUnits > 0

    /** Whole percent of the monthly budget used, not capped; 0 without a budget. */
    val budgetPercentUsed: Int
        get() = if (hasBudget) (monthSpend.minorUnits * PERCENT / monthlyBudget.minorUnits).toInt() else 0

    /** The server is applying a resume: it answers `paused` with `lifecycleState` `resuming` for a moment (ADR-074). */
    val isResuming: Boolean get() = lifecycleState == RESUMING

    /** The server is applying a pause. */
    val isPausing: Boolean get() = lifecycleState == PAUSING

    /**
     * A budget pause is lifted by the budget incident, not by Resume (web app, research A1). While a resume is being
     * applied there is nothing to press: a second tap would send a second mutation (review1b F-A-6).
     */
    val canResume: Boolean get() = status == AgentStatus.Paused && !isBudgetPaused && !isResuming
    val canPause: Boolean get() =
        (status == AgentStatus.Idle || status == AgentStatus.Active || status == AgentStatus.Running) && !isPausing
    val canRunNow: Boolean get() = canPause || status == AgentStatus.Error

    private companion object {
        const val PERCENT = 100
        const val RESUMING = "resuming"
        const val PAUSING = "pausing"
    }
}

data class AgentSection(
    val group: AgentGroup,
    val agents: List<Agent>,
)

object AgentGrouping {
    fun group(agents: List<Agent>): List<AgentSection> =
        AgentGroup.entries.mapNotNull { group ->
            val members = agents.filter { it.group == group }.sortedBy { it.name.lowercase() }
            if (members.isEmpty()) null else AgentSection(group, members)
        }
}
