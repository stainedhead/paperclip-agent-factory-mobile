package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

/** One row of the Work list and the header of the Issue screen (PRD FR-060, FR-061). Names are joined by use cases. */
data class Issue(
    val id: IssueId,
    val identifier: String,
    val title: String,
    val status: IssueStatus,
    val priority: IssuePriority,
    val assigneeAgentId: AgentId?,
    val assigneeUserId: String?,
    val projectId: String?,
    val parentId: IssueId?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val description: String? = null,
)

/** A reference to another issue: the parent or a blocker. */
data class IssueRef(
    val id: IssueId,
    val identifier: String,
    val title: String,
    val status: IssueStatus,
)

/** The read-only plan document (`planDocument` or the legacy plan from the description, research W5). */
data class PlanDocument(
    val title: String?,
    val body: String,
    val updatedAt: Instant?,
)

/** Everything `GET /api/issues/{id}` gives a phone, with the subtasks fetched by `parentId` (research W2). */
data class IssueDetail(
    val issue: Issue,
    val projectName: String?,
    val parent: IssueRef?,
    val blockedBy: List<IssueRef>,
    val subtasks: List<Issue>,
    val plan: PlanDocument?,
)

enum class CommentAuthorType {
    User,
    Agent,
    System,
}

data class Comment(
    val id: String,
    val body: String,
    val authorType: CommentAuthorType,
    val authorAgentId: AgentId?,
    val authorUserId: String?,
    val createdAt: Instant,
    /** Joined from the agent list by `LoadIssue`; the server sends ids only. */
    val authorName: String? = null,
)

/** The two options of a comment on a closed or running issue (PRD FR-062). */
data class CommentOptions(
    val reopen: Boolean = false,
    val interrupt: Boolean = false,
)

data class Attachment(
    val id: String,
    val fileName: String?,
    val contentType: String,
    val byteSize: Long,
    val createdAt: Instant,
    /** Path under the instance's `/api`, opened with HTTP Range. */
    val contentPath: String,
)

/** Who an issue is assigned to; [None] clears the assignee. */
sealed interface Assignee {
    data object None : Assignee

    data class AgentAssignee(
        val id: AgentId,
    ) : Assignee

    data class UserAssignee(
        val id: String,
    ) : Assignee
}

/** The body of a new issue or subtask (PRD FR-062). A [parentId] makes it a subtask. */
data class NewIssue(
    val title: String,
    val description: String? = null,
    val priority: IssuePriority = IssuePriority.Medium,
    val parentId: IssueId? = null,
    val assignee: Assignee = Assignee.None,
)

/** The saved filters of the Work list (PRD FR-060, ADR-022). */
enum class IssueFilter {
    AllOpen,
    Blocked,
    CriticalAndHigh,
    InReview,
    AssignedToMe,
    ;

    /** The `status` list sent to the server; empty means no `status` parameter. */
    val serverStatuses: List<String>
        get() =
            when (this) {
                InReview -> listOf(IssueStatus.InReview.serverName)
                Blocked -> emptyList()
                else -> OPEN_STATUSES.map { it.serverName }
            }

    val serverAttentionBlocked: Boolean get() = this == Blocked
    val serverAssigneeMe: Boolean get() = this == AssignedToMe

    /** The rule the server cannot apply (priority) plus the ones it does, so a fake and the screen agree. */
    fun matches(
        issue: Issue,
        userId: String?,
    ): Boolean =
        when (this) {
            AllOpen -> issue.status.isOpen
            Blocked -> issue.status == IssueStatus.Blocked
            CriticalAndHigh -> issue.status.isOpen && issue.priority.isUrgent
            InReview -> issue.status == IssueStatus.InReview
            AssignedToMe -> issue.status.isOpen && userId != null && issue.assigneeUserId == userId
        }

    private companion object {
        val OPEN_STATUSES =
            listOf(
                IssueStatus.Backlog,
                IssueStatus.Todo,
                IssueStatus.InProgress,
                IssueStatus.InReview,
                IssueStatus.Blocked,
            )
    }
}

/** A page of the Work list: a saved filter, an optional search, and the page (research W1). */
data class IssueQuery(
    val filter: IssueFilter = IssueFilter.AllOpen,
    val search: String? = null,
    val offset: Int = 0,
    val limit: Int = DEFAULT_LIMIT,
) {
    fun nextPage(): IssueQuery = copy(offset = offset + limit)

    companion object {
        const val DEFAULT_LIMIT = 50

        /** The server matches the identifier in any spelling; the fake and tests use the same rule. */
        fun matchesSearch(
            issue: Issue,
            search: String,
        ): Boolean {
            val needle = search.trim().lowercase()
            if (needle.isEmpty()) return true
            val compact = needle.filter { it.isLetterOrDigit() }
            val identifier = issue.identifier.lowercase().filter { it.isLetterOrDigit() }
            return issue.title.lowercase().contains(needle) || (compact.isNotEmpty() && identifier.contains(compact))
        }
    }
}
