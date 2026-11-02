package com.stainedhead.paperclipmobile.application.run

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.failed
import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.LogTail
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunEvents
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.RunGateway

/** A run and the events seen so far (PRD FR-075). */
data class RunView(
    val run: HeartbeatRun,
    val events: List<RunEvent>,
)

/** Loads a run and its newer events. Pass the events already shown; only later ones are requested. */
class LoadRun(
    private val gateway: RunGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: RunId,
        known: List<RunEvent>,
    ): DomainResult<RunView> {
        val run =
            when (val read = retryingRead { gateway.get(scope, id) }) {
                is DomainResult.Failure -> return read
                is DomainResult.Success -> read.value
            }
        val fresh =
            when (val read = retryingRead { gateway.events(scope, id, RunEvents.lastSeq(known)) }) {
                is DomainResult.Failure -> emptyList()
                is DomainResult.Success -> read.value
            }
        return DomainResult.Success(RunView(run, RunEvents.merge(known, fresh)))
    }
}

/** Reads the log from where [LogTail] stopped, a chunk at a time, until nothing new is left (research R1). */
class TailRunLog(
    private val gateway: RunGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        id: RunId,
        tail: LogTail,
    ): DomainResult<LogTail> {
        var current = tail
        repeat(MAX_CHUNKS) {
            val chunk =
                when (val read = retryingRead { gateway.log(scope, id, current.offset) }) {
                    is DomainResult.Failure -> return read
                    is DomainResult.Success -> read.value
                }
            if (chunk.nextOffset == null) return DomainResult.Success(current)
            current = current.append(chunk)
        }
        return DomainResult.Success(current)
    }

    private companion object {
        const val MAX_CHUNKS = 16
    }
}

/**
 * Cancels a run (PRD FR-075). Order (ADR-019): permission, status, device authentication (FR-014), ONE gateway call,
 * one re-read after a timeout or conflict. A viewer never reaches the gateway.
 */
class CancelRun(
    private val gateway: RunGateway,
    private val deviceAuth: DeviceAuthGuard,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        run: HeartbeatRun,
    ): MutationResult<HeartbeatRun> {
        if (!Permissions.allows(context.role, Action.CancelRun)) return MutationResult.Denied
        if (!run.canCancel) return MutationResult.Invalid(InvalidInput.WrongStatus)
        val auth = deviceAuth(Action.CancelRun)
        if (auth != BiometricResult.Approved) return MutationResult.NotAuthorized(auth)
        return when (val result = gateway.cancel(context.scope, run.id)) {
            is DomainResult.Success -> MutationResult.Done(result.value)
            is DomainResult.Failure -> failed(result.error) { gateway.get(context.scope, run.id) }
        }
    }
}

/** The runs of an issue, newest first (PRD FR-061). */
class ListIssueRuns(
    private val gateway: RunGateway,
) {
    suspend operator fun invoke(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<HeartbeatRun>> = retryingRead { gateway.forIssue(scope, issueId) }
}
