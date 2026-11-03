package com.stainedhead.paperclipmobile.infrastructure.api

/**
 * Run bodies in the shapes of the pinned release 2026.1023.0 (compiled dist, not recorded):
 * - the run row of `GET /heartbeat-runs/{id}`, the list and the wake reply (`services/heartbeat/run-state.js:17-60`,
 *   `routes/agents.js:5598-5610`, `5454-5465`): `id`, `agentId`, `issueId`, `status`, times, `error`;
 * - a live row (`routes/agents.js:5494-5597`): the same plus `agentName` from the agents join;
 * - an issue run (`services/activity.js:306-354`): the id key is `runId`, there is no `issueId` and no `companyId`;
 * - an event (`db/dist/schema/heartbeat_run_events.js`) and the log read (`services/run-log-store.js:139-163`).
 */
internal object RunFixtures {
    private fun nullable(text: String?) = text?.let { "\"$it\"" } ?: "null"

    fun run(row: StubRun) =
        """
        {"id":"${row.id}","companyId":"${row.company}","agentId":"${row.agentId}","scopeKind":"issue",
         "issueId":${nullable(row.issueId)},"invocationSource":"assignment","triggerDetail":"manual",
         "status":"${row.status}","startedAt":${nullable(row.startedAt)},"finishedAt":${nullable(row.finishedAt)},
         "createdAt":"${row.createdAt}","error":${nullable(row.error)},"errorCode":null,"exitCode":null,
         "logBytes":${row.log?.encodeToByteArray()?.size ?: 0},"usageJson":null,"resultJson":null,
         "somethingNew":{"a":1}}
        """.trimIndent()

    fun live(row: StubRun) =
        """
        {"id":"${row.id}","companyId":"${row.company}","agentId":"${row.agentId}","agentName":"${row.agentName}",
         "issueId":${nullable(row.issueId)},"invocationSource":"assignment","status":"${row.status}",
         "startedAt":${nullable(row.startedAt)},"finishedAt":null,"createdAt":"${row.createdAt}",
         "adapterType":"claude_local","costUsd":0.12,"livenessState":"advanced"}
        """.trimIndent()

    fun issueRun(row: StubRun) =
        """
        {"runId":"${row.id}","status":"${row.status}","agentId":"${row.agentId}","adapterType":"claude_local",
         "startedAt":${nullable(row.startedAt)},"finishedAt":${nullable(row.finishedAt)},"createdAt":"${row.createdAt}",
         "invocationSource":"assignment","error":${nullable(row.error)},"errorCode":null,"logBytes":0,
         "retryOfRunId":null,"livenessState":"advanced"}
        """.trimIndent()

    fun event(
        run: StubRun,
        e: StubRunEvent,
    ) = """
        {"id":${e.seq + 100},"companyId":"${run.company}","runId":"${run.id}","agentId":"${run.agentId}","seq":${e.seq},
         "eventType":"${e.type}","stream":${nullable(e.stream)},"level":${nullable(e.level)},
         "message":${nullable(e.message)},"payload":{"x":1},"createdAt":"2026-11-01T10:00:0${e.seq % 10}.000Z"}
        """.trimIndent()

    /** The reply of a log read; [next] is absent on the last, short chunk (`run-log-store.js:161`). */
    fun log(
        run: StubRun,
        content: String,
        next: Long?,
    ): String {
        val encoded = kotlinx.serialization.json.JsonPrimitive(content)
        val nextPart = if (next == null) "" else ""","nextOffset":$next"""
        val head = """{"runId":"${run.id}","store":"local_file","logRef":"company/${run.id}.ndjson","content":"""
        return head + encoded + nextPart + "}"
    }
}
