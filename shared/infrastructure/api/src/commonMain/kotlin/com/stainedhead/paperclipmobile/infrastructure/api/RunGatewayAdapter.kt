package com.stainedhead.paperclipmobile.infrastructure.api

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
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.RunDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.RunEventDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.RunLogDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toChunk
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toRunDto
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Heartbeat runs, their events and log (AD-013). A run is addressed by its id alone, so every call starts with a read
 * that proves the run belongs to the scope's company (ADR-061), and an id that is not a uuid is NotFound without a
 * request (the server answers 400 "Invalid heartbeat run ID"). The log is read by byte offset in chunks of
 * [LOG_CHUNK_BYTES]. Cancelling reads the run first because the server answers 200 with the run unchanged when it has
 * already ended (`run-control.js:538-540`): an ended run is a Conflict here and no mutation is sent (ADR-062).
 */
class RunGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
) : RunGateway {
    private val calls = CompanyCalls(client, instances, secrets)

    override suspend fun get(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun> = existing(scope, id).map { it.toDomain() }

    override suspend fun events(
        scope: CompanyScope,
        id: RunId,
        afterSeq: Long,
    ): DomainResult<List<RunEvent>> {
        val run = existing(scope, id)
        if (run is DomainResult.Failure) return run
        val query = listOf("afterSeq" to afterSeq.coerceAtLeast(0).toString(), "limit" to EVENT_PAGE.toString())
        return calls
            .send(scope.instanceId, HttpMethod.Get, listOf("heartbeat-runs", id.value, "events"), query)
            .decode<List<RunEventDto>>()
            .map { rows -> rows.map { it.toDomain() }.takeIf { mapped -> mapped.none { it == null } } }
            .map { events ->
                events
                    .filterNotNull()
                    .filter { it.seq > afterSeq }
                    .distinctBy { it.seq }
                    .sortedBy { it.seq }
            }
    }

    override suspend fun log(
        scope: CompanyScope,
        id: RunId,
        offset: Long,
    ): DomainResult<LogChunk> {
        val run = existing(scope, id)
        if (run is DomainResult.Failure) return run
        val start = offset.coerceAtLeast(0)
        val query = listOf("offset" to start.toString(), "limitBytes" to LOG_CHUNK_BYTES.toString())
        val reply = calls.send(scope.instanceId, HttpMethod.Get, listOf("heartbeat-runs", id.value, "log"), query)
        // The run was just read, so a 404 here is "no log written yet", not an unknown run.
        if (reply is DomainResult.Failure && reply.error == DomainError.NotFound) {
            return DomainResult.Success(LogChunk("", null))
        }
        return reply.decode<RunLogDto>().map { it.toChunk(start) }
    }

    override suspend fun cancel(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<HeartbeatRun> {
        val before = existing(scope, id).map { it.toDomain() }
        if (before !is DomainResult.Success) return before
        if (!before.value.canCancel) return DomainResult.Failure(DomainError.Conflict)
        val reply =
            calls
                .send(scope.instanceId, HttpMethod.Post, listOf("heartbeat-runs", id.value, "cancel"))
                .decode<JsonElement>()
        return when (reply) {
            is DomainResult.Success -> settled(scope, reply.value)
            is DomainResult.Failure -> reply
        }
    }

    override suspend fun forIssue(
        scope: CompanyScope,
        issueId: IssueId,
    ): DomainResult<List<HeartbeatRun>> {
        val issue = calls.requireIssue(scope, issueId)
        if (issue is DomainResult.Failure) return issue
        return calls
            .send(scope.instanceId, HttpMethod.Get, listOf("issues", issueId.value, "runs"))
            .decode<List<RunDto>>()
            .map { rows -> allOrNone(rows) { it.toDomain(issueId = issueId.value) } }
    }

    override suspend fun forAgent(
        scope: CompanyScope,
        agentId: AgentId,
    ): DomainResult<List<HeartbeatRun>> {
        if (!isServerId(agentId.value)) return DomainResult.Failure(DomainError.NotFound)
        val query = listOf("agentId" to agentId.value, "limit" to AGENT_RUNS.toString())
        return calls
            .send(scope.instanceId, HttpMethod.Get, listOf("companies", scope.companyId.value, "heartbeat-runs"), query)
            .decode<List<RunDto>>()
            .map { rows -> allOrNone(rows) { it.toDomain() } }
    }

    override suspend fun live(scope: CompanyScope): DomainResult<List<HeartbeatRun>> =
        calls
            .send(scope.instanceId, HttpMethod.Get, listOf("companies", scope.companyId.value, "live-runs"))
            .decode<List<RunDto>>()
            .map { rows -> allOrNone(rows) { it.toDomain() } }

    private suspend fun existing(
        scope: CompanyScope,
        id: RunId,
    ): DomainResult<RunDto> =
        if (!isServerId(id.value)) {
            DomainResult.Failure(DomainError.NotFound)
        } else {
            calls
                .send(scope.instanceId, HttpMethod.Get, listOf("heartbeat-runs", id.value))
                .decode<RunDto>()
                .ownedBy(scope) { it.companyId }
        }

    /** The cancel reply: a run that did not end as cancelled while it was not active is a Conflict, `null` is gone. */
    private fun settled(
        scope: CompanyScope,
        reply: JsonElement,
    ): DomainResult<HeartbeatRun> {
        val dto = (reply as? JsonObject)?.toRunDto()
        val refusal = if (reply is JsonNull) DomainError.NotFound else dto?.let { ownershipOf(scope, it.companyId) }
        val run = dto?.toDomain()
        return when {
            refusal != null -> DomainResult.Failure(refusal)
            run == null -> DomainResult.Failure(DomainError.Malformed)
            !run.isLive && run.status != RunStatus.Cancelled -> DomainResult.Failure(DomainError.Conflict)
            else -> DomainResult.Success(run)
        }
    }

    private inline fun <T> allOrNone(
        rows: List<RunDto>,
        transform: (RunDto) -> T?,
    ): List<T>? = rows.map(transform).takeIf { mapped -> mapped.none { it == null } }?.filterNotNull()

    private companion object {
        const val LOG_CHUNK_BYTES = 65_536
        const val EVENT_PAGE = 500
        const val AGENT_RUNS = 50
    }
}
