package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.LogChunk
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunEventLevel
import com.stainedhead.paperclipmobile.domain.RunStream
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

// Shapes from the pinned release 2026.1023.0 (compiled dist): an event is a `heartbeat_run_events` row
// (`db/dist/schema/heartbeat_run_events.js`, `seq` is a bigint read as a number, the `payload` is not read) and a log
// read is `{runId, store, logRef, content, nextOffset?}` (`server/dist/services/heartbeat.js:3891-3912`). The log
// reference is a server file path and is never read.

@Serializable
internal data class RunEventDto(
    val seq: JsonPrimitive? = null,
    val eventType: String? = null,
    val stream: String? = null,
    val level: String? = null,
    val message: String? = null,
    val createdAt: String? = null,
)

/** Null (so Malformed) without a whole sequence number, a type or a time. An unknown stream or level is none. */
internal fun RunEventDto.toDomain(): RunEvent? {
    val number = seq?.takeIf { !it.isString }?.longOrNull ?: return null
    val kind = eventType?.takeIf { it.isNotBlank() } ?: return null
    return RunEvent(
        seq = number,
        type = kind,
        stream = streamOf(stream),
        level = levelOf(level),
        message = message,
        createdAt = parseInstantOrNull(createdAt) ?: return null,
    )
}

private fun streamOf(raw: String?): RunStream? =
    when (raw) {
        "system" -> RunStream.System
        "stdout" -> RunStream.Stdout
        "stderr" -> RunStream.Stderr
        else -> null
    }

private fun levelOf(raw: String?): RunEventLevel? =
    when (raw) {
        "info" -> RunEventLevel.Info
        "warn" -> RunEventLevel.Warn
        "error" -> RunEventLevel.Error
        else -> null
    }

@Serializable
internal data class RunLogDto(
    val content: String? = null,
    val nextOffset: JsonPrimitive? = null,
)

/**
 * The chunk read from [offset] (a byte offset). The server sends `nextOffset` only when more bytes follow, so on the
 * last, short chunk it is absent although the content is new: the next read then starts after the bytes just read.
 * Null (so Malformed) without `content`; empty content is a valid read that is caught up.
 */
internal fun RunLogDto.toChunk(offset: Long): LogChunk? {
    val text = content ?: return null
    val sent = nextOffset?.takeIf { !it.isString }?.longOrNull
    val next =
        when {
            sent != null -> sent
            text.isEmpty() -> null
            else -> offset + text.encodeToByteArray().size
        }
    return LogChunk(text, next)
}
