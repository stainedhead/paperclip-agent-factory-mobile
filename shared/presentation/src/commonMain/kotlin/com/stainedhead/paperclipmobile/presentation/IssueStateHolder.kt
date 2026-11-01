package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.refresh.RefreshTarget
import com.stainedhead.paperclipmobile.domain.Invalidation
import com.stainedhead.paperclipmobile.domain.IssueId
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

/**
 * State holder for the Issue screen (screen 4): header, Thread with interaction forms, Plan, Runs and Files tabs, and
 * the comment, status, assignee and subtask actions (PRD FR-061 to FR-063).
 */
class IssueStateHolder(
    useCases: IssueUseCases,
    private val connectivity: Connectivity,
    clock: Clock,
    scope: CoroutineScope,
    target: CompanyTarget,
    issueId: String,
) {
    private val scope = childScope(scope)
    private val model = MutableStateFlow(IssueModel())
    private val mutableState = MutableStateFlow(IssueState())
    private val session =
        IssueSession(
            useCases,
            ContextSource(useCases.resolveContext, target.toScope()),
            this.scope,
            IssueId(issueId),
            model,
            ScreenEnv({ connectivity.isOnline.value }, { clock.now().toEpochMilliseconds() }),
        )
    private val loader = IssueLoader(session)
    private val edits = IssueEdits(session, loader)
    private val interactions = IssueInteractions(session)
    val state: StateFlow<IssueState> = mutableState.asStateFlow()

    /** What the refresh policy needs from this screen (PRD FR-090, FR-091). */
    val refreshTarget: RefreshTarget =
        ScreenRefreshTarget(
            state,
            { it.refreshIntervalSeconds },
            { onIntent(IssueIntent.Refresh) },
            {
                (it is Invalidation.Issue && it.issueId == issueId) ||
                    (it is Invalidation.Run && it.issueId == issueId)
            },
        )

    init {
        this.scope.launch {
            combine(model, connectivity.isOnline) { model, online -> IssueRenderer.render(model, online) }
                .collect { mutableState.value = it }
        }
        loader.load()
    }

    fun onIntent(intent: IssueIntent) {
        when (intent) {
            IssueIntent.Refresh -> loader.load()

            is IssueIntent.TabSelected -> loader.openTab(intent.tab)

            IssueIntent.MessageDismissed -> model.update { it.copy(feedback = null) }

            is IssueIntent.CommentSubmitted,
            is IssueIntent.StatusSelected,
            is IssueIntent.AssigneeSelected,
            is IssueIntent.SubtaskSubmitted,
            -> edits.handle(intent)

            else -> interactions.handle(intent)
        }
    }

    fun close() {
        scope.cancel()
    }
}
