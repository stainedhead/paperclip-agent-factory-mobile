package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.BudgetIncident
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IncidentStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.BudgetGateway
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val inc1 = BudgetIncidentId("inc-1")
private val inc2 = BudgetIncidentId("inc-2")

private fun failure(error: DomainError) = DomainResult.Failure(error)

private fun <T> DomainResult<T>.ok(): T = (this as DomainResult.Success).value

private fun StubWork.seed(vararg rows: StubIncident) {
    budgets.incidents += rows
}

private fun row(
    id: String,
    company: String = ServerFixtures.COMPANY_ACME,
    observed: String = "10000",
) = StubIncident(id = id, company = company, policyId = "policy-$id", observed = observed)

private fun gatewayAnswering(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    contentType: String = "application/json",
): BudgetGateway {
    val world = WorkWorld()
    val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }
    return BudgetGatewayAdapter(PaperclipHttpClient(engine, allowInsecureLocal = false), world.instances, world.secrets)
}

/** AD-012: budget incidents through `budgets/overview` and `budget-incidents/{id}/resolve`. */
class BudgetGatewayAdapterTest {
    private val world = WorkWorld()

    @Test
    fun `AD-012 incident reads the overview of its own company with the stored key and nothing else`() =
        runTest {
            world.home.seed(row("inc-1"))
            world.budget.incident(world.acme, inc1).ok()
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/companies/company-acme/budgets/overview", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
            assertEquals("", request.query)
        }

    @Test
    fun `AD-012 incident maps the scope amounts threshold and approval`() =
        runTest {
            world.home.seed(
                row("inc-1", observed = "12345.5000000").copy(
                    limit = "20000",
                    threshold = "soft",
                    scopeType = "company",
                    scopeName = "Acme Labs",
                    approvalId = "ap-9",
                ),
            )
            val incident: BudgetIncident = world.budget.incident(world.acme, inc1).ok()
            assertEquals("Acme Labs", incident.scopeName)
            assertEquals("company", incident.scopeType)
            assertEquals(BudgetThreshold.Soft, incident.threshold)
            assertEquals(Money.usdCents(12_346), incident.observed)
            assertEquals(Money.usdCents(20_000), incident.limit)
            assertEquals(IncidentStatus.Open, incident.status)
            assertEquals(ApprovalId("ap-9"), incident.approvalId)
        }

    @Test
    fun `AD-012 observed spend is rounded up to whole cents with exact arithmetic`() =
        runTest {
            world.home.seed(
                row("inc-1", observed = "9999.0000001"),
                row("inc-2", observed = "10000.0000000"),
            )
            assertEquals(
                Money.usdCents(10_000),
                world.budget
                    .incident(world.acme, inc1)
                    .ok()
                    .observed,
            )
            assertEquals(
                Money.usdCents(10_000),
                world.budget
                    .incident(world.acme, inc2)
                    .ok()
                    .observed,
            )
        }

    @Test
    fun `AD-012 other incidents in the overview do not matter and an unknown status reads as Unknown`() =
        runTest {
            world.home.seed(row("inc-1").copy(status = "open"), row("inc-2"))
            val body =
                ServerFixtures.error("x").let {
                    BudgetFixtures.overview(
                        "company-acme",
                        listOf(
                            BudgetFixtures.incident(row("inc-1")).replace("\"open\"", "\"paused_by_future\""),
                            """{"id":"inc-2","amountObserved":"not a number"}""",
                        ),
                    )
                }
            val gateway = gatewayAnswering(body)
            assertEquals(IncidentStatus.Unknown, gateway.incident(world.acme, inc1).ok().status)
            assertEquals(failure(DomainError.Malformed), gateway.incident(world.acme, inc2))
        }

    @Test
    fun `AD-012 an incident that is not in the active list is NotFound`() =
        runTest {
            world.home.seed(row("inc-1").copy(status = "resolved"))
            assertEquals(failure(DomainError.NotFound), world.budget.incident(world.acme, inc1))
            assertEquals(failure(DomainError.NotFound), world.budget.incident(world.acme, inc2))
        }

    @Test
    fun `AD-003 empty html array and wrongly typed bodies are Malformed`() =
        runTest {
            listOf(
                "{}",
                "[]",
                "null",
                """{"activeIncidents":"none"}""",
                """{"activeIncidents":[{"scopeName":"no id"}]}""",
            ).forEach { body ->
                val result = gatewayAnswering(body).incident(world.acme, inc1)
                assertTrue(
                    result == failure(DomainError.Malformed) || result == failure(DomainError.NotFound),
                    "$body -> $result",
                )
            }
            assertEquals(failure(DomainError.Malformed), gatewayAnswering("{}").incident(world.acme, inc1))
            assertEquals(
                failure(DomainError.Malformed),
                gatewayAnswering("<html></html>", contentType = "text/html").incident(world.acme, inc1),
            )
            assertEquals(failure(DomainError.Malformed), gatewayAnswering("").incident(world.acme, inc1))
        }

