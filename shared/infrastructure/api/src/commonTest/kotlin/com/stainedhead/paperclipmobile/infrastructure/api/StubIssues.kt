package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** One issue of the stub: the fields the list, the detail and the writes of the issue gateway read. */
internal class StubIssue(
    val id: String,
    val company: String,
    val identifier: String,
    var title: String,
    var status: String = "todo",
    var priority: String = "medium",
) {
    var assigneeAgentId: String? = null
    var assigneeUserId: String? = null
    var parentId: String? = null
    var description: String? = null
    var projectId: String? = "b1000000-0000-4000-8000-000000000001"
    var projectName: String? = "Billing"
    var number = 1
    var createdAt = "2026-10-30T09:00:00.000Z"
    var updatedAt = "2026-11-01T11:00:00.000Z"
    val blockedBy = mutableListOf<String>()
    var planBody: String? = null
    var planTitle: String? = "Plan"
    var planUpdatedAt = "2026-11-01T08:00:00.000Z"
    var legacyPlan: String? = null
    var hasPendingInteraction = false
}

/** The issues of one host, shared by the issue, comment and interaction handlers. */
internal class StubIssueStore {
    val issues = mutableListOf<StubIssue>()
    private var counter = 0

    fun nextId(): String {
        counter += 1
        return "c1000000-0000-4000-8000-" + counter.toString().padStart(ID_TAIL, '0')
    }

    private companion object {
        const val ID_TAIL = 12
    }
}

internal fun StubIssueStore.build(
    company: String,
    draft: IssueDraft,
    now: String,
): StubIssue {
    val status = if (draft.agentId != null || draft.userId != null) "todo" else "backlog"
    val number = issues.count { it.company == company } + 1
    val title = draft.title.ifBlank { draft.description.orEmpty().take(TITLE_LENGTH) }
    val issue = StubIssue(nextId(), company, "ACM-$number", title, status, draft.priority)
    issue.number = number
    issue.parentId = draft.parentId
    issue.description = draft.description
    issue.assigneeAgentId = draft.agentId
    issue.assigneeUserId = draft.userId
    issue.createdAt = now
    issue.updatedAt = now
    issues += issue
    return issue
}

private const val TITLE_LENGTH = 80

/**
 * The issue endpoints of the pinned release (`routes/issues.js:5620-5962,6423-6502,8561-8860,9657-11410`).
 * Rules the adapter has to live with, all read from that dist:
 * - the list is a bare array; `limit` is clamped to 1000 (default 500), a non-numeric limit or offset is 400;
 * - `PATCH` merges the assignee fields with the stored ones, so two assignees are 422, `in_progress` needs an
 *   assignee (422) and `blocked` needs a blocker, a pending interaction or approval, or a descriptor (422);
 * - `POST` without `allowDuplicate: true` answers 200 `deduplicated` for an open issue of the same title and parent
 *   created in the last 48 hours (`services/issues.js:6537-6631`).
 */
