package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.port.HealthGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.infrastructure.api.dto.HealthDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import io.ktor.http.HttpMethod

/**
 * `GET {baseUri}/api/health`, sent with no credentials (AD-010). It carries the pins of the factory configured at
 * that address, if any, so a pinned factory is never probed unpinned (review1b F-A-1, ADR-075).
 */
class HealthGatewayAdapter(
    private val client: PaperclipHttpClient,
    instances: InstanceRepository,
) : HealthGateway {
    private val pinsOf = PinLookup(instances)

    override suspend fun probe(baseUri: BaseUri): DomainResult<HealthReport> =
        client
            .send(ApiRequest(Destination(baseUri, pinsOf(baseUri)), HttpMethod.Get, listOf("health")))
            .decode<HealthDto>()
            .map { it.toDomain() }
}
