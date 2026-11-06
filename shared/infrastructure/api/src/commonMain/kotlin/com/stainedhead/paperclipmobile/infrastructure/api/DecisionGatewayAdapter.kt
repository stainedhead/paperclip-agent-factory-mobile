package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.DecisionDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `/api/decisions/{id}` (AD-012). A decision carries no company in its path, so a read checks the company of the row it
 * got and a write reads the decision first, then posts once (ADR-057). `decide` always sends its idempotency key: a
 * manual re-tap after a timeout replays on the server instead of running the effects twice (ADR-018). Polling is the
 * caller reading again; this adapter never loops and never retries a write.
 */
class DecisionGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
    keyReadReason: String = "",
) : DecisionGateway {
    private val calls = CompanyCalls(client, instances, secrets, keyReadReason)

    override suspend fun get(
        scope: CompanyScope,
        id: DecisionId,
    ): DomainResult<Decision> = fetch(scope, id).map { it.toDomain() }

    override suspend fun decide(
        scope: CompanyScope,
        id: DecisionId,
        optionId: String,
        inputValues: Map<String, String>,
        idempotencyKey: String,
    ): DomainResult<Decision> {
        val body =
            buildJsonObject {
                put("optionId", optionId)
                put("inputValues", JsonObject(inputValues.mapValues { JsonPrimitive(it.value) }))
                put("idempotencyKey", idempotencyKey)
            }
        return mutate(scope, id, "decide", body)
    }

    override suspend fun dismiss(
        scope: CompanyScope,
        id: DecisionId,
        reason: String?,
    ): DomainResult<Decision> = mutate(scope, id, "dismiss", buildJsonObject { reason?.let { put("reason", it) } })

    private suspend fun mutate(
        scope: CompanyScope,
        id: DecisionId,
        action: String,
        body: JsonObject,
    ): DomainResult<Decision> {
        val guard = fetch(scope, id)
        if (guard is DomainResult.Failure) return guard
        return calls
            .send(scope.instanceId, HttpMethod.Post, listOf("decisions", id.value, action), jsonBody = body.toString())
            .decode<DecisionDto>()
            .ownedBy(scope) { it.companyId }
            .map { it.toDomain() }
    }

    private suspend fun fetch(
        scope: CompanyScope,
        id: DecisionId,
    ): DomainResult<DecisionDto> {
        if (!isServerId(id.value)) return DomainResult.Failure(DomainError.NotFound)
        return calls
            .send(scope.instanceId, HttpMethod.Get, listOf("decisions", id.value))
            .decode<DecisionDto>()
            .ownedBy(scope) { it.companyId }
    }
}
