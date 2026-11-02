package com.stainedhead.paperclipmobile.infrastructure.api

/**
 * Agent bodies in the shape of the pinned release 2026.1023.0 (compiled dist, not recorded): the raw `agents` row
 * (`db/dist/schema/agents.js:5-35`: `status`, `lifecycleState`, `reportsTo`, `budgetMonthlyCents` int,
 * `spentMonthlyCents` numeric read as a JS number, `pauseReason`, `lastHeartbeatAt`) as the list route sends it after
 * `hydrateAgentSpend` and `normalizeAgentRows` (`server/dist/lib/agent-records.js:233-279`,
 * `routes/agents.js:2980-2994`), plus the config blobs the server redacts and the fields a newer server may add.
 */
internal object AgentRowFixtures {
    fun agent(row: StubAgent): String {
        val reports = row.reportsTo?.let { "\"$it\"" } ?: "null"
        val title = row.title?.let { "\"$it\"" } ?: "null"
        val reason = row.pauseReason?.let { "\"$it\"" } ?: "null"
        return """
            {"id":"${row.id}","companyId":"${row.company}","name":"${row.name}","role":"${row.role}","title":$title,
             "status":"${row.status}","lifecycleState":"${row.lifecycle}","reportsTo":$reports,
             "budgetMonthlyCents":${row.budget},"spentMonthlyCents":${row.spent},"pauseReason":$reason,
             "pausedAt":null,"lastHeartbeatAt":"${row.lastHeartbeat}","adapterType":"claude_local",
             "adapterConfig":{},"runtimeConfig":{},"permissions":{"canCreateAgents":false},
             "orgChainHealth":{"status":"valid"},"appearance":{"color":"#336699"},"avatarUrl":"/avatars/x.png",
             "createdAt":"2026-10-01T09:00:00.000Z","updatedAt":"2026-11-01T11:00:00.000Z","somethingNew":{"a":1}}
            """.trimIndent()
    }
}
