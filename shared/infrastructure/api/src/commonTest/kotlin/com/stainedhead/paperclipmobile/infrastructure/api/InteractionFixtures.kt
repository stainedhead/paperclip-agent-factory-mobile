package com.stainedhead.paperclipmobile.infrastructure.api

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
 * Interaction rows and payloads in the shapes of the pinned release 2026.1023.0 (compiled dist, not recorded): the
 * hydrated row (`shared/dist/types/issue.d.ts:1332-1360` `IssueThreadInteractionBase`, `services/issue-thread-
 * interactions.js:322-380`) and one payload per kind (`issue.d.ts:985-1330`; the stored payload already carries the
 * defaults the schema fills in, `validators/issue.js:1348-1378,1533-1557`).
 */
internal object InteractionFixtures {
    fun row(row: StubInteraction): String =
        buildJsonObject {
            put("id", row.id)
            put("companyId", row.company)
            put("issueId", row.issueId)
            put("kind", row.kind)
            put("status", row.status)
            put("title", text(row.title))
            put("summary", text(row.summary))
            put("continuationPolicy", "wake_assignee")
            put("requestedResolverPolicy", row.requested)
            put("effectiveResolverPolicy", row.effective)
            put("resolverPolicyProvenance", "explicit")
            put("effectiveResolverPolicySource", row.source)
            put("resolverPolicy", row.requested)
            put("addresseeAgentId", JsonNull)
            put("addresseeUserId", text(row.addresseeUserId))
            put("createdByAgentId", "e0000001-0000-4000-8000-000000000001")
            put("createdAt", row.createdAt)
            put("updatedAt", row.createdAt)
            put("resolvedAt", text(row.resolvedAt))
            if (row.blocker) put("acceptanceBlocker", "workspace_sync_pending")
            put("payload", row.payload)
            put("result", row.result ?: JsonNull)
            put("somethingNew", JsonObject(emptyMap()))
        }.toString()

    private fun options(vararg labels: Pair<String, String>) =
        buildJsonArray {
            labels.forEach { (id, label) ->
                add(
                    buildJsonObject {
                        put("id", id)
                        put("label", label)
                        put("description", "About $label")
                    },
                )
            }
        }

    fun confirmation(
        prompt: String,
        rejectRequiresReason: Boolean = false,
        extra: Map<String, JsonElement> = emptyMap(),
    ): JsonObject =
        JsonObject(
            buildJsonObject {
                put("version", 1)
                put("prompt", prompt)
                put("acceptLabel", "Switch traffic")
                put("rejectLabel", "Not yet")
                put("rejectRequiresReason", rejectRequiresReason)
                put("detailsMarkdown", "Details **here**")
            } + extra,
        )

    fun suggestTasks(vararg keys: String): JsonObject =
        buildJsonObject {
            put("version", 1)
            put(
                "tasks",
                buildJsonArray {
                    keys.forEach { key ->
                        add(
                            buildJsonObject {
                                put("clientKey", key)
                                put("title", "Task $key")
                                put("description", "Do $key")
                                put("priority", "high")
                            },
                        )
                    }
                },
            )
        }

    fun questions(
        required: Boolean? = true,
        mode: String = "single",
        allowOther: Boolean? = null,
    ): JsonObject =
        buildJsonObject {
            put("version", 1)
            put("submitLabel", "Send answers")
            put(
                "questions",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "q1")
                            put("prompt", "Which region first?")
                            put("helpText", "Pick one")
                            put("selectionMode", mode)
                            if (required != null) put("required", required)
                            if (allowOther != null) put("allowOther", allowOther)
                            put("options", options("eu" to "EU", "us" to "US"))
                        },
                    )
                },
            )
        }

    fun checkbox(rejectRequiresReason: Boolean = false): JsonObject =
        buildJsonObject {
            put("version", 1)
            put("prompt", "Which steps may run?")
            put("options", options("a" to "Step A", "b" to "Step B", "c" to "Step C"))
            put("defaultSelectedOptionIds", buildJsonArray { add(JsonPrimitive("a")) })
            put("minSelected", 1)
            put("maxSelected", 2)
            put("rejectRequiresReason", rejectRequiresReason)
        }

    fun verdicts(): JsonObject =
        buildJsonObject {
            put("version", 1)
            put("prompt", "Review these files")
            put("items", options("f1" to "File one", "f2" to "File two"))
            put("verdicts", buildJsonArray { listOf("approve", "reject", "defer").forEach { add(JsonPrimitive(it)) } })
            put("requireReasonOn", buildJsonArray { add(JsonPrimitive("reject")) })
        }

    fun connectionIntent(): JsonObject =
        buildJsonObject {
            put("version", 1)
            put("serviceSlug", "gmail")
            put("serviceName", "Gmail")
            put("requestingAgentId", "e0000001-0000-4000-8000-000000000001")
            put("requestingAgentName", "Engineer 1")
            put("phase", "requested")
        }

    val toolAction: Map<String, JsonElement> =
        mapOf(
            "toolAction" to
                buildJsonObject {
                    put("version", 1)
                    put("actionRequestId", "t1")
                    put("toolName", "delete_repo")
                    put("risk", "destructive")
                },
        )

    fun empty(): JsonArray = JsonArray(emptyList())
}
