package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.Attachment
import com.stainedhead.paperclipmobile.domain.Comment
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Issue
import com.stainedhead.paperclipmobile.domain.IssueDetail
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueRef
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.PlanDocument
import com.stainedhead.paperclipmobile.domain.port.IssueGateway
import com.stainedhead.paperclipmobile.testing.TestData
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Instant

/**
 * An issues endpoint with the server's list rules (research W1): `status`, `attention=blocked`, `assigneeUserId=me` and
 * `q` are applied, priority is NOT (the app filters it), and paging is by offset and limit. A comment with `reopen`
 * moves a closed issue to todo (W3). [failures] scripts errors by operation name.
 */
@OptIn(ExperimentalAtomicApi::class)
class FakeIssueGateway(
    issues: List<Issue> = WorkFixtures.issues(),
    private val currentUserId: String = WorkFixtures.ME,
    comments: Map<IssueId, List<Comment>> = mapOf(WorkFixtures.pap1182 to WorkFixtures.comments()),
    plans: Map<IssueId, PlanDocument> = mapOf(WorkFixtures.pap1182 to WorkFixtures.plan()),
    attachments: Map<IssueId, List<Attachment>>? = null,
    private val projectName: String = "Billing",
) : IssueGateway {
    /** The time a new comment is stamped with; a demo graph sets its clock so the comment sorts last. */
    var now: () -> Instant = { TestData.epoch }

    val store = issues.associateBy { it.id }.toMutableMap()
    val commentStore = comments.mapValues { it.value.toMutableList() }.toMutableMap()
    private val attachmentStore: Map<IssueId, List<Attachment>>? = attachments
    var attachmentList: List<Attachment> = WorkFixtures.attachments()
    val plans = plans.toMutableMap()
    private val callCounts = AtomicReference<Map<String, Int>>(emptyMap())

    /** How often each operation ran. Safe to bump from several coroutines at once (issue refs are read together). */
    val calls: Map<String, Int> get() = callCounts.load()
    val failures = mutableMapOf<String, DomainError>()
    val commentOptions = mutableListOf<CommentOptions>()
    val queries = mutableListOf<IssueQuery>()

    /** The server stores a comment even though the answer is a scripted failure (a timeout after the write). */
    var storesCommentOnFailure = false

    fun count(name: String): Int = calls[name] ?: 0

    private fun record(name: String): DomainError? {
        while (true) {
            val seen = callCounts.load()
            if (callCounts.compareAndSet(seen, seen + (name to (seen[name] ?: 0) + 1))) break
        }
        return failures[name]
    }

    override suspend fun list(
        scope: CompanyScope,
        query: IssueQuery,
    ): DomainResult<List<Issue>> {
        queries += query
        record("list")?.let { return DomainResult.Failure(it) }
        val statuses = query.filter.serverStatuses
        val rows =
            store.values
                .filter { statuses.isEmpty() || it.status.serverName in statuses }
                .filter { !query.filter.serverAttentionBlocked || it.status == IssueStatus.Blocked }
                .filter { !query.filter.serverAssigneeMe || it.assigneeUserId == currentUserId }
                .filter { query.search == null || IssueQuery.matchesSearch(it, query.search!!) }
                .sortedBy { it.identifier }
        return DomainResult.Success(rows.drop(query.offset).take(query.limit))
    }

    override suspend fun get(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<IssueDetail> {
        record("get")?.let { return DomainResult.Failure(it) }
        val issue = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        val parent = issue.parentId?.let { store[it] }?.let { IssueRef(it.id, it.identifier, it.title, it.status) }
        val subtasks = store.values.filter { it.parentId == id }.sortedBy { it.identifier }
        return DomainResult.Success(
            IssueDetail(
                issue,
                projectName,
                parent,
                emptyList(),
                subtasks,
                plans[id],
            ),
        )
    }

    override suspend fun comments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Comment>> {
        record("comments")?.let { return DomainResult.Failure(it) }
        if (id !in store) return DomainResult.Failure(DomainError.NotFound)
        return DomainResult.Success(commentStore[id].orEmpty().toList())
    }

    override suspend fun addComment(
        scope: CompanyScope,
        id: IssueId,
        body: String,
        options: CommentOptions,
    ): DomainResult<Comment> {
        commentOptions += options
        val scripted = record("addComment")
        if (scripted != null && !storesCommentOnFailure) return DomainResult.Failure(scripted)
        val issue = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        if (options.reopen && !issue.status.isOpen) store[id] = issue.copy(status = IssueStatus.Todo)
        val comment =
            Comment(
                "c${commentStore[id].orEmpty().size + 1}",
                body,
                CommentAuthorType.User,
                null,
                currentUserId,
                now(),
            )
        commentStore.getOrPut(id) { mutableListOf() } += comment
        return scripted?.let { DomainResult.Failure(it) } ?: DomainResult.Success(comment)
    }

    override suspend fun setStatus(
        scope: CompanyScope,
        id: IssueId,
        status: IssueStatus,
    ): DomainResult<Issue> {
        record("setStatus")?.let { return DomainResult.Failure(it) }
        val issue = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        return DomainResult.Success(issue.copy(status = status).also { store[id] = it })
    }

    override suspend fun assign(
        scope: CompanyScope,
        id: IssueId,
        assignee: Assignee,
    ): DomainResult<Issue> {
        record("assign")?.let { return DomainResult.Failure(it) }
        val issue = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        val updated =
            when (assignee) {
                Assignee.None -> issue.copy(assigneeAgentId = null, assigneeUserId = null)
                is Assignee.AgentAssignee -> issue.copy(assigneeAgentId = assignee.id, assigneeUserId = null)
                is Assignee.UserAssignee -> issue.copy(assigneeAgentId = null, assigneeUserId = assignee.id)
            }
        return DomainResult.Success(updated.also { store[id] = it })
    }

    override suspend fun create(
        scope: CompanyScope,
        issue: NewIssue,
    ): DomainResult<Issue> {
        record("create")?.let { return DomainResult.Failure(it) }
        if (issue.parentId != null && issue.parentId !in store) return DomainResult.Failure(DomainError.NotFound)
        val number = FIRST_NEW_NUMBER + store.size
        val created =
            WorkFixtures
                .issue(
                    "pap-$number",
                    issue.title,
                    IssueStatus.Backlog,
                    issue.priority,
                    issue.parentId,
                )
        return DomainResult.Success(created.also { store[it.id] = it })
    }

    override suspend fun attachments(
        scope: CompanyScope,
        id: IssueId,
    ): DomainResult<List<Attachment>> {
        record("attachments")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(attachmentStore?.let { it[id].orEmpty() } ?: attachmentList)
    }

    private companion object {
        const val FIRST_NEW_NUMBER = 2_000
    }
}
