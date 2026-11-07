package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import io.ktor.http.HttpMethod

/**
 * One authenticated call to a configured factory: the address, pins and key come from the factory the scope names and
 * from nowhere else, so a company-scoped call can only reach the instance that owns that company (AD-001, AD-014).
 */
internal class CompanyCalls(
    private val client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
    keyReadReason: String,
) {
    private val stored = StoredCredentials(instances, secrets, keyReadReason)

    suspend fun send(
        instanceId: InstanceId,
        method: HttpMethod,
        segments: List<String>,
        query: List<Pair<String, String>> = emptyList(),
        jsonBody: String? = null,
    ): DomainResult<ApiResponse> =
        stored.call(instanceId) { instance, key ->
            val destination = Destination(instance.baseUri, instance.pins)
            client.send(ApiRequest(destination, method, segments, query, jsonBody, key))
        }

    /** A byte read with a `Range` header; the same address, pins and key rules as [send]. */
    suspend fun sendBytes(
        instanceId: InstanceId,
        segments: List<String>,
        range: String,
        maxBytes: Long,
    ): DomainResult<ApiBytes> =
        stored.call(instanceId) { instance, key ->
            val destination = Destination(instance.baseUri, instance.pins)
            client.sendBytes(ApiRequest(destination, HttpMethod.Get, segments, bearer = key), range, maxBytes)
        }
}
