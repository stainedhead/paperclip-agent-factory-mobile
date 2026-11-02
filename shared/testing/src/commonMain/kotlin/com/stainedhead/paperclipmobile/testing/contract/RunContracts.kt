package com.stainedhead.paperclipmobile.testing.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.RunEvents
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.port.RunGateway

/**
 * The read and tail rules of [RunGateway] (research R1: `routes/agents.ts:7038-7675`). [liveRunId] is a running run
 * with at least two events and a log longer than one read chunk, so a tail needs two reads.
 */
abstract class RunReadContract {
    abstract fun create(): RunGateway

    abstract val scope: CompanyScope
    abstract val liveRunId: RunId
    abstract val issueWithRuns: IssueId
    abstract val agentWithRuns: AgentId

    suspend fun runAll() {
        eventsTailByTheLastSequenceNumber()
        theLogTailsByNextOffsetAndAnEmptyReadHasNone()
        liveOnlyListsActiveRuns()
        runsAreListedPerIssueAndPerAgent()
        anUnknownRunIsNotFound()
    }

    private suspend fun eventsTailByTheLastSequenceNumber() {
        val gateway = create()
        val events = gateway.events(scope, liveRunId, 0L).mustSucceed("events")
        check(events.size >= 2 && events == events.sortedBy { it.seq }) { "events come oldest first" }
        val after = gateway.events(scope, liveRunId, RunEvents.lastSeq(events)).mustSucceed("events after the last")
        check(after.isEmpty()) { "afterSeq=lastSeq returns nothing new" }
        val partial = gateway.events(scope, liveRunId, events.first().seq).mustSucceed("events after the first")
        check(partial.none { it.seq <= events.first().seq }) { "only events after afterSeq are returned" }
    }

    private suspend fun theLogTailsByNextOffsetAndAnEmptyReadHasNone() {
        val gateway = create()
        var offset = 0L
        var reads = 0
        var text = ""
        while (true) {
            val chunk = gateway.log(scope, liveRunId, offset).mustSucceed("log")
            check(++reads < MAX_READS) { "the log tail must end" }
            text += chunk.content
            val next = chunk.nextOffset
            if (chunk.content.isEmpty()) {
                check(next == null) { "an empty read has no nextOffset" }
                break
            }
            check(next != null && next > offset) { "a read with content moves nextOffset forward" }
            offset = next
        }
        check(reads >= 2 && text.isNotEmpty()) { "the log needed more than one read" }
    }

    private suspend fun liveOnlyListsActiveRuns() {
        val live = create().live(scope).mustSucceed("live runs")
        check(live.isNotEmpty() && live.all { it.status.isActive }) { "live lists only queued and running runs" }
        check(live.any { it.id == liveRunId }) { "the running run is live" }
    }

    private suspend fun runsAreListedPerIssueAndPerAgent() {
        val gateway = create()
        check(
            gateway.forIssue(scope, issueWithRuns).mustSucceed("runs of the issue").all { it.issueId == issueWithRuns },
        ) {
            "forIssue lists runs of that issue only"
        }
        check(
            gateway.forAgent(scope, agentWithRuns).mustSucceed("runs of the agent").all { it.agentId == agentWithRuns },
        ) {
            "forAgent lists runs of that agent only"
        }
    }

    private suspend fun anUnknownRunIsNotFound() {
        val gateway = create()
        val unknown = RunId("no-such-run")
        gateway.get(scope, unknown).mustFailWith(DomainError.NotFound, "an unknown run")
        gateway.events(scope, unknown, 0L).mustFailWith(DomainError.NotFound, "events of an unknown run")
        gateway.log(scope, unknown, 0L).mustFailWith(DomainError.NotFound, "the log of an unknown run")
    }

    private companion object {
        const val MAX_READS = 1_000
    }
}

/** The cancel rules of [RunGateway]: only an active run can be cancelled (research R1: `routes/agents.ts:7179`). */
abstract class RunWriteContract {
    abstract fun create(): RunGateway

    abstract val scope: CompanyScope
    abstract val liveRunId: RunId
    abstract val finishedRunId: RunId

    suspend fun runAll() {
        cancellingALiveRunMakesItCancelled()
        cancellingAFinishedRunIsAConflict()
    }

    private suspend fun cancellingALiveRunMakesItCancelled() {
        val gateway = create()
        val cancelled = gateway.cancel(scope, liveRunId).mustSucceed("cancel a live run")
        check(cancelled.status == RunStatus.Cancelled) { "a cancelled run reports cancelled" }
        check(gateway.get(scope, liveRunId).mustSucceed("get").status == RunStatus.Cancelled) { "the change is stored" }
        gateway.cancel(scope, liveRunId).mustFailWith(DomainError.Conflict, "cancelling a run that already stopped")
    }

    private suspend fun cancellingAFinishedRunIsAConflict() {
        create().cancel(scope, finishedRunId).mustFailWith(DomainError.Conflict, "cancelling a finished run")
    }
}
