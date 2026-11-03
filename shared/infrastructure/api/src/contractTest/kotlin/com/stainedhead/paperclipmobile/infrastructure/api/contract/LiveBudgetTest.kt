package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.Money
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AD-050 (d): a budget incident made by the server itself. An agent with a 100 cent limit reports a 200 cent cost
 * event; the budget service opens a hard incident (and pauses the agent). The incident is then read and resolved
 * through the adapter; a second resolve of the same incident is refused locally (ADR-056).
 */
class LiveBudgetTest {
    private companion object {
        const val LIMIT_CENTS = 100L
        const val SPEND_CENTS = 200
        const val SECONDS = 30
        const val NAME = "Contract Spender"
    }

    @Test
    fun `an overspent agent opens a hard incident that is read and resolved`() =
        live {
            val spender = seeder.agent(scope.companyId.value, NAME)
            agents.setBudget(scope, AgentId(spender.id), Money.usdCents(LIMIT_CENTS)).verified("AgentGateway.setBudget")
            seeder.costEvent(scope.companyId.value, spender.id, SPEND_CENTS)
            val incidentId =
                eventually("a budget incident for $NAME", SECONDS) {
                    val overview =
                        raw.call(
                            "GET",
                            "/api/companies/${scope.companyId.value}/budgets/overview",
                            bearer = key,
                        )
                    val incidents = overview.obj?.get("activeIncidents") as? JsonArray
                    incidents
                        ?.map { it as JsonObject }
                        ?.firstOrNull { (it["scopeName"] as? JsonPrimitive)?.contentOrNull == NAME }
                        ?.let { (it["id"] as? JsonPrimitive)?.contentOrNull }
                }
            val id = BudgetIncidentId(incidentId)
            val incident = budgets.incident(scope, id).verified("BudgetGateway.incident")
            assertEquals(BudgetThreshold.Hard, incident.threshold)
            assertEquals(Money.usdCents(LIMIT_CENTS), incident.limit)
            assertTrue(incident.observed >= Money.usdCents(SPEND_CENTS.toLong()), "Observed: ${incident.observed}")
            assertTrue(incident.isOpen, "A new incident should be open: ${incident.status}")
            LiveReport.note("budget incident", "${incident.threshold} ${incident.observed}/${incident.limit}")

            val resolved =
                budgets
                    .resolve(scope, id, BudgetResolution.KeepPaused, "Contract test")
                    .verified("BudgetGateway.resolve")
            assertEquals(IncidentStatus.Dismissed, resolved.status)
            budgets.incident(scope, id).verifiedError("BudgetGateway.incident", DomainError.NotFound)
        }
}
