package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.NewIssue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the issue gateway sends. Every body is built key by key, because the server tells "absent" from "null" and the
 * shared JSON has `explicitNulls = false`: an assignee is cleared with an explicit null and merged with the stored one
 * otherwise, so two assignees would be 422 (pinned `services/issues.js:7324-7331`, ADR-063).
 */
internal object IssueRequests {
    /** The query of the Work list: the compact view, the page, and the filter the server has parameters for. */
    fun listParams(query: IssueQuery): List<Pair<String, String>> =
        buildList {
            add("view" to "compact")
            add("limit" to query.limit.toString())
            add("offset" to query.offset.toString())
            if (query.filter.serverStatuses.isNotEmpty()) add("status" to query.filter.serverStatuses.joinToString(","))
            if (query.filter.serverAttentionBlocked) add("attention" to "blocked")
            if (query.filter.serverAssigneeMe) add("assigneeUserId" to "me")
            query.search
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add("q" to it) }
        }

    fun comment(
        body: String,
        options: CommentOptions,
    ): String =
        buildJsonObject {
            put("body", body)
            if (options.reopen) put("reopen", true)
            if (options.interrupt) put("interrupt", true)
        }.toString()

    fun status(serverName: String): String = buildJsonObject { put("status", serverName) }.toString()

    /** Both assignee fields every time: the one that is set, and an explicit null for the other. */
    fun assignee(assignee: Assignee): String =
        buildJsonObject {
            when (assignee) {
                Assignee.None -> {
                    put("assigneeAgentId", JsonNull)
                    put("assigneeUserId", JsonNull)
                }

                is Assignee.AgentAssignee -> {
                    put("assigneeAgentId", assignee.id.value)
                    put("assigneeUserId", JsonNull)
                }

                is Assignee.UserAssignee -> {
                    put("assigneeUserId", assignee.id)
                    put("assigneeAgentId", JsonNull)
                }
            }
        }.toString()

    /** A new issue. `allowDuplicate` keeps the server from handing back an open issue with the same title. */
    fun create(issue: NewIssue): String =
        buildJsonObject {
            put("title", issue.title)
            issue.description?.let { put("description", it) }
            if (issue.priority != IssuePriority.Unknown) put("priority", issue.priority.serverName)
            issue.parentId?.let { put("parentId", it.value) }
            newAssignee(issue.assignee)
            put("allowDuplicate", true)
        }.toString()

    private fun JsonObjectBuilder.newAssignee(assignee: Assignee) {
        when (assignee) {
            Assignee.None -> Unit
            is Assignee.AgentAssignee -> put("assigneeAgentId", assignee.id.value)
            is Assignee.UserAssignee -> put("assigneeUserId", assignee.id)
        }
    }
}
