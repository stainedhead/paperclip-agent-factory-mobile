package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentGrouping
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class AgentsModel(
    val agents: List<Agent> = emptyList(),
    val role: Role? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val confirmPause: AgentId? = null,
    val busy: AgentId? = null,
    val loadedAtMillis: Long? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

/** State holder for the Agents tab (screen 6): groups, chips and row actions by role and state (PRD FR-070, FR-071). */
class AgentsStateHolder(
    private val useCases: AgentsUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
) {
    private val scope = childScope(scope)
    private val companyScope = target.toScope()
    private val context = ContextSource(useCases.resolveContext, companyScope)
    private val model = MutableStateFlow(AgentsModel())
    private val mutableState = MutableStateFlow(AgentsState())
    val state: StateFlow<AgentsState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(AgentsIntent.Refresh) },
            {
                it is Invalidation.Agent || it is Invalidation.Run
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> render(model, online) }
                .collect { mutableState.value = it }
        }
        load()
    }

    fun onIntent(intent: AgentsIntent) {
        when (intent) {
            AgentsIntent.Refresh -> load()
            is AgentsIntent.PauseTapped -> askToPause(intent.agentId)
            AgentsIntent.PauseConfirmed -> confirmPause()
            AgentsIntent.PauseCancelled -> model.update { it.copy(confirmPause = null) }
            is AgentsIntent.ResumeTapped -> act(intent.agentId, AgentActionUi.Resume)
            is AgentsIntent.RunNowTapped -> act(intent.agentId, AgentActionUi.RunNow)
            AgentsIntent.MessageDismissed -> model.update { it.copy(feedback = null) }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun load() {
        if (!connectivity.isOnline.value || model.value.loading) return
        model.update { it.copy(loading = true, error = null) }
        scope.launch {
            val allowed = context.get()
            if (allowed is DomainResult.Failure) {
                model.update { it.copy(error = allowed.error.toErrorUi()) }
            } else {
                when (val result = useCases.list(companyScope)) {
                    is DomainResult.Failure -> {
                        model.update { it.copy(error = result.error.toErrorUi()) }
                    }

                    is DomainResult.Success -> {
                        model.update {
                            it.copy(
                                agents = result.value.agents,
                                role = context.role,
                                loaded = true,
                                loadedAtMillis = clock.now().toEpochMilliseconds(),
                            )
                        }
                    }
                }
            }
            model.update { it.copy(loading = false) }
        }
    }

    private fun askToPause(agentId: String) {
        val agent = model.value.agents.firstOrNull { it.id.value == agentId } ?: return
        val allowed = agent.actionsFor(model.value.role, connectivity.isOnline.value)
        if (AgentActionUi.Pause in allowed && model.value.busy == null) {
            model.update { it.copy(confirmPause = agent.id, feedback = null) }
        }
    }

    private fun confirmPause() {
        val id = model.value.confirmPause ?: return
        model.update { it.copy(confirmPause = null) }
        act(id.value, AgentActionUi.Pause)
    }

    private fun act(
        agentId: String,
        action: AgentActionUi,
    ) {
        val current = model.value
        val agent = current.agents.firstOrNull { it.id.value == agentId } ?: return
        if (current.busy != null || action !in agent.actionsFor(current.role, connectivity.isOnline.value)) return
        model.update { it.copy(busy = agent.id, feedback = null) }
        scope.launch {
            val result =
                when (action) {
                    AgentActionUi.Pause -> {
                        mutate(context) { useCases.pause(it, agent) }
                    }

                    AgentActionUi.Resume -> {
                        mutate(context) { useCases.resume(it, agent) }
                    }

                    AgentActionUi.RunNow -> {
                        mutate(
                            context,
                        ) { useCases.invoke(it, agent) }.let { ActionResult(it.feedback, null) }
                    }
                }
            model.update { m ->
                m.copy(
                    busy = null,
                    feedback = result.feedback,
                    agents =
                        result.value?.let { updated -> m.agents.map { if (it.id == updated.id) updated else it } }
                            ?: m.agents,
                )
            }
        }
    }
}

private fun render(
    model: AgentsModel,
    online: Boolean,
): AgentsState =
    AgentsState(
        phase =
            when {
                model.loaded -> DetailPhase.Content
                model.loading -> DetailPhase.Loading
                else -> DetailPhase.Failed
            },
        sections =
            AgentGrouping.group(model.agents).map { section ->
                AgentSectionUi(
                    section.group.toUi(),
                    section.agents.map {
                        it.toRow(
                            if (model.busy ==
                                null
                            ) {
                                it.actionsFor(model.role, online)
                            } else {
                                emptyList()
                            },
                        )
                    },
                )
            },
        confirmPauseAgentId = model.confirmPause?.value,
        busyAgentId = model.busy?.value,
        role = model.role?.toUi(),
        isOffline = !online,
        actionsDisabledReason = disabledReason(online, model.role, Action.PauseAgent),
        updatedAtEpochMillis = model.loadedAtMillis,
        error = model.error,
        feedback = model.feedback,
    )