internal class StubIssues(
    private val store: StubIssueStore,
    private val runs: StubRuns,
    private val agents: StubAgents,
    private val clock: () -> Instant,
) {
    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        return when {
            s.size == COMPANY_ISSUES && s[0] == "companies" && s[2] == "issues" -> company(request, s[1])
            s.size >= 2 && s[0] == "issues" -> byId(request, s)
            else -> null
        }
    }

    private fun company(
        request: StubRequest,
        company: String,
    ): StubReply? =
        when (request.method) {
            HttpMethod.Get -> StubIssueList.list(store.issues.filter { it.company == company }, request)
            HttpMethod.Post -> create(request, company)
            else -> null
        }

    private fun byId(
        request: StubRequest,
        s: List<String>,
    ): StubReply? {
        val issue = store.issues.firstOrNull { it.id == s[1] }
        val refusal = issue?.let { request.resourceRefusal(it.company, NOT_FOUND) }
        return when {
            !isServerId(s[1]) -> StubReply.error(500, "Internal server error")
            issue == null -> StubReply.notFound(NOT_FOUND)
            refusal != null -> refusal
            s.size == 2 -> issueRoute(request, issue)
            s.size == 3 && request.method == HttpMethod.Get && s[2] == "runs" -> issueRuns(issue)
            else -> null
        }
    }

    private fun issueRoute(
        request: StubRequest,
        issue: StubIssue,
    ): StubReply? =
        when (request.method) {
            HttpMethod.Get -> StubReply.ok(IssueRowFixtures.detail(issue, store.issues))
            HttpMethod.Patch -> patch(request, issue)
            else -> null
        }

    private fun issueRuns(issue: StubIssue): StubReply {
        val rows = runs.runs.filter { it.issueId == issue.id }.reversed()
        return StubReply.ok(rows.joinToString(",", "[", "]") { RunFixtures.issueRun(it) })
    }

    private fun patch(
        request: StubRequest,
        issue: StubIssue,
    ): StubReply {
        val body = request.jsonBody() ?: return StubReply.invalid()
        val status = body.string("status")
        val change = IssueChange(body)
        val refusal =
            when {
                status != null && status !in STATUSES -> StubReply.invalid()
                change.assignee.isBad -> StubReply.invalid()
                else -> rule(issue, status, change)
            }
        if (refusal != null) return refusal
        val changed = mutableListOf<String>()
        if (status != null) {
            issue.status = status
            changed += "status"
        }
        change.apply(issue)?.let { changed += it }
        issue.updatedAt = clock().toString()
        return StubReply.ok(IssueRowFixtures.patched(issue, store.issues, changed))
    }

    /** The service rules of `update` (`services/issues.js:7320-7340`) and the blocked rule (`issues.js:10085`). */
    private fun rule(
        issue: StubIssue,
        status: String?,
        change: IssueChange,
    ): StubReply? {
        val agent = change.assignee.agentId(issue.assigneeAgentId)
        val user = change.assignee.userId(issue.assigneeUserId)
        return when {
            agent != null && user != null -> {
                StubReply.unprocessable("Issue can only have one assignee")
            }

            status == "in_progress" && agent == null && user == null -> {
                StubReply.unprocessable("in_progress issues require an assignee")
            }

            status == "blocked" && issue.blockedBy.isEmpty() && !issue.hasPendingInteraction -> {
                StubReply.unprocessable(BLOCKED_RULE)
            }

            change.assignee.agentSet && agent != null && !agentExists(issue.company, agent) -> {
                StubReply.unprocessable("Agent not found")
            }

            else -> {
                null
            }
        }
    }

    private fun agentExists(
        company: String,
        id: String,
    ) = agents.agents.any { it.id == id && it.company == company }

    private fun create(
        request: StubRequest,
        company: String,
    ): StubReply {
        val body = request.jsonBody() ?: return StubReply.invalid()
        val draft = IssueDraft(body)
        createRefusal(company, draft)?.let { return it }
        val duplicate = if (draft.allowDuplicate) null else recentDuplicate(company, draft)
        if (duplicate != null) return StubReply.ok(IssueRowFixtures.deduplicated(duplicate, store.issues))
        val issue = store.build(company, draft, clock().toString())
        return StubReply.created(IssueRowFixtures.detail(issue, store.issues))
    }

    private fun createRefusal(
        company: String,
        draft: IssueDraft,
    ): StubReply? {
        val parent = draft.parentId?.let { id -> store.issues.firstOrNull { it.id == id && it.company == company } }
        val twoAssignees = draft.agentId != null && draft.userId != null
        return when {
            !draft.valid -> StubReply.invalid()
            draft.parentId != null && parent == null -> StubReply.notFound("Parent issue not found")
            twoAssignees -> StubReply.unprocessable("Issue can only have one assignee")
            draft.agentId != null && !agentExists(company, draft.agentId) -> StubReply.unprocessable("Agent not found")
            else -> null
        }
    }

    private fun recentDuplicate(
        company: String,
        draft: IssueDraft,
    ): StubIssue? {
        val cutoff = clock() - DUPLICATE_WINDOW
        return store.issues.firstOrNull {
            it.company == company && it.parentId == draft.parentId && it.status !in CLOSED &&
                normal(it.title) == normal(draft.title) && Instant.parse(it.createdAt) >= cutoff
        }
    }

    private fun normal(title: String) = title.trim().lowercase().replace(Regex("\\s+"), " ")

    private companion object {
        const val COMPANY_ISSUES = 3
        const val NOT_FOUND = "Issue not found"
        const val BLOCKED_RULE =
            "Entering blocked requires unresolved blockers, a pending interaction/approval, or unblockDescriptor"
        val STATUSES = setOf("backlog", "todo", "in_progress", "in_review", "done", "blocked", "cancelled")
        val CLOSED = setOf("done", "cancelled")
        val DUPLICATE_WINDOW = 48.hours
    }
}

/** The assignee part of a PATCH: absent means "keep", an explicit null means "clear". */
internal class AssigneeChange(
    body: JsonObject,
) {
    val agentSet = "assigneeAgentId" in body
    val userSet = "assigneeUserId" in body
    private val agent: JsonElement? = body["assigneeAgentId"]
    private val user: JsonElement? = body["assigneeUserId"]
    val isBad: Boolean =
        listOf(agent, user).any { it != null && it !is JsonNull && !(it is JsonPrimitive && it.isString) }

    fun agentId(current: String?): String? = if (agentSet) text(agent) else current

    fun userId(current: String?): String? = if (userSet) text(user) else current

    private fun text(element: JsonElement?) = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}

internal class IssueChange(
    private val body: JsonObject,
) {
    val assignee = AssigneeChange(body)

    /** Applies the title, priority and assignee parts; returns the first changed name, if any. */
    fun apply(issue: StubIssue): String? {
        var changed: String? = null
        if (assignee.agentSet) {
            issue.assigneeAgentId = assignee.agentId(issue.assigneeAgentId)
            changed = "assigneeAgentId"
        }
        if (assignee.userSet) {
            issue.assigneeUserId = assignee.userId(issue.assigneeUserId)
            changed = changed ?: "assigneeUserId"
        }
        body.string("title")?.let {
            issue.title = it
            changed = changed ?: "title"
        }
        body.string("priority")?.let {
            issue.priority = it
            changed = changed ?: "priority"
        }
        return changed
    }
}

/** The fields of a create body the stub reads; `valid` follows `createIssueSchema` for those fields. */
internal class IssueDraft(
    body: JsonObject,
) {
    val title: String = body.string("title").orEmpty()
    val description: String? = body.string("description")
    val priority: String = body.string("priority") ?: "medium"
    val parentId: String? = body.string("parentId")
    val agentId: String? = body.string("assigneeAgentId")
    val userId: String? = body.string("assigneeUserId")
    val allowDuplicate: Boolean = (body["allowDuplicate"] as? JsonPrimitive)?.content == "true"
    val valid: Boolean =
        (title.isNotBlank() || !description.isNullOrBlank()) && priority in PRIORITIES &&
            (parentId == null || isServerId(parentId))

    private companion object {
        val PRIORITIES = setOf("critical", "high", "medium", "low")
    }
}
