package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.agent.AgentView
import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Agent
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.Permissions
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

private data class DetailModel(
    val view: AgentView? = null,
    val role: Role? = null,
    val loading: Boolean = false,
    val budgetAmount: Long? = null,
    val budgetIssue: InvalidKey? = null,
    val budgetSaved: Int = 0,
    val confirmPause: Boolean = false,
    val busy: Boolean = false,
    /** Counts finished actions. A read that started before one finished must not replace its result (F-B-7). */
    val version: Int = 0,
    val loadedAtMillis: Long? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

/** State holder for Agent detail (screen 11): role, manager, current issue, runs, month spend and actions. */
class AgentDetailStateHolder(
    private val useCases: AgentDetailUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
    agentId: String,
) {
    private val scope = childScope(scope)
    private val companyScope = target.toScope()
    private val id = AgentId(agentId)
    private val context = ContextSource(useCases.resolveContext, companyScope)
    private val model = MutableStateFlow(DetailModel())
    private val mutableState = MutableStateFlow(AgentDetailState())
    val state: StateFlow<AgentDetailState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(AgentDetailIntent.Refresh) },
            {
                (it is Invalidation.Agent && it.agentId == agentId) ||
                    (it is Invalidation.Run && it.agentId == agentId)
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> renderDetail(model, online) }
                .collect { mutableState.value = it }
        }
        load()
    }

    fun onIntent(intent: AgentDetailIntent) {
        when (intent) {
            AgentDetailIntent.Refresh -> {
                load()
            }

            AgentDetailIntent.PauseTapped -> {
                askToPause()
            }

            AgentDetailIntent.PauseConfirmed -> {
                confirmPause()
            }

            AgentDetailIntent.PauseCancelled -> {
                model.update { it.copy(confirmPause = false) }
            }

            AgentDetailIntent.ResumeTapped -> {
                act(AgentActionUi.Resume)
            }

            AgentDetailIntent.RunNowTapped -> {
                act(AgentActionUi.RunNow)
            }

            is AgentDetailIntent.BudgetAmountTyped -> {
                val parsed = MoneyInput.parse(intent.text, intent.decimalSeparator, intent.fractionDigits)
                model.update { it.copy(budgetAmount = parsed.minorUnits, budgetIssue = parsed.issue) }
            }

            AgentDetailIntent.BudgetSubmitted -> {
                setBudget()
            }

            AgentDetailIntent.MessageDismissed -> {
                model.update { it.copy(feedback = null) }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun load() {
        if (!connectivity.isOnline.value) return
        if (!model.claim({ it.loading }) { it.copy(loading = true, error = null) }) return
        val started = model.value.version
        scope.launch {
            val allowed = context.get()
            if (allowed is DomainResult.Failure) {
                model.update { it.copy(error = allowed.error.toErrorUi()) }
            } else {
                when (val result = useCases.load(companyScope, id)) {
                    is DomainResult.Failure -> {
                        val role = context.roleAfter(result.error)
                        model.update { it.copy(error = result.error.toErrorUi(), role = role ?: it.role) }
                    }

                    is DomainResult.Success -> {
                        model.update {
                            if (it.version != started) {
                                it
                            } else {
                                it.copy(
                                    view = result.value,
                                    role = context.role,
                                    loadedAtMillis = clock.now().toEpochMilliseconds(),
                                )
                            }
                        }
                    }
                }
            }
            model.update { it.copy(loading = false) }
        }
    }

    private fun current(): Agent? = model.value.view?.agent

    private fun allowed(action: AgentActionUi): Boolean {
        val agent = current() ?: return false
        return !model.value.busy && action in agent.actionsFor(model.value.role, connectivity.isOnline.value)
    }

    private fun askToPause() {
        if (allowed(AgentActionUi.Pause)) model.update { it.copy(confirmPause = true, feedback = null) }
    }

    private fun confirmPause() {
        if (!model.value.confirmPause) return
        model.update { it.copy(confirmPause = false) }
        act(AgentActionUi.Pause)
    }

    private fun act(action: AgentActionUi) {
        val agent = current() ?: return
        if (!allowed(action)) return
        run {
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
                    ) { useCases.invoke(it, agent) }.let { ActionResult(it.feedback, null, role = it.role) }
                }
            }
        }
    }

    private fun setBudget() {
        val agent = current() ?: return
        val minorUnits = model.value.budgetAmount ?: return
        val mayAct = model.value.role?.let { Permissions.allows(it, Action.SetAgentBudget) } == true
        val idle = !model.value.busy && connectivity.isOnline.value
        if (!mayAct || !idle) return
        run(budget = true) {
            mutate(context) { useCases.setBudget(it, agent, Money(minorUnits, agent.monthlyBudget.currency)) }
        }
    }

    private fun run(
        budget: Boolean = false,
        call: suspend () -> ActionResult<Agent>,
    ) {
        if (!model.claim({ it.busy }) { it.copy(busy = true, feedback = null) }) return
        scope.launch {
            val result = call()
            model.update { m ->
                m.copy(
                    busy = false,
                    role = result.role ?: m.role,
                    version = m.version + 1,
                    feedback = result.feedback,
                    budgetSaved = if (budget && result.feedback == null) m.budgetSaved + 1 else m.budgetSaved,
                    budgetAmount = if (budget && result.feedback == null) null else m.budgetAmount,
                    view =
                        result.value?.let { agent -> m.view?.copy(agent = agent) } ?: m.view,
                )
            }
        }
    }
}

private fun renderDetail(
    model: DetailModel,
    online: Boolean,
): AgentDetailState {
    val view = model.view
    val role = model.role
    val actions = if (model.busy) emptyList() else view?.agent?.actionsFor(role, online).orEmpty()
    return AgentDetailState(
        phase =
            when {
                view != null -> DetailPhase.Content
                model.loading -> DetailPhase.Loading
                else -> DetailPhase.Failed
            },
        agent = view?.agent?.toRow(actions),
        reportsToName = view?.reportsToName,
        currentRun = view?.currentRun?.let { run -> run.toRow(view.issueRefs[run.issueId] ?: view.currentIssue) },
        currentIssue = view?.currentIssue?.toUi(),
        recentRuns = view?.recentRuns.orEmpty().map { it.toRow(view?.issueRefs?.get(it.issueId)) },
        monthSpend = view?.agent?.monthSpend?.toUi(),
        monthlyBudget = view?.agent?.monthlyBudget?.toUi(),
        budgetPercentUsed = view?.agent?.budgetPercentUsed ?: 0,
        actions = actions,
        canSetBudget = !model.busy && mayAct(online, role, Action.SetAgentBudget),
        budgetAmountMinorUnits = model.budgetAmount,
        budgetAmountIssue = model.budgetIssue,
        canSaveBudget =
            model.budgetAmount != null && !model.busy && mayAct(online, role, Action.SetAgentBudget),
        budgetSavedCount = model.budgetSaved,
        confirmPause = model.confirmPause,
        isBusy = model.busy,
        role = role?.toUi(),
        isOffline = !online,
        actionsDisabledReason = disabledReason(online, role, Action.PauseAgent),
        budgetDisabledReason = disabledReason(online, role, Action.SetAgentBudget),
        updatedAtEpochMillis = model.loadedAtMillis,
        refreshIntervalSeconds = if (view?.currentRun != null) RUNNING_REFRESH_SECONDS else SCREEN_REFRESH_SECONDS,
        error = model.error,
        feedback = model.feedback,
    )
}
