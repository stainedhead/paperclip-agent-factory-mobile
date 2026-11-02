package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One issue-thread interaction of the stub. [payload] is the stored payload of its [kind]. */
internal class StubInteraction(
    val id: String,
    val company: String,
    val issueId: String,
    val kind: String,
    val payload: JsonObject,
) {
    var status = "pending"
    var title: String? = null
    var summary: String? = null
    var createdAt = "2026-11-01T09:00:00.000Z"
    var resolvedAt: String? = null
    var blocker = false
    var addresseeUserId: String? = null
    var result: JsonObject? = null
    var requested = "anyone"
    var effective = "anyone"
    var source = "requested"
}

/**
 * The interaction endpoints of the pinned release (`routes/issues.js:12074-12082,12223-12783`,
 * `services/issue-thread-interactions.js`). What the adapter has to live with, all read from that dist:
 * - a pending interaction of a closed issue is listed as `expired` and cannot be answered (409);
 * - an answer to an interaction that is not pending is 409 "Interaction has already been resolved";
 * - the endpoint decides the kind: `accept` is for suggest_tasks and confirmations, `respond` for questions,
 *   `verdicts` for item verdicts, and a wrong kind or a `connection_intent` is 422;
 * - accepting `suggest_tasks` without `selectedClientKeys` accepts every task.
 */
internal class StubInteractions(
    private val store: StubIssueStore,
    private val clock: () -> kotlin.time.Instant,
) {
    val interactions = mutableListOf<StubInteraction>()

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        if (s.size < LIST || s[0] != "issues" || s[2] != "interactions") return null
        val issue = store.issues.firstOrNull { it.id == s[1] }
        val refusal = issue?.let { request.resourceRefusal(it.company, "Issue not found") }
        return when {
            !isServerId(s[1]) -> StubReply.error(500, "Internal server error")
            issue == null -> StubReply.notFound("Issue not found")
            refusal != null -> refusal
            s.size == LIST && request.method == HttpMethod.Get -> list(issue)
            s.size == ACTION && request.method == HttpMethod.Post -> act(request, issue, s[3], s[4])
            else -> null
        }
    }

    private fun list(issue: StubIssue): StubReply {
        val closed = issue.status in CLOSED
        val rows =
            interactions.filter { it.issueId == issue.id }.map { row ->
                if (closed &&
                    row.status == "pending"
                ) {
                    InteractionFixtures.row(expired(row))
                } else {
                    InteractionFixtures.row(row)
                }
            }
        return StubReply.ok(rows.joinToString(",", "[", "]"))
    }

    private fun expired(row: StubInteraction): StubInteraction {
        val copy = StubInteraction(row.id, row.company, row.issueId, row.kind, row.payload)
        copy.status = "expired"
        copy.createdAt = row.createdAt
        return copy
    }

    private fun act(
        request: StubRequest,
        issue: StubIssue,
        id: String,
        action: String,
    ): StubReply? {
        val row = interactions.firstOrNull { it.id == id && it.issueId == issue.id }
        return when {
            action !in ACTIONS -> {
                null
            }

            row == null -> {
                StubReply.notFound("Interaction not found")
            }

            issue.status in CLOSED -> {
                StubReply.error(409, "Interaction is no longer actionable because the issue is closed")
            }

            row.status != "pending" -> {
                StubReply.error(409, "Interaction has already been resolved")
            }

            row.addresseeUserId != null && row.addresseeUserId != StubIssueList.ME -> {
                StubReply.forbidden("This interaction is addressed to another person")
            }

            else -> {
                StubInteractionRules.resolve(row, action, request.jsonBody(), clock().toString())
            }
        }
    }

    private companion object {
        const val LIST = 3
        const val ACTION = 5
        val CLOSED = setOf("done", "cancelled")
        val ACTIONS = setOf("accept", "reject", "respond", "verdicts", "skip")
    }
}

/** What an answer does to a pending interaction, and the 4xx it can earn (`issue-thread-interactions.js`). */
internal object StubInteractionRules {
    fun resolve(
        row: StubInteraction,
        action: String,
        body: JsonObject?,
        now: String,
    ): StubReply {
        val refusal = refusal(row, action, body ?: JsonObject(emptyMap()))
        if (refusal != null) return refusal
        row.status =
            when (action) {
                "accept" -> "accepted"
                "reject" -> "rejected"
                "skip" -> "cancelled"
                else -> "answered"
            }
        row.resolvedAt = now
        row.result = body
        return StubReply.ok(InteractionFixtures.row(row))
    }

