package com.stainedhead.paperclipmobile.application.issue

import com.stainedhead.paperclipmobile.application.RetryBudget
import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueDetail
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.PlanDocument
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.domain.valueOrNull

/** One page of the Work list. [hasMore] is true when the server's page was full, even if the device dropped rows. */
data class IssuePage(
    val issues: List<Issue>,
    val hasMore: Boolean,
    /** Rows the server sent, before the device dropped any; the next page starts that many rows later. */
    val serverRowCount: Int,
)

/** Lists issues for a saved filter (PRD FR-060). The one rule the server lacks, priority, is applied here (ADR-022). */
class ListIssues(
    private val gateway: IssueGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        query: IssueQuery,
    ): DomainResult<IssuePage> =
        if (query.filter == IssueFilter.CriticalAndHigh) {
            urgentPage(scope, query)
        } else {
            when (val result = retryingRead { gateway.list(scope, query) }) {
                is DomainResult.Failure -> {
                    result
                }

                is DomainResult.Success -> {
                    DomainResult.Success(
                        IssuePage(result.value, hasMore = result.value.size >= query.limit, result.value.size),
                    )
                }
            }
        }

    /**
     * Priority cannot be filtered by the server, so server pages are read until [IssueQuery.limit] urgent rows are
     * found or the server runs out, up to [MAX_SERVER_PAGES] pages (F-A-12). [IssuePage.serverRowCount] is how many
     * server rows were used, so the next page starts right after the last row shown.
     */
    private suspend fun urgentPage(
        scope: CompanyScope,
        query: IssueQuery,
    ): DomainResult<IssuePage> {
        val shown = mutableListOf<Issue>()
        var used = 0
        var hasMore = true
        var pages = 0
        while (shown.size < query.limit && hasMore && pages < MAX_SERVER_PAGES) {
            pages += 1
            val rows =
                when (val read = retryingRead { gateway.list(scope, query.copy(offset = query.offset + used)) }) {
                    is DomainResult.Success -> read.value
                    is DomainResult.Failure -> return if (shown.isEmpty()) read else partial(shown, used)
                }
            val take = rows.indexOfFilledAt(shown.size, query.limit)
            shown += rows.take(take).filter { it.priority.isUrgent }
            used += take
            hasMore = take < rows.size || rows.size >= query.limit
        }
        return DomainResult.Success(IssuePage(shown, hasMore, used))
    }

    private fun partial(
        shown: List<Issue>,
        used: Int,
    ) = DomainResult.Success(IssuePage(shown, hasMore = true, serverRowCount = used))

    /** How many of these server rows to use: all of them, or up to the row that makes the page [limit] long. */
    private fun List<Issue>.indexOfFilledAt(
        already: Int,
        limit: Int,
    ): Int {
        var urgent = already
        forEachIndexed { index, issue ->
            if (issue.priority.isUrgent) urgent += 1
            if (urgent >= limit) return index + 1
        }
        return size
    }

    private companion object {
        const val MAX_SERVER_PAGES = 10
    }
}

/** Searches issues by text, keeping the saved filter (PRD FR-060). Blank text is the plain list. */
class SearchIssues(
    private val list: ListIssues,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        filter: IssueFilter,
        text: String,
    ): DomainResult<IssuePage> = list(scope, IssueQuery(filter, text.trim().ifEmpty { null }))
}

/** The issue, its thread and the names the server leaves out (PRD FR-061, ADR-024). */
data class IssueView(
    val detail: IssueDetail,
    val comments: List<Comment>,
    val agentNames: Map<AgentId, String>,
)

/** Loads an issue for its screen. Names come from the agent list and are best effort. */
class LoadIssue(
    private val issues: IssueGateway,
    private val agents: AgentGateway,
    private val connectivity: Connectivity,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<IssueView> {
        val budget = RetryBudget(connectivity)
        val detail =
            when (val read = retryingRead(budget) { issues.get(scope, id) }) {
                is DomainResult.Failure -> return read
                is DomainResult.Success -> read.value
            }
        val comments =
            when (val read = retryingRead(budget) { issues.comments(scope, id) }) {
                is DomainResult.Failure -> return read
                is DomainResult.Success -> read.value
            }
        val names =
            retryingRead(budget) { agents.list(scope) }.valueOrNull().orEmpty().associate { it.id to it.name }
        val thread =
            comments.sortedBy { it.createdAt }.map { comment ->
                comment.copy(authorName = comment.authorAgentId?.let { names[it] })
            }
        return DomainResult.Success(IssueView(detail, thread, names))
    }
}

/** The read-only plan document, or null when the issue has none (PRD FR-061). */
class LoadIssuePlan(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<PlanDocument?> =
        when (val read = retryingRead { issues.get(scope, id) }) {
            is DomainResult.Failure -> read
            is DomainResult.Success -> DomainResult.Success(read.value.plan)
        }
}

/** The files attached to an issue; the platform opens them with HTTP Range (PRD FR-061). */
class ListIssueAttachments(
    private val issues: IssueGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Attachment>> = retryingRead { issues.attachments(scope, id) }
}
