package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.AttentionFeedDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.DismissalRowDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * The Attention feed and the per-person dismissal and triage endpoints (AD-012), all under
 * `/companies/{companyId}`. The feed is always asked in decide order (`sort=decide`); the cursor is opaque and the
 * server alone judges it, so a stale one comes back as the server's own rejection. Every write is sent once.
 */
class AttentionGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
) : AttentionGateway {
    private val calls = CompanyCalls(client, instances, secrets)

    override suspend fun feed(
        scope: CompanyScope,
        query: FeedQuery,
    ): DomainResult<AttentionFeed> {
        val params =
            buildList {
                add("sort" to "decide")
                add("limit" to query.limit.toString())
                query.cursor?.let { add("cursor" to it) }
            }
        return calls
            .send(scope.instanceId, HttpMethod.Get, company(scope, "attention"), params)
            .decode<AttentionFeedDto>()
            .map { it.toDomain() }
    }

    override suspend fun dismiss(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Dismissal> =
        postDismissal(
            scope,
            buildJsonObject {
                put("itemKey", itemKey)
                put("kind", "dismiss")
            },
        )

    override suspend fun snooze(
        scope: CompanyScope,
        itemKey: String,
        until: Instant,
    ): DomainResult<Dismissal> =
        postDismissal(
            scope,
            buildJsonObject {
                put("itemKey", itemKey)
                put("kind", "snooze")
                put("snoozedUntil", until.toString())
            },
        )

    override suspend fun restore(
        scope: CompanyScope,
        itemKey: String,
    ): DomainResult<Unit> =
        calls.send(scope.instanceId, HttpMethod.Delete, company(scope, "inbox-dismissals", itemKey)).ack()

    override suspend fun setDecideBy(
        scope: CompanyScope,
        sourceKind: AttentionSourceKind,
        sourceId: String,
        decideBy: DecideBy,
    ): DomainResult<Unit> {
        // The server needs the key present to know it is a change: clearing is an explicit null, not a missing field.
        val body = buildJsonObject { put("decideBy", decideBy.toServer()) }
        val path = company(scope, "decision-triage", sourceKind.serverName, sourceId)
        return calls.send(scope.instanceId, HttpMethod.Put, path, jsonBody = body.toString()).ack()
    }

    private suspend fun postDismissal(
        scope: CompanyScope,
        body: JsonObject,
    ): DomainResult<Dismissal> =
        calls
            .send(scope.instanceId, HttpMethod.Post, company(scope, "inbox-dismissals"), jsonBody = body.toString())
            .decode<DismissalRowDto>()
            .map { it.toDomain() }

    private fun company(
        scope: CompanyScope,
        vararg more: String,
    ) = listOf("companies", scope.companyId.value) + more
}
