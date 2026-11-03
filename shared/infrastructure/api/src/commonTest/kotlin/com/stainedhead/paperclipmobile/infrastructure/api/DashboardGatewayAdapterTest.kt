package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.SpendRow
import com.stainedhead.paperclipmobile.domain.SpendWindowKind
import com.stainedhead.paperclipmobile.domain.SpendWindows
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val company = ServerFixtures.COMPANY_ACME

/** AD-013: factory home through `dashboard` and `costs/window-spend`. */
class DashboardGatewayAdapterTest {
    private val world = WorkWorld()

    private fun gateway(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) = DashboardGatewayAdapter(answeringClient(body, status, contentType), world.instances, world.secrets)

    @Test
    fun `AD-013 the dashboard reads its own company with the stored key and nothing else`() =
        runTest {
            world.dashboard.dashboard(world.acme).value()
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/companies/company-acme/dashboard", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
            assertEquals("", request.query)
        }

    @Test
    fun `AD-013 the tiles map counts and money in cents`() =
        runTest {
            world.home.dashboard.rows += StubDashboardRow(company, monthSpend = "312000.5", monthBudget = "500000")
            val summary = world.dashboard.dashboard(world.acme).value()
            assertEquals(4, summary.agents.active)
            assertEquals(1, summary.agents.running)
            assertEquals(2, summary.agents.paused)
            assertEquals(1, summary.agents.error)
            assertEquals(5, summary.tasks.open)
            assertEquals(1, summary.tasks.inProgress)
            assertEquals(1, summary.tasks.blocked)
            assertEquals(1, summary.tasks.done)
            assertEquals(Money.usdCents(312_001), summary.monthSpend)
            assertEquals(Money.usdCents(500_000), summary.monthBudget)
            assertEquals(2, summary.pendingApprovals)
            assertEquals(1, summary.activeBudgetIncidents)
            assertEquals(62, summary.monthPercentUsed)
        }

    @Test
    fun `AD-013 a month spend printed in exponent form is read without floating point`() =
        runTest {
            world.home.dashboard.rows += StubDashboardRow(company, monthSpend = "5e-7", monthBudget = "0")
            val summary = world.dashboard.dashboard(world.acme).value()
            assertEquals(Money.usdCents(1), summary.monthSpend)
            assertEquals(false, summary.hasBudget)
        }

    @Test
    fun `AD-003 bodies that are not a dashboard are Malformed`() =
        runTest {
            val bad =
                NOT_THE_EXPECTED_SHAPE + "[]" + "{}" +
                    """{"agents":{"active":1},"tasks":{},"costs":{},"pendingApprovals":0,"budgets":{}}""" +
                    """{"agents":{"active":-1,"running":0,"paused":0,"error":0},"tasks":{"open":0,"inProgress":0,""" +
                    """"blocked":0,"done":0},"costs":{"monthSpendCents":1,"monthBudgetCents":1},""" +
                    """"pendingApprovals":0,"budgets":{"activeIncidents":0}}""" +
                    """{"agents":{"active":"4","running":0,"paused":0,"error":0},"tasks":{"open":0,"inProgress":0,""" +
                    """"blocked":0,"done":0},"costs":{"monthSpendCents":1,"monthBudgetCents":1},""" +
                    """"pendingApprovals":0,"budgets":{"activeIncidents":0}}"""
            bad.forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                assertEquals(
                    failed(DomainError.Malformed),
                    gateway(body, contentType = type).dashboard(world.acme),
                    body,
                )
            }
        }

    @Test
    fun `AD-013 window spend reads the exact sum and falls back to the double`() =
        runTest {
            world.home.dashboard.spend +=
                listOf(
                    StubSpendRow("anthropic", "5h", "1200", exact = "1200.0000001"),
                    StubSpendRow("openai", "5h", "0.1", exact = "0.1000000"),
                    StubSpendRow("anthropic", "24h", "5e-7", exact = "0.0000005"),
                    StubSpendRow("anthropic", "7d", "61000"),
                )
            val rows = world.dashboard.windowSpend(world.acme).value()
            assertEquals(
                listOf(
                    SpendRow("5h", 1_201),
                    SpendRow("5h", 1),
                    SpendRow("24h", 1),
                    SpendRow("7d", 61_000),
                ),
                rows,
            )
            val windows = SpendWindows.fromRows(rows)
            assertEquals(SpendWindowKind.Last5Hours, windows.first().window)
            assertEquals(Money.usdCents(1_202), windows.first().cost)
            val request = world.server.requests.single()
            assertEquals("/api/companies/company-acme/costs/window-spend", request.path)
        }

    @Test
    fun `AD-013 window spend without the exact text uses the printed number`() =
        runTest {
            val body =
                """[{"provider":"a","window":"24h","costCents":5e-7},{"provider":"b","window":"7d","costCents":12.5}]"""
            assertEquals(listOf(SpendRow("24h", 1), SpendRow("7d", 13)), gateway(body).windowSpend(world.acme).value())
        }

    @Test
    fun `AD-013 an empty window list is empty and an unknown window is kept for the domain to ignore`() =
        runTest {
            assertEquals(emptyList(), gateway("[]").windowSpend(world.acme).value())
            val rows = gateway("""[{"window":"30d","costCents":1}]""").windowSpend(world.acme).value()
            assertEquals(listOf(SpendRow("30d", 1)), rows)
            assertTrue(SpendWindows.fromRows(rows).all { it.cost == Money.usdCents(0) })
        }

    @Test
    fun `AD-003 window spend that is not a list of rows is Malformed`() =
        runTest {
            val bad =
                NOT_THE_EXPECTED_SHAPE + "{}" + """[{"window":"5h"}]""" + """[{"costCents":1}]""" +
                    """[{"window":"5h","costCents":"abc"}]""" + """[{"window":"5h","costCents":-1}]""" + "[1]"
            bad.forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                assertEquals(
                    failed(DomainError.Malformed),
                    gateway(body, contentType = type).windowSpend(world.acme),
                    body,
                )
            }
        }

    @Test
    fun `AD-013 a viewer reads both and a company without membership is Forbidden`() =
        runTest {
            world.dashboard.dashboard(world.globex).value()
            world.dashboard.windowSpend(world.globex).value()
            assertEquals(failed(DomainError.Forbidden), world.dashboard.dashboard(world.elsewhere))
            assertEquals(failed(DomainError.Forbidden), world.dashboard.windowSpend(world.elsewhere))
        }

    @Test
    fun `AD-013 a missing key and an unknown factory and a server error map to their errors`() =
        runTest {
            assertEquals(failed(DomainError.Unauthorized), world.dashboard.dashboard(world.signedOut))
            assertEquals(failed(DomainError.NotFound), world.dashboard.windowSpend(world.unknownFactory))
            assertEquals(
                failed(DomainError.ServerError),
                gateway(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError).dashboard(world.acme),
            )
        }
}
