package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

internal class StubIssueComment(
    val id: String,
    val company: String,
    val issueId: String,
    val body: String,
    val createdAt: String,
) {
    var authorType = "user"
    var authorAgentId: String? = null
    var authorUserId: String? = "user-1"
    var derivedAgentId: String? = null
    var deleted = false
}

internal class StubAttachment(
    val id: String,
    val company: String,
    val issueId: String,
    val fileName: String?,
    val contentType: String,
    val bytes: ByteArray,
) {
    var createdAt = "2026-11-01T09:00:00.000Z"
}

/**
 * The thread of an issue in the pinned release: comments (`routes/issues.js:11628-11660,13166-14016`), attachments and
 * their content with HTTP Range (`routes/issues.js:14099-14108,14248-14301`, range rules `2822-2850`). The adapter
 * lives with these facts: the comment list is newest first unless `order=asc`; a comment with `reopen` moves a
 * closed issue to `todo`; `interrupt` cancels the issue's active run; the content route answers 206, 200 or 416, and
 * a non-uuid id is 500.
 */
internal class StubThread(
    private val store: StubIssueStore,
    private val runs: StubRuns,
    private val clock: () -> Instant,
) {
    val comments = mutableListOf<StubIssueComment>()
    val attachments = mutableListOf<StubAttachment>()
    private var counter = 0

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        return when {
            s.size == 3 && s[0] == "issues" && s[2] in THREAD -> issueThread(request, s)
            s.size == 3 && s[0] == "attachments" && s[2] == "content" -> content(request, s[1])
            else -> null
        }
    }

    private fun issueThread(
        request: StubRequest,
        s: List<String>,
    ): StubReply? {
        val issue = store.issues.firstOrNull { it.id == s[1] }
        val refusal = issue?.let { request.resourceRefusal(it.company, "Issue not found") }
        return when {
            !isStubUuid(s[1]) -> StubReply.error(500, "Internal server error")
            issue == null -> StubReply.notFound("Issue not found")
            refusal != null -> refusal
            s[2] == "comments" && request.method == HttpMethod.Get -> list(request, issue)
            s[2] == "comments" && request.method == HttpMethod.Post -> post(request, issue)
            s[2] == "attachments" && request.method == HttpMethod.Get -> attachmentList(issue)
            else -> null
        }
    }

    private fun list(
        request: StubRequest,
        issue: StubIssue,
    ): StubReply {
        val rows = comments.filter { it.issueId == issue.id }
        val ordered = if (request.query["order"] == "asc") rows else rows.reversed()
        return StubReply.ok(ordered.joinToString(",", "[", "]") { IssueRowFixtures.comment(it) })
    }

    private fun post(
        request: StubRequest,
        issue: StubIssue,
    ): StubReply {
        val body = request.jsonBody()
        val text = body?.string("body")
        val reopen = (body?.get("reopen") as? JsonPrimitive)?.content == "true"
        val interrupt = (body?.get("interrupt") as? JsonPrimitive)?.content == "true"
        if (text.isNullOrEmpty()) return StubReply.invalid()
        if (reopen && reopenable(issue)) issue.status = "todo"
        if (interrupt) {
            runs.runs.filter { it.issueId == issue.id && it.status in ACTIVE }.forEach { it.status = "cancelled" }
        }
        counter += 1
        val id = "c2000000-0000-4000-8000-" + counter.toString().padStart(ID_TAIL, '0')
        val comment = StubIssueComment(id, issue.company, issue.id, text, clock().toString())
        comments += comment
        return StubReply.created(IssueRowFixtures.comment(comment))
    }

    /** A comment with `reopen` moves a closed issue, or a blocked one with no blocker left, to todo. */
    private fun reopenable(issue: StubIssue) =
        issue.status in CLOSED || (issue.status == "blocked" && issue.blockedBy.isEmpty())

    private fun attachmentList(issue: StubIssue): StubReply {
        val rows = attachments.filter { it.issueId == issue.id }.reversed()
        return StubReply.ok(rows.joinToString(",", "[", "]") { IssueRowFixtures.attachment(it) })
    }

    private fun content(
        request: StubRequest,
        id: String,
    ): StubReply? {
        val file = attachments.firstOrNull { it.id == id }
        val refusal = file?.let { request.resourceRefusal(it.company, "Attachment not found") }
        return when {
            request.method != HttpMethod.Get -> null
            !isStubUuid(id) -> StubReply.error(500, "Internal server error")
            file == null -> StubReply.notFound("Attachment not found")
            refusal != null -> refusal
            else -> slice(request.range, file)
        }
    }

    /** `parseAttachmentRangeHeader` (`routes/issues.js:2822-2850`) and the 206 and 416 replies that follow. */
    private fun slice(
        header: String?,
        file: StubAttachment,
    ): StubReply {
        val total = file.bytes.size
        val common = mapOf("Accept-Ranges" to "bytes")
        if (header == null) return StubReply(200, null, file.bytes, file.contentType, common)
        val bounds = rangeOf(header, total)
        if (bounds == null) {
            return StubReply(416, null, ByteArray(0), file.contentType, common + ("Content-Range" to "bytes */$total"))
        }
        val (start, end) = bounds
        val headers = common + ("Content-Range" to "bytes $start-$end/$total")
        return StubReply(206, null, file.bytes.copyOfRange(start, end + 1), file.contentType, headers)
    }

    private fun rangeOf(
        header: String,
        total: Int,
    ): Pair<Int, Int>? {
        val spec = header.removePrefix("bytes=").takeIf { header.lowercase().startsWith("bytes=") }?.trim()
        val parts = spec?.split("-", limit = 2)
        val first = parts?.getOrNull(0)
        val last = parts?.getOrNull(1)
        return when {
            total <= 0 || spec.isNullOrEmpty() || spec.contains(',') -> null
            first == null || last == null -> null
            first.isEmpty() -> last.toIntOrNull()?.takeIf { it > 0 }?.let { maxOf(total - it, 0) to total - 1 }
            else -> bounded(first.toIntOrNull(), last, total)
        }
    }

    private fun bounded(
        start: Int?,
        last: String,
        total: Int,
    ): Pair<Int, Int>? {
        val end = if (last.isEmpty()) total - 1 else last.toIntOrNull()
        if (start == null || end == null) return null
        return if (start !in 0 until total || end < start) null else start to minOf(end, total - 1)
    }

    private companion object {
        const val ID_TAIL = 12
        val THREAD = setOf("comments", "attachments")
        val CLOSED = setOf("done", "cancelled")
        val ACTIVE = setOf("queued", "running", "scheduled_retry")
    }
}
