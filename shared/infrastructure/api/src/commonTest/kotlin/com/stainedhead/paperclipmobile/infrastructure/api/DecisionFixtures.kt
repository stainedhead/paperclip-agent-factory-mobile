package com.stainedhead.paperclipmobile.infrastructure.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Decision bodies in the shapes of the pinned server, written from source (not recorded): the decision row plus
 * `executions` that `GET /decisions/{id}` and both mutations answer (`services/decisions.ts:277-281`, columns
 * `packages/db/src/schema/decisions.ts:34-115`, options/effects `packages/shared/src/types/decision.ts:6-78`).
 * `signedSpec` and `targetSnapshots` are in the row and are ignored by the app.
 *
 * Re-verified in 1b-4b against the pinned release 2026.1023.0 (compiled dist): `server/dist/routes/decisions.js:24-25,
 * 179-203`, `services/decisions.js:243-248,610-760`, `db/dist/schema/decisions.js`.
 */
internal object DecisionFixtures {
    fun decision(row: StubDecision): String =
        buildJsonObject {
            put("id", row.id)
            put("companyId", row.company)
            put("originAgentId", "agent-eng-1")
            put("originIssueId", "issue-1182")
            put("ruleKey", JsonNull)
            put("title", row.title)
            put("body", row.body)
            put("options", JsonArray(row.options))
            put("inputs", row.inputs?.let { JsonArray(it) } ?: JsonNull)
            put("status", row.status)
            put("executionStatus", row.executionStatus?.let { JsonPrimitive(it) } ?: JsonNull)
            put("chosenOptionId", row.chosenOptionId?.let { JsonPrimitive(it) } ?: JsonNull)
            put(
                "inputValues",
                row.inputValues?.let { values -> JsonObject(values.mapValues { JsonPrimitive(it.value) }) } ?: JsonNull,
            )
            put("decidedByUserId", if (row.status == "decided") JsonPrimitive("user-1") else JsonNull)
            put("expiresAt", row.expiresAt)
            put("idempotencyKey", JsonNull)
            put("signedSpec", "signature")
            put("targetSnapshots", JsonObject(emptyMap()))
            put("continuationPolicy", "none")
            put("metadata", metadata(row))
            put("createdAt", "2026-11-01T09:00:00.000Z")
            put("updatedAt", "2026-11-01T11:00:00.000Z")
            put("executions", JsonArray(row.executions))
        }.toString()

    private fun metadata(row: StubDecision) =
        buildJsonObject {
            if (row.dismissed) {
                put("dismissed", true)
                put("dismissReason", "not now")
            }
        }

    fun option(
        id: String,
        label: String,
        style: String? = null,
        effects: List<JsonObject> = emptyList(),
    ) = buildJsonObject {
        put("id", id)
        put("label", label)
        put("description", "About $label")
        if (style != null) put("style", style)
        put("effects", JsonArray(effects))
    }

    fun commentEffect(target: String = "issue-1182") =
        buildJsonObject {
            put("type", "comment_on_issue")
            put("targetIssueId", target)
            put("staleness", "lenient")
            put("bodyMarkdown", "Go ahead with the migration.")
        }

    fun cancelEffect(target: String = "issue-1182") =
        buildJsonObject {
            put("type", "cancel_issue_tree")
            put("targetIssueId", target)
            put("staleness", "strict")
            put("reasonComment", "Stopped by the board.")
        }

    fun input(
        id: String,
        required: Boolean = false,
    ) = buildJsonObject {
        put("id", id)
        put("label", "Note")
        put("placeholder", "Say why")
        put("required", required)
        put("maxLength", MAX_INPUT)
    }

    /** One `decision_effect_executions` row (`schema/decisions.ts:100-114`). */
    fun execution(
        index: Int,
        type: String,
        status: String,
        error: String? = null,
    ) = buildJsonObject {
        put("id", "exec-$index")
        put("decisionId", "d")
        put("effectIndex", index)
        put("effectType", type)
        put("targetIssueId", "issue-1182")
        put("status", status)
        put("result", JsonObject(emptyMap()))
        put("error", error?.let { JsonPrimitive(it) } ?: JsonNull)
        put("executedAt", "2026-11-01T11:00:01.000Z")
    }

    /** The standard three options of the app's own decision fixture: ship (comment), hold (no effect), cancel. */
    fun standardOptions() =
        listOf(
            option("ship", "Ship it", "primary", listOf(commentEffect())),
            option("hold", "Hold"),
            option("cancel", "Cancel the work", "destructive", listOf(cancelEffect())),
        )

    fun inputs(vararg ids: String): List<JsonObject> = ids.map { input(it) }

    const val MAX_INPUT = 500
}
