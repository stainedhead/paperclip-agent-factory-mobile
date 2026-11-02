package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `GET /decisions/{id}` and both mutations (`services/decisions.ts:277-281`): the decision row plus its `executions`.
 * Options, inputs and executions stay open objects and are read by hand, so an effect type this app does not know
 * cannot fail the whole decision (ADR-055). `signedSpec`, `targetSnapshots`, `ruleKey` and the rest are ignored.
 */
@Serializable
internal data class DecisionDto(
    val id: String? = null,
    val companyId: String? = null,
    val originAgentId: String? = null,
    val originIssueId: String? = null,
    val title: String? = null,
    val body: String? = null,
    val options: List<JsonObject>? = null,
    val inputs: List<JsonObject>? = null,
    val status: String? = null,
    val executionStatus: String? = null,
    val chosenOptionId: String? = null,
    val inputValues: JsonObject? = null,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val metadata: JsonObject? = null,
    val executions: List<JsonObject>? = null,
)

/** Null (so Malformed) when the id, title, body, status, options or time is missing or an option is unusable. */
internal fun DecisionDto.toDomain(): Decision? {
    val created = parseInstantOrNull(createdAt) ?: return null
    val decisionId = id?.takeIf { it.isNotBlank() } ?: return null
    val mappedOptions = (options ?: return null).map { decisionOption(it) }
    val mappedInputs = inputs.orEmpty().map { decisionInput(it) }
    if (title == null || body == null || status == null) return null
    if (mappedOptions.any { it == null } || mappedInputs.any { it == null }) return null
    return Decision(
        id = DecisionId(decisionId),
        title = title,
        body = body,
        status = DecisionStatus.fromServer(status),
        options = mappedOptions.filterNotNull(),
        inputs = mappedInputs.filterNotNull(),
        createdAt = created,
        originAgentId = originAgentId?.takeIf { it.isNotBlank() },
        originIssueId = originIssueId?.takeIf { it.isNotBlank() },
        expiresAt = parseInstantOrNull(expiresAt),
        chosenOptionId = chosenOptionId?.takeIf { it.isNotBlank() },
        inputValues = inputValues.orEmpty().textValues(),
        dismissed = metadata?.flag("dismissed") == true,
        outcome = decisionOutcome(executionStatus, executions.orEmpty()),
    )
}

private fun Map<String, JsonElement>.textValues(): Map<String, String> =
    mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content } }.toMap()
