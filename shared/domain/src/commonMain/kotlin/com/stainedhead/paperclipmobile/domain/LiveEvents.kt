package com.stainedhead.paperclipmobile.domain

/**
 * A live event of the server's WebSocket reduced to what the app invalidates by (PRD FR-091). [type] is the server's
 * `LiveEventType`; the ids come from the event payload and are null when the payload has none.
 */
data class LiveEvent(
    val type: String,
    val agentId: String?,
    val runId: String?,
    val issueId: String?,
)

/** What a live event makes stale in the cache. */
sealed interface Invalidation {
    data class Agent(
        val agentId: String,
    ) : Invalidation

    data class Run(
        val runId: String,
        val agentId: String?,
        val issueId: String?,
    ) : Invalidation

    data class Issue(
        val issueId: String,
    ) : Invalidation

    /** The Attention feed and everything counted from it. */
    data object AttentionFeed : Invalidation
}

/** Maps a live event to the cache entries it invalidates (PRD FR-091). Pure; unknown events invalidate nothing. */
object InvalidationRules {
    private const val RUN_PREFIX = "heartbeat.run."

    fun invalidationsFor(event: LiveEvent): List<Invalidation> =
        when {
            event.type == "agent.status" -> {
                listOfNotNull(event.agentId?.let { Invalidation.Agent(it) })
            }

            event.type.startsWith(RUN_PREFIX) -> {
                listOfNotNull(event.runId?.let { runInvalidation(event, it) })
            }

            event.type == "activity.logged" -> {
                listOfNotNull(event.issueId?.let { Invalidation.Issue(it) }) + Invalidation.AttentionFeed
            }

            else -> {
                emptyList()
            }
        }

    private fun runInvalidation(
        event: LiveEvent,
        runId: String,
    ) = Invalidation.Run(runId, event.agentId, event.issueId)
}
