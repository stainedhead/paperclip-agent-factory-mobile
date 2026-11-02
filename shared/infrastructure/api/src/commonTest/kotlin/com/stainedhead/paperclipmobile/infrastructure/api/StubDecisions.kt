package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

internal data class StubDecision(
    val id: String,
    val company: String,
    val options: List<JsonObject> = DecisionFixtures.standardOptions(),
    val inputs: List<JsonObject>? = null,
    var status: String = "open",
    var expiresAt: String = "2026-11-03T12:00:00.000Z",
    val title: String = "Ship the billing migration?",
    val body: String = "Engineer 1 finished PAP-1182. Ship tonight or hold for the review.",
) {
    var executionStatus: String? = null
    var chosenOptionId: String? = null
    var inputValues: Map<String, String>? = null
    var storedKey: String? = null
    var dismissed = false
    var pendingReads = 0
    val executions = mutableListOf<JsonObject>()

    fun option(optionId: String): JsonObject? =
        options.firstOrNull { (it["id"] as? JsonPrimitive)?.content == optionId }

    fun effects(option: JsonObject): List<JsonObject> =
        (option["effects"] as? JsonArray).orEmpty().map { it as JsonObject }
}

/**
 * Decisions as the pinned server runs them (`routes/decisions.ts:122-196`, `services/decisions.ts:673-748`): the
 * replay rules of `decide`, `dismiss` as a decide on the effect-less option, 409 for a final decision, 422 for an
 * unknown option or a bad input. [runningReads] makes a decide leave the execution `running` for that many reads,
 * like a second request that finds another still executing the effects (`decisions.ts:571,589-607`).
 */
internal class StubDecisions(
    private val clock: () -> Instant,
) {
    val decisions = mutableListOf<StubDecision>()
    var runningReads = 0

    private fun find(id: String): StubDecision? = if (isStubUuid(id)) decisions.firstOrNull { it.id == id } else null

    /** A non-uuid id on a uuid column is a database error, 500 (`services/decisions.js:243`). */
    private fun absent(id: String) =
        if (isStubUuid(id)) StubReply.notFound(NOT_FOUND) else StubReply.error(500, "Internal server error")

    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        if (s.size < MIN || s[0] != "decisions") return null
        val decision = find(s[1]) ?: return absent(s[1])
        request.resourceRefusal(decision.company, NOT_FOUND)?.let { return it }
        return when {
            s.size == MIN && request.method == HttpMethod.Get -> {
                StubReply.ok(
                    DecisionFixtures.decision(settle(decision)),
                )
            }

            s.size == SUB && request.method == HttpMethod.Post && s[2] == "decide" -> {
                decide(request, decision)
            }

            s.size == SUB && request.method == HttpMethod.Post && s[2] == "dismiss" -> {
                dismiss(request, decision)
            }

            else -> {
                null
            }
        }
    }

    /** A read that finds the execution running either still sees it running or finishes it. */
    private fun settle(decision: StubDecision): StubDecision {
        if (decision.executionStatus != "running") return decision
        if (decision.pendingReads > 0) {
            decision.pendingReads -= 1
        } else {
            finish(decision)
        }
        return decision
    }

    private fun finish(decision: StubDecision) {
        val option = decision.option(decision.chosenOptionId.orEmpty())
        val effects = option?.let { decision.effects(it) }.orEmpty()
        decision.executions.clear()
        effects.forEachIndexed { index, effect ->
            decision.executions +=
                DecisionFixtures.execution(index, (effect["type"] as JsonPrimitive).content, "executed")
        }
        decision.executionStatus = "succeeded"
    }

    private fun decide(
        request: StubRequest,
        decision: StubDecision,
    ): StubReply {
        val body = request.jsonBody() ?: return StubReply.invalid()
        val optionId = body.string("optionId")
        val values =
            (body["inputValues"] as? JsonObject)?.mapValues {
                (it.value as? JsonPrimitive)
                    ?.takeIf { p ->
                        p.isString
                    }?.content
            }
        val key = body.string("idempotencyKey")
        if (optionId.isNullOrEmpty() || !validValues(body.has("inputValues"), values)) {
            return StubReply.invalid()
        }
        return applyDecision(decision, optionId, values.orEmpty().mapValues { it.value!! }, key, dismissed = false)
    }

    private fun validValues(
        present: Boolean,
        values: Map<String, String?>?,
    ) = if (present) values != null && values.values.none { it == null } else true

    private fun dismiss(
        request: StubRequest,
        decision: StubDecision,
    ): StubReply {
        if (request.jsonBody() == null) return StubReply.invalid()
        val effectLess = decision.options.firstOrNull { decision.effects(it).isEmpty() }
        if (effectLess != null) {
            val id = (effectLess["id"] as JsonPrimitive).content
            return applyDecision(decision, id, emptyMap(), null, dismissed = true)
        }
        if (decision.status != "open") return conflict("decision_already_resolved")
        decision.status = "decided"
        decision.executionStatus = "succeeded"
        decision.chosenOptionId = "dismissed"
        decision.dismissed = true
        return StubReply.ok(DecisionFixtures.decision(decision))
    }

    private fun applyDecision(
        decision: StubDecision,
        optionId: String,
        values: Map<String, String>,
        key: String?,
        dismissed: Boolean,
    ): StubReply {
        val replay = isReplay(decision, optionId, values, key)
        val refusal = if (replay) null else refusal(decision, optionId, values)
        if (replay) return StubReply.ok(DecisionFixtures.decision(settle(decision)))
        if (refusal != null) return refusal
        decision.status = "decided"
        decision.chosenOptionId = optionId
        decision.inputValues = values
        decision.storedKey = key
        decision.dismissed = dismissed
        decision.executionStatus = "running"
        if (runningReads > 0) decision.pendingReads = runningReads
        if (runningReads == 0) finish(decision)
        return StubReply.ok(DecisionFixtures.decision(decision))
    }

    private fun refusal(
        decision: StubDecision,
        optionId: String,
        values: Map<String, String>,
    ): StubReply? {
        if (decision.status != "open") return conflict("decision_already_resolved")
        if (Instant.parse(decision.expiresAt) <= clock()) {
            decision.status = "expired"
            return conflict("decision_expired")
        }
        if (decision.option(optionId) == null) return StubReply.unprocessable("Unknown optionId")
        decision.inputs.orEmpty().forEach { field ->
            val id = (field["id"] as JsonPrimitive).content
            val value = values[id].orEmpty()
            val required = (field["required"] as? JsonPrimitive)?.content == "true"
            val max = (field["maxLength"] as? JsonPrimitive)?.content?.toIntOrNull()
            if (required && value.isBlank()) return StubReply.unprocessable("Input $id is required")
            if (max != null && value.length > max) return StubReply.unprocessable("Input $id is too long")
        }
        return null
    }

    /** `decisions.ts:683-690`: the same key, or the same option with the same inputs, replays the stored decision. */
    private fun isReplay(
        decision: StubDecision,
        optionId: String,
        values: Map<String, String>,
        key: String?,
    ): Boolean {
        if (decision.status != "decided") return false
        val sameKey = key != null && decision.storedKey == key
        return sameKey || (decision.chosenOptionId == optionId && decision.inputValues == values)
    }

    private fun conflict(code: String) = StubReply.error(StubReply.STATUS_CONFLICT, code)

    private companion object {
        const val MIN = 2
        const val SUB = 3
        const val NOT_FOUND = "Decision not found"
    }
}
