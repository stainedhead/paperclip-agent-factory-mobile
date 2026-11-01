package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HeartbeatRun
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.LogChunk
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.testing.AgentFixtures

/**
 * Runs with the server's tail rules (research R1): events after a sequence number, log by offset where an empty
 * read has no `nextOffset`, cancel only while the run is active (else 409). [failures] scripts errors by name.
 */
class FakeRunGateway(
    runs: List<HeartbeatRun> = AgentFixtures.runs(),
    private val chunkSize: Int = DEFAULT_CHUNK,
) : RunGateway {
    val store = runs.associateBy { it.id }.toMutableMap()
    val eventStore = mutableListOf<RunEvent>().apply { addAll(AgentFixtures.events()) }
    private var logText = AgentFixtures.LOG
    val calls = mutableMapOf<String, Int>()
    val failures = mutableMapOf<String, DomainError>()

    fun count(name: String): Int = calls[name] ?: 0

    fun appendLog(
        id: RunId,
        text: String,
    ) {
        if (id in store) logText += text
    }

    private fun record(name: String): DomainError? {
        calls[name] = count(name) + 1
        return failures[name]
    }

    override suspend fun get(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun> {
        record("get")?.let { return DomainResult.Failure(it) }
        return store[id]?.let { DomainResult.Success(it) } ?: DomainResult.Failure(DomainError.NotFound)
    }

    override suspend fun events(
        scope: CompanyScope,
        id: RunId,
        afterSeq: Long,
    ): DomainResult<List<RunEvent>> {
        record("events")?.let { return DomainResult.Failure(it) }
        if (id !in store) return DomainResult.Failure(DomainError.NotFound)
        return DomainResult.Success(eventStore.filter { it.seq > afterSeq })
    }

    override suspend fun log(
        scope: CompanyScope,
        id: RunId,
        offset: Long,
    ): DomainResult<LogChunk> {
        record("log")?.let { return DomainResult.Failure(it) }
        if (id !in store) return DomainResult.Failure(DomainError.NotFound)
        val start = offset.toInt().coerceAtMost(logText.length)
        val end = (start + chunkSize).coerceAtMost(logText.length)
        val content = logText.substring(start, end)
        return DomainResult.Success(LogChunk(content, if (content.isEmpty()) null else end.toLong()))
    }

    override suspend fun cancel(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun> {
        record("cancel")?.let { return DomainResult.Failure(it) }
        val run = store[id] ?: return DomainResult.Failure(DomainError.NotFound)
        if (!run.canCancel) return DomainResult.Failure(DomainError.Conflict)
        return DomainResult.Success(run.copy(status = RunStatus.Cancelled).also { store[id] = it })
    }

    override suspend fun forIssue(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<HeartbeatRun>> {
        record("forIssue")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(store.values.filter { it.issueId == issueId })
    }

    override suspend fun forAgent(
        scope: CompanyScope,
        agentId: AgentId,
    ): DomainResult<List<HeartbeatRun>> {
        record("forAgent")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(store.values.filter { it.agentId == agentId })
    }

    override suspend fun live(scope: CompanyScope): DomainResult<List<HeartbeatRun>> {
        record("live")?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(store.values.filter { it.isLive })
    }

    private companion object {
        const val DEFAULT_CHUNK = 65_536
    }
}
