package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test

private fun event(
    type: String,
    agentId: String? = null,
    runId: String? = null,
    issueId: String? = null,
) = LiveEvent(type, agentId, runId, issueId)

/** FR-091: which cached entities a live event makes stale (names from `LIVE_EVENT_TYPES` of the pinned server). */
class InvalidationRulesTest {
    @Test
    fun `FR-091 agent status invalidates that agent`() {
        InvalidationRules.invalidationsFor(event("agent.status", agentId = "a1")) shouldBe
            listOf(Invalidation.Agent("a1"))
    }

    @Test
    fun `FR-091 every heartbeat run event invalidates the run with its agent and issue`() {
        val types =
            listOf(
                "heartbeat.run.queued",
                "heartbeat.run.status",
                "heartbeat.run.progress",
                "heartbeat.run.event",
                "heartbeat.run.log",
                "heartbeat.run.activity",
            )
        types.forEach { type ->
            InvalidationRules.invalidationsFor(event(type, "a1", "r1", "i1")) shouldBe
                listOf(Invalidation.Run("r1", "a1", "i1"))
        }
    }

    @Test
    fun `FR-091 activity logged invalidates the referenced issue and the attention feed`() {
        InvalidationRules.invalidationsFor(event("activity.logged", issueId = "i1")) shouldBe
            listOf(Invalidation.Issue("i1"), Invalidation.AttentionFeed)
    }

    @Test
    fun `FR-091 activity without an issue invalidates only the attention feed`() {
        InvalidationRules.invalidationsFor(event("activity.logged")) shouldBe listOf(Invalidation.AttentionFeed)
    }

    @Test
    fun `an unknown event type or a missing id invalidates nothing`() {
        InvalidationRules.invalidationsFor(event("plugin.ui.updated", "a1", "r1", "i1")) shouldBe emptyList()
        InvalidationRules.invalidationsFor(event("agent.status")) shouldBe emptyList()
        InvalidationRules.invalidationsFor(event("heartbeat.run.status", agentId = "a1")) shouldBe emptyList()
    }
}
