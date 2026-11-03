package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DashboardSummary
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.SpendRow
import com.stainedhead.paperclipmobile.domain.port.DashboardGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.DashboardDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.spendRows
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject

/**
 * Factory home (AD-013): `GET /companies/{c}/dashboard` and `GET /companies/{c}/costs/window-spend`. Both are reads,
 * sent once per call; the company is in the path, so the server decides what this key may see. Money is read from the
 * text of the server's numbers, never through a float (ADR-054).
 */
class DashboardGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
) : DashboardGateway {
    private val calls = CompanyCalls(client, instances, secrets)

    override suspend fun dashboard(scope: CompanyScope): DomainResult<DashboardSummary> =
        calls
            .send(scope.instanceId, HttpMethod.Get, listOf("companies", scope.companyId.value, "dashboard"))
            .decode<DashboardDto>()
            .map { it.toDomain() }

    override suspend fun windowSpend(scope: CompanyScope): DomainResult<List<SpendRow>> =
        calls
            .send(scope.instanceId, HttpMethod.Get, listOf("companies", scope.companyId.value, "costs", "window-spend"))
            .decode<List<JsonObject>>()
            .map { spendRows(it) }
}
