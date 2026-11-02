package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.agent.InvokeHeartbeat
import com.stainedhead.paperclipmobile.application.agent.ListAgents
import com.stainedhead.paperclipmobile.application.agent.LoadAgent
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.agent.ResumeAgent
import com.stainedhead.paperclipmobile.application.agent.SetAgentBudget
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentGroup

enum class AgentGroupUi {
    NeedsAttention,
    Running,
    Paused,
    Idle,
}

internal fun AgentGroup.toUi() = AgentGroupUi.valueOf(name)

/** A button on an agent row or the detail screen (PRD FR-071). */
enum class AgentActionUi {
    Pause,
    Resume,
    RunNow,
}

/** One row of the Agents tab. Chips: [status], [lifecycleState] when not ready, and [isBudgetPaused]. */
data class AgentRowUi(
    val id: String,
    val name: String,
    val role: String,
    val status: AgentStatusUi,
    val lifecycleState: String?,
    val isBudgetPaused: Boolean,
    val hasBudget: Boolean,
    val budgetPercentUsed: Int,
    val lastHeartbeatAtEpochMillis: Long?,
    val actions: List<AgentActionUi>,
)

data class AgentSectionUi(
    val group: AgentGroupUi,
    val rows: List<AgentRowUi>,
)

data class AgentsState(
    val phase: DetailPhase = DetailPhase.Loading,
    val sections: List<AgentSectionUi> = emptyList(),
    /** Set while the person is asked to confirm a pause (PRD FR-071, FR-014). */
    val confirmPauseAgentId: String? = null,
    val busyAgentId: String? = null,
    val role: RoleUi? = null,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val updatedAtEpochMillis: Long? = null,
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

sealed interface AgentsIntent {
    data object Refresh : AgentsIntent

    data class PauseTapped(
        val agentId: String,
    ) : AgentsIntent

    data object PauseConfirmed : AgentsIntent

    data object PauseCancelled : AgentsIntent

    data class ResumeTapped(
        val agentId: String,
    ) : AgentsIntent

    data class RunNowTapped(
        val agentId: String,
    ) : AgentsIntent

    data object MessageDismissed : AgentsIntent
}

data class AgentsUseCases(
    val resolveContext: ResolveCompanyContext,
    val list: ListAgents,
    val pause: PauseAgent,
    val resume: ResumeAgent,
    val invoke: InvokeHeartbeat,
)

data class AgentDetailUseCases(
    val resolveContext: ResolveCompanyContext,
    val load: LoadAgent,
    val pause: PauseAgent,
    val resume: ResumeAgent,
    val invoke: InvokeHeartbeat,
    val setBudget: SetAgentBudget,
)

/** The actions an agent offers to [role] right now; none for a viewer or without a connection (PRD FR-071). */
internal fun Agent.actionsFor(
    role: com.stainedhead.paperclipmobile.domain.Role?,
    online: Boolean,
): List<AgentActionUi> =
    listOfNotNull(
        AgentActionUi.Pause.takeIf { canPause && mayAct(online, role, Action.PauseAgent) },
        AgentActionUi.Resume.takeIf { canResume && mayAct(online, role, Action.ResumeAgent) },
        AgentActionUi.RunNow.takeIf { canRunNow && mayAct(online, role, Action.RunAgentNow) },
    )

internal fun Agent.toRow(actions: List<AgentActionUi>) =
    AgentRowUi(
        id = id.value,
        name = name,
        role = role,
        status = status.toUi(),
        lifecycleState = visibleLifecycle,
        isBudgetPaused = isBudgetPaused,
        hasBudget = hasBudget,
        budgetPercentUsed = budgetPercentUsed,
        lastHeartbeatAtEpochMillis = lastHeartbeatAt?.toEpochMilliseconds(),
        actions = actions,
    )
