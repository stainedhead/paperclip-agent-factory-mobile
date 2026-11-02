package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod

/**
 * The run endpoints of the pinned release (`routes/agents.js:5454-5642,5990-6026`, `run-log-store.js:139-163`).
 * Behaviours the adapter has to compensate for, all read from that dist:
 * - a run id that is not a uuid is 400 "Invalid heartbeat run ID" (`readHeartbeatRunId`);
 * - cancelling a run that already ended answers 200 with the run unchanged (`run-control.js:538-540`);
 * - a log read returns `nextOffset` only when more bytes follow the chunk, so the last, short chunk has none;
 * - a run with no log yet answers 404 "Run log not found" (`heartbeat.js:3898`).
 */
internal class StubRunHandlers(
    private val store: StubRuns,
) {
    fun handle(request: StubRequest): StubReply? {
        val s = request.segments
        return when {
            s.size >= 2 && s[0] == "heartbeat-runs" -> byId(request, s)
            s.size == COMPANY_LIST && s[0] == "companies" && s[2] == "live-runs" -> live(request, s[1])
            s.size == COMPANY_LIST && s[0] == "companies" && s[2] == "heartbeat-runs" -> forAgent(request, s[1])
            else -> null
        }
    }

    private fun byId(
        request: StubRequest,
        s: List<String>,
    ): StubReply? {
        if (!isServerId(s[1])) return StubReply.error(400, "Invalid heartbeat run ID")
        val run = store.runs.firstOrNull { it.id == s[1] } ?: return StubReply.notFound(NOT_FOUND)
        request.resourceRefusal(run.company, NOT_FOUND)?.let { return it }
        return if (s.size == 2) {
            if (request.method == HttpMethod.Get) StubReply.ok(RunFixtures.run(run)) else null
        } else {
            act(request, run, s[2])
        }
    }

    private fun act(
        request: StubRequest,
        run: StubRun,
        action: String,
    ): StubReply? =
        when {
            request.method == HttpMethod.Get && action == "events" -> events(request, run)
            request.method == HttpMethod.Get && action == "log" -> log(request, run)
            request.method == HttpMethod.Post && action == "cancel" -> cancel(run)
            else -> null
        }

    private fun events(
        request: StubRequest,
        run: StubRun,
    ): StubReply {
        val after = request.query["afterSeq"]?.toLongOrNull() ?: 0L
        val limit = (request.query["limit"]?.toIntOrNull() ?: DEFAULT_EVENTS).coerceIn(1, MAX_EVENTS)
        val rows =
            run.events
                .filter { it.seq > after }
                .sortedBy { it.seq }
                .take(limit)
        return StubReply.ok(rows.joinToString(",", "[", "]") { RunFixtures.event(run, it) })
    }

    private fun log(
        request: StubRequest,
        run: StubRun,
    ): StubReply {
        val text = run.log ?: return StubReply.notFound("Run log not found")
        val bytes = text.encodeToByteArray()
        val offset = (request.query["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0).coerceAtMost(bytes.size.toLong())
        val limit = (request.query["limitBytes"]?.toIntOrNull() ?: DEFAULT_LOG).coerceIn(1, MAX_LOG)
        val read = bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + limit + 1).toInt())
        val next = if (read.size > limit) offset + limit else null
        val content = read.copyOfRange(0, minOf(read.size, limit)).decodeToString()
        return StubReply.ok(RunFixtures.log(run, content, next))
    }

    private fun cancel(run: StubRun): StubReply {
        if (run.status in ACTIVE) {
            run.status = "cancelled"
            run.finishedAt = "2026-11-01T12:00:00.000Z"
            run.error = "Cancelled by a board operator"
        }
        return StubReply.ok(RunFixtures.run(run))
    }

    private fun live(
        request: StubRequest,
        company: String,
    ): StubReply? {
        if (request.method != HttpMethod.Get) return null
        val rows =
            store.runs
                .filter { it.company == company && it.status in LIVE }
                .reversed()
                .take(LIVE_LIMIT)
        return StubReply.ok(rows.joinToString(",", "[", "]") { RunFixtures.live(it) })
    }

    private fun forAgent(
        request: StubRequest,
        company: String,
    ): StubReply? {
        if (request.method != HttpMethod.Get) return null
        val agent = request.query["agentId"]
        val limit = request.query["limit"]?.toIntOrNull()
        val rows = store.runs.filter { it.company == company && (agent == null || it.agentId == agent) }.reversed()
        return StubReply.ok((limit?.let { rows.take(it) } ?: rows).joinToString(",", "[", "]") { RunFixtures.run(it) })
    }

    private companion object {
        const val COMPANY_LIST = 3
        const val DEFAULT_EVENTS = 200
        const val MAX_EVENTS = 1_000
        const val DEFAULT_LOG = 256_000
        const val MAX_LOG = 1_048_576
        const val LIVE_LIMIT = 50
        const val NOT_FOUND = "Heartbeat run not found"
        val ACTIVE = setOf("queued", "running", "scheduled_retry")
        val LIVE = setOf("queued", "running")
    }
}