    @Test
    fun `AD-012 keep paused posts the action once after reading the overview and returns Dismissed`() =
        runTest {
            world.home.seed(row("inc-1"))
            val result = world.budget.resolve(world.acme, inc1, BudgetResolution.KeepPaused, null).ok()
            assertEquals(IncidentStatus.Dismissed, result.status)
            val sent = world.server.requests
            assertEquals(listOf("GET", "POST"), sent.map { it.method })
            assertEquals("/api/companies/company-acme/budget-incidents/inc-1/resolve", sent[1].path)
            assertEquals("""{"action":"keep_paused"}""", sent[1].body)
        }

    @Test
    fun `AD-012 a raise sends the amount in cents and the note`() =
        runTest {
            world.home.seed(row("inc-1"))
            val raise = BudgetResolution.RaiseAndResume(Money.usdCents(10_001))
            val result = world.budget.resolve(world.acme, inc1, raise, "Approved by Alex").ok()
            assertEquals(IncidentStatus.Resolved, result.status)
            assertEquals(
                """{"action":"raise_budget_and_resume","amount":10001,"decisionNote":"Approved by Alex"}""",
                world.server.requests
                    .last()
                    .body,
            )
        }

    @Test
    fun `AD-012 resolving an incident that is no longer active sends no mutation`() =
        runTest {
            world.home.seed(row("inc-1").copy(status = "dismissed"))
            assertEquals(
                failure(DomainError.NotFound),
                world.budget.resolve(world.acme, inc1, BudgetResolution.KeepPaused, null),
            )
            assertEquals(listOf("GET"), world.server.requests.map { it.method })
        }

    @Test
    fun `AD-012 a raise the server cannot take is refused here and nothing is sent`() =
        runTest {
            world.home.seed(row("inc-1"))
            val tooBig = BudgetResolution.RaiseAndResume(Money(2_147_483_648L, "USD"))
            val euros = BudgetResolution.RaiseAndResume(Money(20_000, "EUR"))
            assertTrue(world.budget.resolve(world.acme, inc1, tooBig, null).isRejected())
            assertTrue(world.budget.resolve(world.acme, inc1, euros, null).isRejected())
            assertTrue(world.server.requests.isEmpty())
        }

    private fun DomainResult<*>.isRejected() = (this as? DomainResult.Failure)?.error is DomainError.Rejected

    @Test
    fun `AD-012 the three 422 reasons of the server arrive as the reason`() =
        runTest {
            world.home.seed(row("inc-1"))
            val raise = BudgetResolution.RaiseAndResume(Money.usdCents(10_001))
            world.home.budgets.unpricedUsage = true
            assertEquals(
                failure(
                    DomainError.Rejected(
                        "Resolve unpriced usage or explicitly allow it in the budget policy before resuming",
                    ),
                ),
                world.budget.resolve(world.acme, inc1, raise, null),
            )
            world.home.budgets.unpricedUsage = false
            world.home.budgets.pendingRuns = true
            assertEquals(
                failure(DomainError.Rejected("Completed runs must finish accounting before resuming")),
                world.budget.resolve(world.acme, inc1, raise, null),
            )
            world.home.budgets.pendingRuns = false
            val same = BudgetResolution.RaiseAndResume(Money.usdCents(10_000))
            assertEquals(
                failure(DomainError.Rejected("New budget must exceed current observed spend")),
                world.budget.resolve(world.acme, inc1, same, null),
            )
        }

    @Test
    fun `AD-002 a failing resolve is sent once and never retried`() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.method.value == "GET") {
                        respond(
                            BudgetFixtures.overview("company-acme", listOf(BudgetFixtures.incident(row("inc-1")))),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            ServerFixtures.error("boom"),
                            HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            val gateway = BudgetGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            assertEquals(
                failure(DomainError.ServerError),
                gateway.resolve(world.acme, inc1, BudgetResolution.KeepPaused, null),
            )
            assertEquals(1, engine.requestHistory.count { it.method.value == "POST" })
        }

    @Test
    fun `AD-012 a viewer may read but a resolve is Forbidden and a company without membership is Forbidden`() =
        runTest {
            world.home.seed(row("inc-1", company = ServerFixtures.COMPANY_GLOBEX))
            world.budget.incident(world.globex, inc1).ok()
            assertEquals(
                failure(DomainError.Forbidden),
                world.budget.resolve(world.globex, inc1, BudgetResolution.KeepPaused, null),
            )
            assertEquals(failure(DomainError.Forbidden), world.budget.incident(world.elsewhere, inc1))
        }

    @Test
    fun `AD-012 an incident of another company is NotFound under this one`() =
        runTest {
            world.home.seed(row("inc-1", company = ServerFixtures.COMPANY_GLOBEX))
            assertEquals(failure(DomainError.NotFound), world.budget.incident(world.acme, inc1))
        }

    @Test
    fun `AD-012 a factory with no key is Unauthorized and an unknown factory is NotFound`() =
        runTest {
            assertEquals(failure(DomainError.Unauthorized), world.budget.incident(world.signedOut, inc1))
            assertEquals(failure(DomainError.NotFound), world.budget.incident(world.unknownFactory, inc1))
            assertTrue(world.server.requests.isEmpty())
        }
}
