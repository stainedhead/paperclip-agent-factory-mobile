package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InvokeOutcome
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.PauseReason
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val ENG = "e0000001-0000-4000-8000-000000000001"
private const val CTO = "e0000002-0000-4000-8000-000000000002"
private const val GLOBEX_AGENT = "e0000003-0000-4000-8000-000000000003"
private val acme = ServerFixtures.COMPANY_ACME
private val eng = AgentId(ENG)

private fun StubWork.seed() {
    agents.agents +=
        listOf(
            StubAgent(ENG, acme),
            StubAgent(CTO, acme, "CTO", "cto", "idle"),
            StubAgent(GLOBEX_AGENT, ServerFixtures.COMPANY_GLOBEX, "Globex agent"),
        )
}

/** AD-013: agents through `agents`, `agents/{id}`, `pause`, `resume`, `heartbeat/invoke` and `budgets`. */
class AgentGatewayAdapterTest {
    private val world = WorkWorld().also { it.home.seed() }

    private fun gateway(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) = AgentGatewayAdapter(answeringClient(body, status, contentType), world.instances, world.secrets)

    @Test
    fun `AD-013 list reads the company agents with the stored key and no query`() =
        runTest {
            world.agents.list(world.acme).value()
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/companies/company-acme/agents", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
            assertEquals("", request.query)
        }

    @Test
    fun `AD-013 list maps status lifecycle reporting line budget and month spend`() =
        runTest {
            world.home.agents.agents
                .first { it.id == CTO }
                .apply {
                    title = "Chief Technology Officer"
                    lifecycle = "pausing"
                    reportsTo = ENG
                    budget = 250_000
                    spent = "1234.0000001"
                    status = "paused"
                    pauseReason = "budget"
                }
            val cto =
                world.agents
                    .list(world.acme)
                    .value()
                    .first { it.id == AgentId(CTO) }
            assertEquals("CTO", cto.name)
            assertEquals("cto", cto.role)
            assertEquals("Chief Technology Officer", cto.title)
            assertEquals(AgentStatus.Paused, cto.status)
            assertEquals("pausing", cto.lifecycleState)
            assertEquals("pausing", cto.visibleLifecycle)
            assertEquals(AgentId(ENG), cto.reportsToId)
            assertEquals(PauseReason.Budget, cto.pauseReason)
            assertEquals(Money.usdCents(250_000), cto.monthlyBudget)
            assertEquals(Money.usdCents(1_235), cto.monthSpend)
            assertEquals(Instant.parse("2026-11-01T11:00:00.000Z"), cto.lastHeartbeatAt)
            assertTrue(cto.isBudgetPaused)
        }

    @Test
    fun `AD-013 a ready agent has no visible lifecycle and a terminated agent is not listed`() =
        runTest {
            world.home.agents.agents
                .first { it.id == CTO }
                .status = "terminated"
            val listed = world.agents.list(world.acme).value()
            assertEquals(listOf(AgentId(ENG)), listed.map { it.id })
            assertEquals(null, listed.single().visibleLifecycle)
        }

    @Test
    fun `AD-013 spend printed in exponent form and unknown values are read without crashing`() =
        runTest {
            val body =
                AgentRowFixtures
                    .agent(StubAgent(ENG, acme).apply { spent = "5e-7" })
                    .replace("\"status\":\"running\"", "\"status\":\"hovering\"")
                    .replace("\"pauseReason\":null", "\"pauseReason\":\"quota\"")
            val agent = gateway("[$body]").list(world.acme).value().single()
            assertEquals(Money.usdCents(1), agent.monthSpend)
            assertEquals(AgentStatus.Unknown, agent.status)
            assertEquals(PauseReason.Other, agent.pauseReason)
        }

