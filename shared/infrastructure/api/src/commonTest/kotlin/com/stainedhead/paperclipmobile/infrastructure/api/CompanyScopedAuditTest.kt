package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private const val APPROVAL = "aaaaaaa1-0000-4000-8000-000000000001"
private const val DECISION = "dddddddd-0000-4000-8000-000000000001"
private val company = ServerFixtures.COMPANY_ACME

private fun seed(work: StubWork) {
    work.attention.items += FeedFixtures.standard()
    work.approvals.approvals += StubApproval(APPROVAL, company)
    work.decisions.decisions += StubDecision(DECISION, company)
    work.budgets.incidents += StubIncident("b0000001-0000-4000-8000-000000000001", company, "policy-1")
}

/** Calls every method of the four gateways once, success and failure paths alike, for [scope]. */
private suspend fun WorkWorld.exerciseEverything(scope: CompanyScope): List<Any?> {
    val results = mutableListOf<Any?>()
    results += attention.feed(scope, FeedQuery(limit = 2))
    results += attention.feed(scope, FeedQuery(cursor = "not-a-cursor"))
    results += attention.dismiss(scope, "attention:approval:ap-1")
    results += attention.snooze(scope, "attention:approval:ap-1", clock.now() + 1.hours)
    results += attention.restore(scope, "attention:approval:ap-1")
    results += attention.setDecideBy(scope, AttentionSourceKind.Approval, "ap-1", DecideBy.Today)
    results += decisions.get(scope, DecisionId(DECISION))
    results += decisions.decide(scope, DecisionId(DECISION), "ship", emptyMap(), "key-1")
    results += decisions.decide(scope, DecisionId(DECISION), "cancel", emptyMap(), "key-2")
    results += decisions.dismiss(scope, DecisionId(DECISION), "no")
    results += approvals.get(scope, ApprovalId(APPROVAL))
    results += approvals.comments(scope, ApprovalId(APPROVAL))
    results += approvals.linkedIssues(scope, ApprovalId(APPROVAL))
    results += approvals.addComment(scope, ApprovalId(APPROVAL), "Hello")
    results += approvals.approve(scope, ApprovalId(APPROVAL), "ok")
    results += approvals.reject(scope, ApprovalId(APPROVAL), "late")
    results += approvals.requestRevision(scope, ApprovalId(APPROVAL), "again")
    results += budget.incident(scope, BudgetIncidentId("b0000001-0000-4000-8000-000000000001"))
    results +=
        budget.resolve(
            scope,
            BudgetIncidentId("b0000001-0000-4000-8000-000000000001"),
            BudgetResolution.RaiseAndResume(Money.usdCents(10_001)),
            null,
        )
    results +=
        budget.resolve(
            scope,
            BudgetIncidentId("b0000001-0000-4000-8000-000000000001"),
            BudgetResolution.KeepPaused,
            "again",
        )
    return results
}

/** AD-012, AD-014: company-scoped calls reach their own instance only, with its key only, and leak no secret. */
class CompanyScopedAuditTest {
    private val world = WorkWorld()

    init {
        seed(world.home)
        seed(world.other)
    }

    @Test
    fun `AD-014 every call for a factory goes to its own host with its own key and no other`() =
        runTest {
            world.exerciseEverything(world.acme)
            world.exerciseEverything(world.otherFactory)
            val byKey = world.server.requests.groupBy({ it.authorization }, { it.host })
            assertEquals(setOf(WorkWorld.HOME), byKey.getValue("Bearer ${TestData.TOKEN}").toSet())
            assertEquals(setOf(WorkWorld.OTHER), byKey.getValue("Bearer ${WorkWorld.OTHER_TOKEN}").toSet())
            assertEquals(setOf("Bearer ${TestData.TOKEN}", "Bearer ${WorkWorld.OTHER_TOKEN}"), byKey.keys)
            assertTrue(world.server.requests.size > 40)
        }

    @Test
    fun `AD-014 every company path carries the scope's company and no request has a foreign one`() =
        runTest {
            world.exerciseEverything(world.acme)
            world.server.requests.forEach { request ->
                val path = request.path.removePrefix("/api/")
                val ok =
                    path.startsWith("companies/company-acme/") || path.startsWith("approvals/") ||
                        path.startsWith("decisions/")
                assertTrue(ok, request.toString())
            }
            assertTrue(world.server.requests.none { "company-globex" in it.path || "company-elsewhere" in it.path })
        }

    @Test
    fun `AD-014 no board key is in any URL body trace line or result of the company gateways`() =
        runTest {
            val results = world.exerciseEverything(world.acme) + world.exerciseEverything(world.otherFactory)
            val keys = listOf(TestData.TOKEN, WorkWorld.OTHER_TOKEN)
            world.server.requests.forEach { request ->
                keys.forEach { key ->
                    assertTrue(key !in request.path + request.query + request.body, request.toString())
                }
            }
            val sink = results.joinToString("\n") { it.toString() } + world.trace.joinToString("\n")
            keys.forEach { key -> assertTrue(key !in sink, "a key leaked into results or trace lines") }
        }

    @Test
    fun `AD-014 a hostile engine cannot put a key or url into an error or trace line`() =
        runTest {
            var round = 0
            val keys = listOf(TestData.TOKEN, WorkWorld.OTHER_TOKEN)
            val engine =
                MockEngine {
                    round += 1
                    if (round % 2 ==
                        1
                    ) {
                        throw IOException("failed GET https://home.example.com/x?token=${keys.joinToString()}")
                    }
                    respond(
                        "{}",
                        HttpStatusCode.InternalServerError,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val trace = mutableListOf<String>()
            val client = PaperclipHttpClient(engine, allowInsecureLocal = false) { trace += it }
            val feed = AttentionGatewayAdapter(client, world.instances, world.secrets)
            val decisions = DecisionGatewayAdapter(client, world.instances, world.secrets)
            val approvals = ApprovalGatewayAdapter(client, world.instances, world.secrets)
            val budget = BudgetGatewayAdapter(client, world.instances, world.secrets)
            val results = mutableListOf<Any?>()
            repeat(2) {
                results += feed.feed(world.acme, FeedQuery())
                results += decisions.get(world.acme, DecisionId(DECISION))
                results += approvals.get(world.acme, ApprovalId(APPROVAL))
                results += budget.incident(world.acme, BudgetIncidentId("b0000001-0000-4000-8000-000000000001"))
            }
            val sink = results.joinToString("\n") { it.toString() } + trace.joinToString("\n")
            keys.forEach { key -> assertTrue(key !in sink, "a key leaked: $sink") }
            assertTrue("https://" !in sink && "token=" !in sink, sink)
        }
}
