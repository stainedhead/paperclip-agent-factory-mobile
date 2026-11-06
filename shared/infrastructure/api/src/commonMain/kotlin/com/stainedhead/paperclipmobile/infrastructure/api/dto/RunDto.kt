package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.infrastructure.api.ApiJson
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

// Shapes from the pinned release 2026.1023.0 (compiled dist): the run row of `server/dist/services/heartbeat/
// run-state.js:17-60` (`id`), a live row (`routes/agents.js:5494-5597`, adds `agentName`) and an issue run
// (`services/activity.js:306-354`, whose id key is `runId` and which has no `issueId` or `companyId`).

/** A heartbeat run as any of the three routes sends it. Only the fields a phone shows are read. */
@Serializable
internal data class RunDto(
    val id: String? = null,
    val runId: String? = null,
    val companyId: String? = null,
    val agentId: String? = null,
    val agentName: String? = null,
    val issueId: String? = null,
    val status: String? = null,
    val invocationSource: String? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val error: String? = null,
)

/**
 * Null (so Malformed) when the run has no id, agent or creation time. An unknown status is `Unknown`, never an error.
 * [issueId] and [agentName] fill what a route does not send (an issue's own runs name no issue, a wake reply no agent).
 */
internal fun RunDto.toDomain(
    issueId: String? = null,
    agentName: String? = null,
): HeartbeatRun? {
    val runKey = (id ?: runId)?.takeIf { it.isNotBlank() } ?: return null
    val agent = agentId?.takeIf { it.isNotBlank() } ?: return null
    return HeartbeatRun(
        id = RunId(runKey),
        agentId = AgentId(agent),
        agentName = (this.agentName ?: agentName)?.takeIf { it.isNotBlank() },
        issueId = (this.issueId ?: issueId)?.takeIf { it.isNotBlank() }?.let(::IssueId),
        status = RunStatus.fromServer(status.orEmpty()),
        invocationSource = invocationSource?.takeIf { it.isNotBlank() },
        createdAt = parseInstantOrNull(createdAt) ?: return null,
        startedAt = parseInstantOrNull(startedAt),
        finishedAt = parseInstantOrNull(finishedAt),
        error = error?.takeIf { it.isNotBlank() },
    )
}

/** A run object read from an open reply, or null when a field has the wrong type. */
internal fun JsonObject.toRunDto(): RunDto? =
    try {
        ApiJson.decodeFromJsonElement(RunDto.serializer(), this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
