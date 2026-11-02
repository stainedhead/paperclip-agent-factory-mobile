package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod

/** One issue of the stub; the fields that matter to the run and agent tests. */
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
}

/** The issue endpoints of the pinned release. Extended with the issue gateway; only what the run tests need here. */
internal class StubIssues(
    private val runs: StubRuns,
) {
    val issues = mutableListOf<StubIssue>()

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        if (s.size < 2 || s[0] != "issues") return null
        val issue = issues.firstOrNull { it.id == s[1] }
        val refusal = if (isServerId(s[1])) issue?.let { request.resourceRefusal(it.company, NOT_FOUND) } else null
        return when {
            !isServerId(s[1]) -> {
                StubReply.error(500, "Internal server error")
            }

            issue == null -> {
                StubReply.notFound(NOT_FOUND)
            }

            refusal != null -> {
                refusal
            }

            s.size == 2 && request.method == HttpMethod.Get -> {
                StubReply.ok(IssueRowFixtures.detail(issue))
            }

            s.size == 3 && request.method == HttpMethod.Get && s[2] == "runs" -> {
                val rows = runs.runs.filter { it.issueId == issue.id }.reversed()
                StubReply.ok(rows.joinToString(",", "[", "]") { RunFixtures.issueRun(it) })
            }

            else -> {
                null
            }
        }
    }

    private companion object {
        const val NOT_FOUND = "Issue not found"
    }
}
