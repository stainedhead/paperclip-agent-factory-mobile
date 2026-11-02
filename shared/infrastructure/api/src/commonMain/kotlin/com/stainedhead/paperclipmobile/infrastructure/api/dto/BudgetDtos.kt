package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.Money
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** `GET .../budgets/overview` (types/budget.ts:81-88); the app reads only the open incidents. *
 * Re-verified in 1b-4b against the pinned dist: `server/dist/services/budgets.js:395-428`,
 * `db/dist/schema/budget_incidents.js`.
 */
@Serializable
internal data class BudgetOverviewDto(
    val activeIncidents: List<BudgetIncidentDto>,
)

/**
 * `BudgetIncident` (types/budget.ts:61-77; services/budgets.ts:604-626). Amounts are JSON numbers in cents:
 * `amountObserved` can be fractional (`numeric(24,7)`), so both are kept as the literal and parsed exactly.
 */
@Serializable
internal data class BudgetIncidentDto(
    val id: String? = null,
    val scopeName: String? = null,
    val scopeType: String? = null,
    val thresholdType: String? = null,
    val amountLimit: JsonPrimitive? = null,
    val amountObserved: JsonPrimitive? = null,
    val status: String? = null,
    val approvalId: String? = null,
)

/**
 * The incident as a domain value, or null when it lacks an id or a readable amount. A threshold other than `hard` reads
 * as soft, which offers the fewer actions; an absent or new status reads as Unknown so nothing is shown as open.
 */
internal fun BudgetIncidentDto.toDomain(): BudgetIncident? {
    val observed = amountObserved?.content?.let(::ceilToCents)
    val limit = amountLimit?.content?.let(::ceilToCents)
    if (id.isNullOrBlank() || observed == null || limit == null) return null
    return BudgetIncident(
        id = BudgetIncidentId(id),
        scopeName = scopeName.orEmpty(),
        scopeType = scopeType.orEmpty(),
        threshold = if (thresholdType == "hard") BudgetThreshold.Hard else BudgetThreshold.Soft,
        observed = Money.usdCents(observed),
        limit = Money.usdCents(limit),
        status = status?.let(IncidentStatus::fromServer) ?: IncidentStatus.Unknown,
        approvalId = approvalId?.takeIf { it.isNotBlank() }?.let(::ApprovalId),
    )
}
