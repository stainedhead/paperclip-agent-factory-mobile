package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class WorkModel(
    val filter: IssueFilter = IssueFilter.AllOpen,
    val search: String = "",
    val issues: List<Issue> = emptyList(),
    val names: Map<AgentId, String> = emptyMap(),
    val userId: String? = null,
    val role: Role? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** Rows the server has sent so far: where the next page starts (the device may drop some). */
    val serverRows: Int = 0,
    val creating: Boolean = false,
    val createdId: String? = null,
    val loadedAtMillis: Long? = null,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

/** State holder for the Work list (screen 10): saved filters, search and create (PRD FR-060, FR-062). */
class WorkListStateHolder(
    private val useCases: WorkListUseCases,
    private val connectivity: Connectivity,
    private val clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
) {
    private val scope = childScope(scope)
    private val companyScope = target.toScope()
    private val context = ContextSource(useCases.resolveContext, companyScope)
    private val model = MutableStateFlow(WorkModel())
    private val mutableState = MutableStateFlow(WorkListState())
    private var loadJob: Job? = null
    val state: StateFlow<WorkListState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(WorkListIntent.Refresh) },
            {
                it is Invalidation.Issue
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> renderWork(model, online) }
                .collect { mutableState.value = it }
        }
        reload()
    }

    fun onIntent(intent: WorkListIntent) {
        when (intent) {
            WorkListIntent.Refresh -> if (!model.value.loading) reload()
            is WorkListIntent.FilterSelected -> change { it.copy(filter = intent.filter.toDomain()) }
            is WorkListIntent.SearchChanged -> change { it.copy(search = intent.text.trim()) }
            WorkListIntent.LoadMore -> loadMore()
            is WorkListIntent.CreateSubmitted -> create(intent)
            WorkListIntent.CreatedAcknowledged -> model.update { it.copy(createdId = null) }
            WorkListIntent.MessageDismissed -> model.update { it.copy(feedback = null) }
        }
    }

    fun close() {
        scope.cancel()
    }

    /** Offline a new filter or search is ignored, so the chip never labels rows from another query. */
    private fun change(update: (WorkModel) -> WorkModel) {
        if (!connectivity.isOnline.value) return
        model.update(update)
        loadJob?.cancel()
        model.update { it.copy(loading = false) }
        reload()
    }

    private fun query(offset: Int = 0): IssueQuery =
        IssueQuery(model.value.filter, model.value.search.ifEmpty { null }, offset)

    private fun reload() {
        if (!connectivity.isOnline.value) {
            model.update { it.copy(loading = false) }
            return
        }
        model.update { it.copy(loading = true, error = null) }
        loadJob =
            scope.launch {
                val allowed = context.get()
                if (allowed is DomainResult.Failure) {
                    model.update { it.copy(error = allowed.error.toErrorUi(), loading = false) }
                    return@launch
                }
                val names = namesOrExisting()
                val page = useCases.search(companyScope, model.value.filter, model.value.search, userId())
                when (page) {
                    is DomainResult.Failure -> {
                        model.update { it.copy(error = page.error.toErrorUi(), loading = false) }
                    }

                    is DomainResult.Success -> {
                        model.update {
                            it.copy(
                                issues = page.value.issues,
                                hasMore = page.value.hasMore,
                                serverRows = page.value.serverRowCount,
                                names = names,
                                role = context.role,
                                userId = userId(),
                                loaded = true,
                                loading = false,
                                loadedAtMillis = clock.now().toEpochMilliseconds(),
                            )
                        }
                    }
                }
            }
    }

    private fun userId(): String? = context.userId

    private suspend fun namesOrExisting(): Map<AgentId, String> {
        val agents = useCases.listAgents(companyScope)
        return (agents as? DomainResult.Success)?.value?.agents?.associate { it.id to it.name } ?: model.value.names
    }

    private fun loadMore() {
        val current = model.value
        val idle = !current.loading && !current.loadingMore
        if (!connectivity.isOnline.value || !current.hasMore || !idle) return
        model.update { it.copy(loadingMore = true) }
        scope.launch {
            val next = query(offset = current.serverRows)
            val page = useCases.list(companyScope, next, userId())
            model.update {
                when (page) {
                    is DomainResult.Failure -> {
                        it.copy(loadingMore = false, error = page.error.toErrorUi())
                    }

                    is DomainResult.Success -> {
                        it.copy(
                            issues = it.issues + page.value.issues,
                            hasMore = page.value.hasMore,
                            serverRows = it.serverRows + page.value.serverRowCount,
                            loadingMore = false,
                        )
                    }
                }
            }
        }
    }

    private fun create(intent: WorkListIntent.CreateSubmitted) {
        val current = model.value
        val allowed = current.role?.let { Permissions.allows(it, Action.CreateIssue) } == true
        if (!allowed || !connectivity.isOnline.value || current.creating) return
        model.update { it.copy(creating = true, feedback = null) }
        scope.launch {
            val result =
                mutate(context) { useCases.create(it, NewIssue(intent.title, priority = intent.priority.toDomain())) }
            model.update { it.copy(creating = false, feedback = result.feedback, createdId = result.value?.id?.value) }
            if (needsReload(result)) {
                model.update { it.copy(loading = false) }
                reload()
            }
        }
    }
}

/** After a create the list is read again: it shows the new issue, or tells the truth after a timeout. */
private fun needsReload(result: ActionResult<Issue>) =
    result.value != null || result.feedback?.error?.key == ErrorKey.Timeout

private fun renderWork(
    model: WorkModel,
    online: Boolean,
): WorkListState {
    val role = model.role
    val mayCreate = role != null && Permissions.allows(role, Action.CreateIssue)
    return WorkListState(
        phase =
            when {
                model.loaded -> DetailPhase.Content
                model.loading -> DetailPhase.Loading
                else -> DetailPhase.Failed
            },
        filter = model.filter.toUi(),
        search = model.search,
        rows = model.issues.map { it.toRow(model.names, model.userId) },
        hasMore = model.hasMore,
        isLoadingMore = model.loadingMore,
        isCreating = model.creating,
        canCreate = online && mayCreate && !model.creating,
        createdIssueId = model.createdId,
        role = role?.toUi(),
        isOffline = !online,
        actionsDisabledReason =
            when {
                !online -> ActionsDisabledReason.Offline
                role == Role.Viewer -> ActionsDisabledReason.ViewerRole
                else -> null
            },
        updatedAtEpochMillis = model.loadedAtMillis,
        error = model.error,
        feedback = model.feedback,
    )
}
