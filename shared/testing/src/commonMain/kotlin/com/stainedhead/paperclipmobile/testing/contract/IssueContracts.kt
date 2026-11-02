package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.port.IssueGateway

/**
 * The read rules of [IssueGateway] (research W1, W2, W5: `routes/issues.ts:8389-8764`, `services/issues.ts:1885`).
 * The gateway holds at least five issues of which at least one is blocked and one is closed (done or cancelled).
 */
abstract class IssueReadContract {
    abstract fun create(): IssueGateway

    abstract val scope: CompanyScope

    /** The identifier of an issue whose number is searchable, for example `PAP-1182`. */
    abstract val searchableIdentifier: String

    suspend fun runAll() {
        statusFiltersAreAppliedByTheServerButPriorityIsNot()
        pagesByOffsetAndLimitCoverTheListOnce()
        searchMatchesTheIdentifier()
        anUnknownIssueIsNotFound()
    }

    private suspend fun ids(
        gateway: IssueGateway,
        query: IssueQuery,
    ) = gateway.list(scope, query).mustSucceed("list").map { it.id }

    private suspend fun statusFiltersAreAppliedByTheServerButPriorityIsNot() {
        val gateway = create()
        val open = gateway.list(scope, IssueQuery(IssueFilter.AllOpen)).mustSucceed("open issues")
        check(open.all { it.status.isOpen }) { "AllOpen lists only open statuses" }
        val blocked = gateway.list(scope, IssueQuery(IssueFilter.Blocked)).mustSucceed("blocked issues")
        check(blocked.isNotEmpty() && blocked.all { it.status == IssueStatus.Blocked }) { "attention=blocked" }
        check(ids(gateway, IssueQuery(IssueFilter.CriticalAndHigh)) == open.map { it.id }) {
            "there is no priority parameter: the app filters priority, so the server list equals AllOpen"
        }
    }

    private suspend fun pagesByOffsetAndLimitCoverTheListOnce() {
        val gateway = create()
        val all = ids(gateway, IssueQuery(limit = IssueQuery.DEFAULT_LIMIT))
        val paged = mutableListOf<IssueId>()
        var query = IssueQuery(limit = 2)
        do {
            val page = ids(gateway, query)
            paged += page
            query = query.nextPage()
        } while (page.size == 2)
        check(paged == all) { "paging by offset and limit visits the list once, in order" }
    }

    private suspend fun searchMatchesTheIdentifier() {
        val gateway = create()
        val hits = gateway.list(scope, IssueQuery(search = searchableIdentifier.lowercase())).mustSucceed("search")
        check(hits.any { it.identifier == searchableIdentifier }) { "q matches the identifier in any spelling" }
    }

    private suspend fun anUnknownIssueIsNotFound() {
        val gateway = create()
        gateway.get(scope, IssueId("no-such-issue")).mustFailWith(DomainError.NotFound, "an unknown issue")
        gateway
            .comments(
                scope,
                IssueId("no-such-issue"),
            ).mustFailWith(DomainError.NotFound, "comments of an unknown issue")
    }
}

/**
 * The write rules of [IssueGateway] (research W3, W4: comments, `PATCH /api/issues/{id}`, create). [openId] is an open
 * issue, [closedId] a done or cancelled one and [agent] an agent that exists.
 */
abstract class IssueWriteContract {
    abstract fun create(): IssueGateway

    abstract val scope: CompanyScope
    abstract val openId: IssueId
    abstract val closedId: IssueId
    abstract val agent: AgentId

    suspend fun runAll() {
        aCommentAppendsToTheThread()
        aCommentWithReopenMovesAClosedIssueToTodo()
        statusAndAssigneeChangesAreReturned()
        createMakesABacklogIssueAndASubtaskNeedsItsParent()
    }

    private suspend fun aCommentAppendsToTheThread() {
        val gateway = create()
        val before = gateway.comments(scope, openId).mustSucceed("comments").size
        gateway
            .addComment(
                scope,
                openId,
                "Please retry.",
                CommentOptions(reopen = false, interrupt = false),
            ).mustSucceed("comment")
        check(gateway.comments(scope, openId).mustSucceed("comments").size == before + 1) { "the comment is appended" }
    }

    private suspend fun aCommentWithReopenMovesAClosedIssueToTodo() {
        val gateway = create()
        gateway
            .addComment(
                scope,
                closedId,
                "Reopening.",
                CommentOptions(reopen = true, interrupt = false),
            ).mustSucceed("comment")
        check(
            gateway
                .get(scope, closedId)
                .mustSucceed("issue")
                .issue.status == IssueStatus.Todo,
        ) {
            "reopen moves a closed issue to todo"
        }
    }

    private suspend fun statusAndAssigneeChangesAreReturned() {
        val gateway = create()
        check(
            gateway.setStatus(scope, openId, IssueStatus.InReview).mustSucceed("status").status == IssueStatus.InReview,
        ) {
            "the changed issue is returned"
        }
        check(
            gateway.assign(scope, openId, Assignee.AgentAssignee(agent)).mustSucceed("assign").assigneeAgentId == agent,
        ) {
            "assigning an agent sets it"
        }
        check(gateway.assign(scope, openId, Assignee.None).mustSucceed("unassign").assigneeAgentId == null) {
            "None clears the assignee"
        }
    }

    private suspend fun createMakesABacklogIssueAndASubtaskNeedsItsParent() {
        val gateway = create()
        val created = gateway.create(scope, NewIssue("Follow up", priority = IssuePriority.High)).mustSucceed("create")
        check(
            created.priority == IssuePriority.High && created.title == "Follow up",
        ) { "create keeps title and priority" }
        val sub = gateway.create(scope, NewIssue("Subtask", parentId = openId)).mustSucceed("create a subtask")
        check(sub.parentId == openId) { "a subtask names its parent" }
        gateway
            .create(
                scope,
                NewIssue("Orphan", parentId = IssueId("no-such-issue")),
            ).mustFailWith(DomainError.NotFound, "an unknown parent")
    }
}