    @Test
    fun `AD-003 bodies that are not an agent list are Malformed`() =
        runTest {
            val noId = """[{"name":"x","role":"r","status":"idle","budgetMonthlyCents":0,"spentMonthlyCents":0}]"""
            val badSpend = AgentRowFixtures.agent(StubAgent(ENG, acme).apply { spent = "\"lots\"" })
            (NOT_THE_EXPECTED_SHAPE + "{}" + noId + "[$badSpend]" + "[1]").forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                assertEquals(failed(DomainError.Malformed), gateway(body, contentType = type).list(world.acme), body)
            }
            assertEquals(emptyList(), gateway("[]").list(world.acme).value())
        }

    @Test
    fun `AD-013 get reads one agent and equals the listed one`() =
        runTest {
            val listed =
                world.agents
                    .list(world.acme)
                    .value()
                    .first { it.id == eng }
            assertEquals(listed, world.agents.get(world.acme, eng).value())
            assertEquals(
                "/api/agents/$ENG",
                world.server.requests
                    .last()
                    .path,
            )
        }

    @Test
    fun `AD-014 an agent of another company is NotFound under this scope and an id that is not a uuid sends nothing`() =
        runTest {
            assertEquals(failed(DomainError.NotFound), world.agents.get(world.acme, AgentId(GLOBEX_AGENT)))
            assertEquals(failed(DomainError.NotFound), world.agents.get(world.acme, AgentId("no-such-agent")))
            assertEquals(failed(DomainError.NotFound), world.agents.pause(world.acme, AgentId("no-such-agent")))
            assertEquals(1, world.server.requests.size)
        }

    @Test
    fun `AD-013 pause reads first then posts once and returns a manual pause`() =
        runTest {
            val paused = world.agents.pause(world.acme, eng).value()
            assertEquals(AgentStatus.Paused, paused.status)
            assertEquals(PauseReason.Manual, paused.pauseReason)
            val sent = world.server.requests
            assertEquals(listOf("GET", "POST"), sent.map { it.method })
            assertEquals("/api/agents/$ENG/pause", sent[1].path)
            assertEquals("", sent[1].body)
        }

    @Test
    fun `AD-013 pausing an agent cancels its active runs on the server and resume goes idle`() =
        runTest {
            val run = world.home.runs.start(acme, ENG, "Engineer 1")
            world.agents.pause(world.acme, eng).value()
            assertEquals("cancelled", run.status)
            val resumed = world.agents.resume(world.acme, eng).value()
            assertEquals(AgentStatus.Idle, resumed.status)
            assertEquals(null, resumed.pauseReason)
            assertEquals(
                "/api/agents/$ENG/resume",
                world.server.requests
                    .last()
                    .path,
            )
        }

    @Test
    fun `AD-013 a broken reporting chain makes resume and run now a Conflict`() =
        runTest {
            world.home.agents.agents
                .first { it.id == ENG }
                .invalidOrgChain = true
            assertEquals(failed(DomainError.Conflict), world.agents.resume(world.acme, eng))
            assertEquals(failed(DomainError.Conflict), world.agents.invokeHeartbeat(world.acme, eng))
        }

    @Test
    fun `AD-013 run now answers Started with the run and the agent name`() =
        runTest {
            val outcome = world.agents.invokeHeartbeat(world.acme, eng).value()
            val started = assertIs<InvokeOutcome.Started>(outcome)
            assertEquals(eng, started.run.agentId)
            assertEquals("Engineer 1", started.run.agentName)
            assertEquals(RunStatus.Queued, started.run.status)
            val sent = world.server.requests
            assertEquals(listOf("GET", "POST"), sent.map { it.method })
            assertEquals("/api/agents/$ENG/heartbeat/invoke", sent[1].path)
        }

    @Test
    fun `AD-013 run now of an agent that cannot run is Skipped and an unreadable reply is Malformed`() =
        runTest {
            world.home.agents.agents
                .first { it.id == CTO }
                .status = "paused"
            assertEquals(InvokeOutcome.Skipped, world.agents.invokeHeartbeat(world.acme, AgentId(CTO)).value())
            val engine = scripted(post = "{}", status = HttpStatusCode.Accepted)
            assertEquals(failed(DomainError.Malformed), engine.invokeHeartbeat(world.acme, eng))
            val other = scripted(post = """{"status":"queued"}""", status = HttpStatusCode.Accepted)
            assertEquals(failed(DomainError.Malformed), other.invokeHeartbeat(world.acme, eng))
        }

    @Test
    fun `AD-013 set budget patches whole cents once and returns the agent`() =
        runTest {
            val updated = world.agents.setBudget(world.acme, eng, Money.usdCents(75_000)).value()
            assertEquals(Money.usdCents(75_000), updated.monthlyBudget)
            val sent = world.server.requests
            assertEquals(listOf("GET", "PATCH"), sent.map { it.method })
            assertEquals("/api/agents/$ENG/budgets", sent[1].path)
            assertEquals("""{"budgetMonthlyCents":75000}""", sent[1].body)
        }

    @Test
    fun `AD-013 a budget the server cannot take is refused here and nothing is sent`() =
        runTest {
            val tooBig = Money(2_147_483_648L, "USD")
            val euros = Money(500, "EUR")
            assertTrue(
                (world.agents.setBudget(world.acme, eng, tooBig) as DomainResult.Failure).error is DomainError.Rejected,
            )
            assertTrue(
                (world.agents.setBudget(world.acme, eng, euros) as DomainResult.Failure).error is DomainError.Rejected,
            )
            assertTrue(world.server.requests.isEmpty())
        }

    @Test
    fun `AD-013 a viewer may read but every control is Forbidden`() =
        runTest {
            world.home.agents.agents +=
                StubAgent("e0000009-0000-4000-8000-000000000009", ServerFixtures.COMPANY_GLOBEX, "G2")
            val id = AgentId("e0000009-0000-4000-8000-000000000009")
            world.agents.list(world.globex).value()
            world.agents.get(world.globex, id).value()
            assertEquals(failed(DomainError.Forbidden), world.agents.pause(world.globex, id))
            assertEquals(failed(DomainError.Forbidden), world.agents.resume(world.globex, id))
            assertEquals(failed(DomainError.Forbidden), world.agents.invokeHeartbeat(world.globex, id))
            assertEquals(failed(DomainError.Forbidden), world.agents.setBudget(world.globex, id, Money.usdCents(1)))
            assertEquals(failed(DomainError.Forbidden), world.agents.list(world.elsewhere))
        }

    /** Reads answer with the real stub but the mutation reply is [post], as a bug or a newer server could send. */
    private fun scripted(
        post: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        counter: MutableList<String> = mutableListOf(),
    ): AgentGatewayAdapter {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val engine =
            MockEngine { request ->
                counter += request.method.value
                if (request.method.value == "GET") {
                    respond(AgentRowFixtures.agent(StubAgent(ENG, acme)), HttpStatusCode.OK, json)
                } else {
                    respond(post, status, json)
                }
            }
        return AgentGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
    }

    @Test
    fun `AD-014 a mutation reply that names another company is not trusted`() =
        runTest {
            val foreign = AgentRowFixtures.agent(StubAgent(ENG, ServerFixtures.COMPANY_GLOBEX))
            assertEquals(failed(DomainError.NotFound), scripted(foreign).pause(world.acme, eng))
        }

    @Test
    fun `AD-002 a failing mutation is sent once and never retried`() =
        runTest {
            val methods = mutableListOf<String>()
            val failing = scripted(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError, methods)
            assertEquals(failed(DomainError.ServerError), failing.pause(world.acme, eng))
            assertEquals(listOf("GET", "POST"), methods)
        }
}
