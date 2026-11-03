package com.stainedhead.paperclipmobile.infrastructure.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun text(value: String?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull

/**
 * Issue bodies in the shapes of the pinned release 2026.1023.0 (compiled dist, not recorded):
 * - a compact list row (`routes/issues.js:1760-1830` `toCompactIssue`; the list `description` is a preview of at most
 *   1200 characters, `services/issues.js:3079-3096`);
 * - the issue detail (`routes/issues.js:6423-6502`): the row plus `ancestors` nearest first (`issues.js:9172`),
 *   `blockedBy` (`:2129` `summarizeIssueRelationRow`), `project`, `planDocument` and `legacyPlanDocument`
 *   (`services/documents.js:85-113`, `mapIssueDocumentRow:38-60`);
 * - a comment (`db/dist/schema/issue_comments.js`; a deleted one has an empty body, `services/issues.js:4440-4454`);
 * - an attachment (`routes/issues.js:2813-2821` `withContentPath`, `services/issues.js:9047-9069`).
 */
internal object IssueRowFixtures {
    private fun base(issue: StubIssue): MutableMap<String, JsonElement> =
        mutableMapOf(
            "id" to JsonPrimitive(issue.id),
            "companyId" to JsonPrimitive(issue.company),
            "projectId" to text(issue.projectId),
            "parentId" to text(issue.parentId),
            "title" to JsonPrimitive(issue.title),
            "status" to JsonPrimitive(issue.status),
            "priority" to JsonPrimitive(issue.priority),
            "assigneeAgentId" to text(issue.assigneeAgentId),
            "assigneeUserId" to text(issue.assigneeUserId),
            "issueNumber" to JsonPrimitive(issue.number),
            "identifier" to text(issue.identifier),
            "visibility" to JsonPrimitive("open"),
            "originKind" to JsonPrimitive("manual"),
            "createdAt" to JsonPrimitive(issue.createdAt),
            "updatedAt" to JsonPrimitive(issue.updatedAt),
            "somethingNew" to buildJsonObject { put("a", 1) },
        )

    /** The compact list row; the description is the server's preview of the first 1200 characters. */
    fun compact(issue: StubIssue): JsonObject {
        val row = base(issue)
        row["description"] = text(issue.description?.take(PREVIEW))
        row["descriptionTruncated"] = JsonPrimitive((issue.description?.length ?: 0) > PREVIEW)
        row["labelIds"] = JsonArray(emptyList())
        return JsonObject(row)
    }

    private fun project(
        id: String?,
        name: String,
    ) = buildJsonObject {
        put("id", id.orEmpty())
        put("name", name)
        put("status", "in_progress")
    }

    private fun ref(other: StubIssue) =
        buildJsonObject {
            put("id", other.id)
            put("identifier", other.identifier)
            put("title", other.title)
            put("status", other.status)
            put("priority", other.priority)
            put("assigneeAgentId", text(other.assigneeAgentId))
            put("assigneeUserId", text(other.assigneeUserId))
        }

    fun detail(
        issue: StubIssue,
        all: List<StubIssue>,
    ): String {
        val row = base(issue)
        row["description"] = text(issue.description)
        val ancestors = mutableListOf<StubIssue>()
        var next = issue.parentId
        while (next != null && ancestors.size < MAX_ANCESTORS) {
            val parent = all.firstOrNull { it.id == next } ?: break
            ancestors += parent
            next = parent.parentId
        }
        row["ancestors"] = JsonArray(ancestors.map { ref(it) })
        row["blockedBy"] = JsonArray(issue.blockedBy.mapNotNull { id -> all.firstOrNull { it.id == id } }.map(::ref))
        row["blocks"] = JsonArray(emptyList())
        row["project"] = issue.projectName?.let { project(issue.projectId, it) } ?: JsonNull
        row["planDocument"] = issue.planBody?.let { plan(issue, it) } ?: JsonNull
        row["legacyPlanDocument"] =
            issue.legacyPlan?.let { body ->
                buildJsonObject {
                    put("key", "plan")
                    put("body", body)
                    put("source", "issue_description")
                }
            } ?: JsonNull
        row["documentSummaries"] = JsonArray(emptyList())
        row["workProducts"] = JsonArray(emptyList())
        return JsonObject(row).toString()
    }

    private fun plan(
        issue: StubIssue,
        body: String,
    ) = buildJsonObject {
        put("id", "d0000001-0000-4000-8000-000000000001")
        put("companyId", issue.company)
        put("issueId", issue.id)
        put("key", "plan")
        put("title", text(issue.planTitle))
        put("format", "markdown")
        put("body", body)
        put("latestRevisionNumber", 3)
        put("createdAt", "2026-10-30T09:00:00.000Z")
        put("updatedAt", issue.planUpdatedAt)
    }

    /** The reply of `PATCH /issues/{id}`: the row, what changed, and the comment that went with it. */
    fun patched(
        issue: StubIssue,
        all: List<StubIssue>,
        changed: List<String>,
    ): String {
        val row = (Json.parseToJsonElement(detail(issue, all)) as JsonObject).toMutableMap()
        row["changes"] = buildJsonObject { changed.forEach { put(it, true) } }
        row["comment"] = JsonNull
        return JsonObject(row).toString()
    }

    /** The 200 reply for a title that is already open: the existing issue marked as deduplicated. */
    fun deduplicated(
        issue: StubIssue,
        all: List<StubIssue>,
    ): String {
        val row = (Json.parseToJsonElement(detail(issue, all)) as JsonObject).toMutableMap()
        row["deduplicated"] = JsonPrimitive(true)
        row["deduplicationReason"] = JsonPrimitive("recent_open_title")
        return JsonObject(row).toString()
    }

    fun comment(row: StubIssueComment): String =
        buildJsonObject {
            put("id", row.id)
            put("companyId", row.company)
            put("issueId", row.issueId)
            put("authorType", row.authorType)
            put("authorAgentId", text(row.authorAgentId))
            put("authorUserId", text(row.authorUserId))
            put("derivedAuthorAgentId", text(row.derivedAgentId))
            put("body", if (row.deleted) "" else row.body)
            put("origin", "comment")
            put("presentation", JsonNull)
            put("metadata", JsonNull)
            put("deletedAt", if (row.deleted) JsonPrimitive("2026-11-01T11:00:00.000Z") else JsonNull)
            put("createdAt", row.createdAt)
            put("updatedAt", row.createdAt)
        }.toString()

    fun attachment(row: StubAttachment): String {
        val path = "/api/attachments/${row.id}/content"
        return buildJsonObject {
            put("id", row.id)
            put("companyId", row.company)
            put("issueId", row.issueId)
            put("assetId", "f0000000-0000-4000-8000-000000000001")
            put("provider", "local_disk")
            put("objectKey", "company/${row.id}")
            put("contentType", row.contentType)
            put("byteSize", row.bytes.size)
            put("sha256", "00")
            put("originalFilename", text(row.fileName))
            put("createdAt", row.createdAt)
            put("updatedAt", row.createdAt)
            put("contentPath", path)
            put("openPath", path)
            put("downloadPath", "$path?download=1")
        }.toString()
    }

    private const val PREVIEW = 1_200
    private const val MAX_ANCESTORS = 50
}
