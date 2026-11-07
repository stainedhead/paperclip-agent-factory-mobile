package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.BudgetIncidentDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.BudgetOverviewDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Budget incidents (AD-012). There is no single-incident endpoint, so both calls start from
 * `GET /companies/{c}/budgets/overview`. A resolve is only sent for an incident that is still in `activeIncidents`:
 * the server answers 200 to a second resolve (its keep-paused update has no status filter), so the guard is ours
 * (ADR-056). The mutation itself is sent once and never retried.
 */
class BudgetGatewayAdapter(
    client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
    keyReadReason: String = "",
) : BudgetGateway {
    private val calls = CompanyCalls(client, instances, secrets, keyReadReason)

    override suspend fun incident(
        scope: CompanyScope,
        id: BudgetIncidentId,
    ): DomainResult<BudgetIncident> = activeIncident(scope, id).map { it.toDomain() }

    override suspend fun resolve(
        scope: CompanyScope,
        id: BudgetIncidentId,
        resolution: BudgetResolution,
        note: String?,
    ): DomainResult<BudgetIncident> {
        refusal(resolution)?.let { return DomainResult.Failure(it) }
        val active = activeIncident(scope, id)
        if (active is DomainResult.Failure) return active
        val request =
            calls.send(
                scope.instanceId,
                HttpMethod.Post,
                listOf("companies", scope.companyId.value, "budget-incidents", id.value, "resolve"),
                jsonBody = body(resolution, note),
            )
        return request.decode<BudgetIncidentDto>().map { it.toDomain() }
    }

    private suspend fun activeIncident(
        scope: CompanyScope,
        id: BudgetIncidentId,
    ): DomainResult<BudgetIncidentDto> {
        val overview =
            calls
                .send(
                    scope.instanceId,
                    HttpMethod.Get,
                    listOf("companies", scope.companyId.value, "budgets", "overview"),
                ).decode<BudgetOverviewDto>()
        return when (overview) {
            is DomainResult.Failure -> {
                overview
            }

            is DomainResult.Success -> {
                overview.value.activeIncidents
                    .firstOrNull { it.id == id.value }
                    ?.let { DomainResult.Success(it) } ?: DomainResult.Failure(DomainError.NotFound)
            }
        }
    }

    private fun refusal(resolution: BudgetResolution): DomainError? {
        if (resolution !is BudgetResolution.RaiseAndResume) return null
        return when {
            resolution.amount.currency != Money.USD -> DomainError.Rejected("Only US dollars can be raised")
            resolution.amount.minorUnits > MAX_AMOUNT -> DomainError.Rejected("The amount is too large")
            else -> null
        }
    }

    private fun body(
        resolution: BudgetResolution,
        note: String?,
    ): String =
        buildJsonObject {
            when (resolution) {
                BudgetResolution.KeepPaused -> {
                    put("action", KEEP_PAUSED)
                }

                is BudgetResolution.RaiseAndResume -> {
                    put("action", RAISE)
                    put("amount", resolution.amount.minorUnits)
                }
            }
            note?.takeIf { it.isNotBlank() }?.let { put("decisionNote", it) }
        }.toString()

    private companion object {
        const val KEEP_PAUSED = "keep_paused"
        const val RAISE = "raise_budget_and_resume"
        const val MAX_AMOUNT = 2_147_483_647L
    }
}
