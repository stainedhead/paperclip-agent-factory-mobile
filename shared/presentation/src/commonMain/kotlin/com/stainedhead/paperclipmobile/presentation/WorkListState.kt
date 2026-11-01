package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.application.agent.ListAgents
import com.stainedhead.paperclipmobile.application.issue.CreateIssue
import com.stainedhead.paperclipmobile.application.issue.ListIssues
import com.stainedhead.paperclipmobile.application.issue.SearchIssues
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueFilter

/** The saved filters of the Work list (PRD FR-060). */
enum class IssueFilterUi {
    AllOpen,
    Blocked,
    CriticalAndHigh,
    InReview,
    AssignedToMe,
}

internal fun IssueFilterUi.toDomain() = IssueFilter.valueOf(name)

internal fun IssueFilter.toUi() = IssueFilterUi.valueOf(name)

data class IssueRowUi(
    val id: String,
    val identifier: String,
    val title: String,
    val status: IssueStatusUi,
    val priority: IssuePriorityUi,
    val assigneeName: String?,
    val assignedToMe: Boolean,
    val updatedAtEpochMillis: Long,
)

internal fun Issue.toRow(
    names: Map<com.stainedhead.paperclipmobile.domain.AgentId, String>,
    userId: String?,
) = IssueRowUi(
    id = id.value,
    identifier = identifier,
    title = title,
    status = status.toUi(),
    priority = priority.toUi(),
    assigneeName = assigneeAgentId?.let { names[it] },
    assignedToMe = userId != null && assigneeUserId == userId,
    updatedAtEpochMillis = updatedAt.toEpochMilliseconds(),
)

data class WorkListState(
    val phase: DetailPhase = DetailPhase.Loading,
    val filter: IssueFilterUi = IssueFilterUi.AllOpen,
    val search: String = "",
    val rows: List<IssueRowUi> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isCreating: Boolean = false,
    val canCreate: Boolean = false,
    /** Set after a create succeeds, so the platform UI can open the new issue; send `CreatedAcknowledged` after. */
    val createdIssueId: String? = null,
    val role: RoleUi? = null,
    val isOffline: Boolean = false,
    val actionsDisabledReason: ActionsDisabledReason? = null,
    val updatedAtEpochMillis: Long? = null,
    val refreshIntervalSeconds: Int = SCREEN_REFRESH_SECONDS,
    val error: ErrorUi? = null,
    val feedback: FeedbackUi? = null,
)

sealed interface WorkListIntent {
    data object Refresh : WorkListIntent

    data class FilterSelected(
        val filter: IssueFilterUi,
    ) : WorkListIntent

    /** The search box text; blank clears the search. */
    data class SearchChanged(
        val text: String,
    ) : WorkListIntent

    data object LoadMore : WorkListIntent

    data class CreateSubmitted(
        val title: String,
        val priority: IssuePriorityUi,
    ) : WorkListIntent

    data object CreatedAcknowledged : WorkListIntent

    data object MessageDismissed : WorkListIntent
}

data class WorkListUseCases(
    val resolveContext: ResolveCompanyContext,
    val list: ListIssues,
    val search: SearchIssues,
    val create: CreateIssue,
    val listAgents: ListAgents,
)
