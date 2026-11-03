package com.stainedhead.paperclipmobile.infrastructure.api

import kotlinx.serialization.json.JsonArray

/**
 * `GET /companies/{c}/issues` as the pinned route answers (`routes/issues.js:5620-5800`, `services/issues.js:958-970`):
 * a bare array, `limit` default 500 clamped to 1000, 400 for a malformed limit, offset, attention or view, a comma list
 * for `status`, `assigneeUserId=me` for the signed-in board user, `parentId` for subtasks and `q` for search over the
 * identifier (any spelling) and the title. `attention=blocked` keeps the blocked issues (the stub does not model the
 * other cases that carry blocker attention). Rows come in insertion order; the server's priority order is not modelled.
 */
internal object StubIssueList {
    private const val DEFAULT_LIMIT = 500
    private const val MAX_LIMIT = 1_000
    const val ME = "user-1"

    fun list(
        rows: List<StubIssue>,
        request: StubRequest,
    ): StubReply {
        val q = request.query
        val limitText = q["limit"]
        val offsetText = q["offset"]
        val digits = Regex("^\\d+$")
        val refusal =
            when {
                q["attention"] != null && q["attention"] != "blocked" -> {
                    "attention must be 'blocked' when provided"
                }

                q["view"] != null && q["view"] != "compact" -> {
                    "view must be 'compact' when provided"
                }

                limitText != null && (!digits.matches(limitText) || limitText.toLong() <= 0) -> {
                    "limit must be a positive integer up to $MAX_LIMIT"
                }

                offsetText != null && !digits.matches(offsetText) -> {
                    "offset must be a non-negative integer"
                }

                else -> {
                    null
                }
            }
        if (refusal != null) return StubReply.error(400, refusal)
        val limit = (limitText?.toLong() ?: DEFAULT_LIMIT.toLong()).coerceIn(1, MAX_LIMIT.toLong()).toInt()
        val offset = offsetText?.toInt() ?: 0
        val page = filtered(rows, request).drop(offset).take(limit)
        return StubReply.ok(JsonArray(page.map { IssueRowFixtures.compact(it) }).toString())
    }

    private fun filtered(
        rows: List<StubIssue>,
        request: StubRequest,
    ): List<StubIssue> {
        val q = request.query
        val statuses =
            q["status"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        val search = q["q"]?.trim().orEmpty()
        return rows.filter { issue ->
            (statuses.isEmpty() || issue.status in statuses) &&
                (q["attention"] != "blocked" || issue.status == "blocked") &&
                (q["assigneeUserId"] != "me" || issue.assigneeUserId == ME) &&
                (q["parentId"] == null || issue.parentId == q["parentId"]) &&
                (search.isEmpty() || matches(issue, search))
        }
    }

    private fun matches(
        issue: StubIssue,
        search: String,
    ): Boolean {
        val needle = search.lowercase()
        val compact = needle.filter { it.isLetterOrDigit() }
        val identifier = issue.identifier.lowercase().filter { it.isLetterOrDigit() }
        return issue.title.lowercase().contains(needle) || (compact.isNotEmpty() && identifier.contains(compact))
    }
}