    private fun refusal(
        row: StubInteraction,
        action: String,
        body: JsonObject,
    ): StubReply? =
        when {
            row.kind == "connection_intent" && action in setOf("accept", "reject") -> {
                StubReply.unprocessable("Connection intents must be resolved through the connection intent endpoints")
            }

            action != "skip" && row.kind !in KINDS.getValue(action) -> {
                StubReply.unprocessable("Only ${KINDS.getValue(action).first()} interactions can be answered here")
            }

            else -> {
                bodyRefusal(row, action, body)
            }
        }

    private fun bodyRefusal(
        row: StubInteraction,
        action: String,
        body: JsonObject,
    ): StubReply? =
        when (action) {
            "accept" -> acceptRefusal(row, body)
            "reject" -> rejectRefusal(row, body)
            "respond" -> respondRefusal(row, body)
            "verdicts" -> verdictRefusal(row, body)
            else -> null
        }

    private fun strings(element: JsonElement?): List<String>? =
        (element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }

    private fun acceptRefusal(
        row: StubInteraction,
        body: JsonObject,
    ): StubReply? {
        val keys = strings(body["selectedClientKeys"])
        val known = ids(row.payload["tasks"], "clientKey")
        return when {
            keys != null && (keys.isEmpty() || keys.toSet().size != keys.size) -> StubReply.invalid()
            keys != null && keys.any { it !in known } -> StubReply.unprocessable("Unknown selectedClientKey")
            else -> null
        }
    }

    private fun rejectRefusal(
        row: StubInteraction,
        body: JsonObject,
    ): StubReply? {
        val needs = (row.payload["rejectRequiresReason"] as? JsonPrimitive)?.content == "true"
        return if (needs && body.string("reason").isNullOrBlank()) {
            StubReply.unprocessable("A decline reason is required for this confirmation")
        } else {
            null
        }
    }

    private fun ids(
        element: JsonElement?,
        key: String,
    ): List<String> = (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string(key) }

    private fun respondRefusal(
        row: StubInteraction,
        body: JsonObject,
    ): StubReply? {
        val answers = (body["answers"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return StubReply.invalid()
        val questions = (row.payload["questions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val known = questions.mapNotNull { it.string("id") }
        val unknown = answers.firstOrNull { it.string("questionId") !in known }
        val missing =
            questions.firstOrNull { question ->
                (question["required"] as? JsonPrimitive)?.content == "true" &&
                    answers.none {
                        it.string("questionId") == question.string("id") &&
                            strings(it["optionIds"]).orEmpty().isNotEmpty()
                    }
            }
        return when {
            unknown != null -> StubReply.unprocessable("Unknown questionId: ${unknown.string("questionId")}")
            missing != null -> StubReply.unprocessable("Question ${missing.string("id")} requires an answer")
            else -> null
        }
    }

    private fun verdictRefusal(
        row: StubInteraction,
        body: JsonObject,
    ): StubReply? {
        val given = (body["verdicts"] as? JsonArray)?.mapNotNull { it as? JsonObject }
        val known = ids(row.payload["items"], "id")
        val reasons = strings(row.payload["requireReasonOn"]).orEmpty()
        val bad = given?.firstOrNull { it.string("id") !in known }
        val noReason = given?.firstOrNull { it.string("verdict") in reasons && it.string("reason").isNullOrBlank() }
        return when {
            given == null || given.isEmpty() -> {
                StubReply.invalid()
            }

            bad != null -> {
                StubReply.unprocessable("Unknown item id")
            }

            noReason != null -> {
                StubReply.unprocessable(
                    "A reason is required when verdict is ${noReason.string("verdict")}",
                )
            }

            else -> {
                null
            }
        }
    }

    private val KINDS =
        mapOf(
            "accept" to listOf("suggest_tasks", "request_confirmation", "request_checkbox_confirmation"),
            "reject" to listOf("suggest_tasks", "request_confirmation", "request_checkbox_confirmation"),
            "respond" to listOf("ask_user_questions"),
            "verdicts" to listOf("request_item_verdicts"),
        )
}
