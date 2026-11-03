package com.stainedhead.paperclipmobile.infrastructure.api

/** One heartbeat run of the stub. [log] is null until the run has written one; [events] are in `seq` order. */
internal class StubRun(
    val id: String,
    val company: String,
    val agentId: String,
    val agentName: String,
    val issueId: String?,
    var status: String,
) {
    var createdAt = "2026-11-01T10:00:00.000Z"
    var startedAt: String? = createdAt
    var finishedAt: String? = null
    var error: String? = null
    var log: String? = null
    val events = mutableListOf<StubRunEvent>()
}

internal class StubRunEvent(
    val seq: Long,
    val type: String,
    val stream: String?,
    val level: String?,
    val message: String?,
)

/** The runs of one host: the agent stub starts them here and the run handlers read them. */
internal class StubRuns {
    val runs = mutableListOf<StubRun>()
    private var counter = 0

    fun start(
        company: String,
        agentId: String,
        agentName: String,
    ): StubRun {
        counter += 1
        val run =
            StubRun(
                "a0000000-0000-4000-8000-" + counter.toString().padStart(ID_TAIL, '0'),
                company,
                agentId,
                agentName,
                null,
                "queued",
            )
        run.startedAt = null
        runs += run
        return run
    }

    private companion object {
        const val ID_TAIL = 12
    }
}
