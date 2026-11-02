package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

data class HeartbeatRun(
    val id: RunId,
    val agentId: AgentId,
    val agentName: String?,
    val issueId: IssueId?,
    val status: RunStatus,
    val invocationSource: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val error: String?,
) {
    /** The run can still change: the screen refreshes it every five seconds (PRD FR-090). */
    val isLive: Boolean get() = status.isActive
    val canCancel: Boolean get() = status.canCancel
}

enum class RunStream {
    System,
    Stdout,
    Stderr,
}

enum class RunEventLevel {
    Info,
    Warn,
    Error,
}

data class RunEvent(
    val seq: Long,
    val type: String,
    val stream: RunStream?,
    val level: RunEventLevel?,
    val message: String?,
    val createdAt: Instant,
)

object RunEvents {
    /** Events by `seq`, each once, oldest first. The next read asks for `afterSeq = lastSeq` (research R1). */
    fun merge(
        known: List<RunEvent>,
        incoming: List<RunEvent>,
    ): List<RunEvent> = (known + incoming).distinctBy { it.seq }.sortedBy { it.seq }

    fun lastSeq(events: List<RunEvent>): Long = events.maxOfOrNull { it.seq } ?: 0L
}

/** A read of `GET /heartbeat-runs/{r}/log`; [nextOffset] is where the next read starts, absent when nothing is new. */
data class LogChunk(
    val content: String,
    val nextOffset: Long?,
)

/** The log shown so far, bounded so a long run cannot grow the screen's memory without limit. */
data class LogTail(
    val text: String,
    val offset: Long,
    val droppedStart: Boolean,
) {
    fun append(chunk: LogChunk): LogTail {
        val joined = text + chunk.content
        val overflow = joined.length - MAX_CHARS
        return LogTail(
            text = if (overflow > 0) joined.substring(overflow) else joined,
            offset = chunk.nextOffset ?: offset,
            droppedStart = droppedStart || overflow > 0,
        )
    }

    companion object {
        const val MAX_CHARS = 200_000
        val Empty = LogTail("", 0L, false)
    }
}

/** What `heartbeat/invoke` did: it started a run, or the server skipped the wake. */
sealed interface InvokeOutcome {
    data class Started(
        val run: HeartbeatRun,
    ) : InvokeOutcome

    data object Skipped : InvokeOutcome
}
