package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Decision
import com.stainedhead.paperclipmobile.domain.DecisionEffect
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DecisionInput
import com.stainedhead.paperclipmobile.domain.DecisionStatus
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.EffectResult
import com.stainedhead.paperclipmobile.domain.EffectStaleness
import com.stainedhead.paperclipmobile.domain.EffectStatus
import com.stainedhead.paperclipmobile.domain.ExecutionStatus
import com.stainedhead.paperclipmobile.domain.OptionStyle
import com.stainedhead.paperclipmobile.domain.port.DecisionGateway
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val D1 = "dddddddd-0000-4000-8000-000000000001"
private const val D2 = "dddddddd-0000-4000-8000-000000000002"
private const val COMMENT_TEXT = "Go ahead with the migration."
private val optionsPart = Regex("\"options\":\\[.*?\\],\"inputs\"")
private val d1 = DecisionId(D1)
private val acme = ServerFixtures.COMPANY_ACME

private fun fail(error: DomainError) = DomainResult.Failure(error)

private fun <T> DomainResult<T>.ok(): T = (this as DomainResult.Success).value

private fun StubWork.seed(vararg rows: StubDecision) {
    decisions.decisions += rows
}

private fun gatewayAnswering(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    contentType: String = "application/json",
): DecisionGateway {
    val world = WorkWorld()
    val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }
    val client = PaperclipHttpClient(engine, allowInsecureLocal = false)
    return DecisionGatewayAdapter(client, world.instances, world.secrets)
}

private fun bodyOf(row: StubDecision) = DecisionFixtures.decision(row)

/** AD-012: decisions, one test per endpoint mapping and per error shape. */
class DecisionGatewayAdapterTest {
    private val world = WorkWorld()

    @Test
    fun `AD-012 get reads the decision by id with the stored key and no query`() =
        runTest {
            world.home.seed(StubDecision(D1, acme))
            world.decisions.get(world.acme, d1).ok()
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/decisions/$D1", request.path)
            assertEquals("", request.query)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
        }

    @Test
    fun `AD-012 an open decision maps title options effects inputs and times`() =
        runTest {
            world.home.seed(StubDecision(D1, acme, inputs = DecisionFixtures.inputs("note")))
            val decision: Decision = world.decisions.get(world.acme, d1).ok()
            assertEquals("Ship the billing migration?", decision.title)
            assertEquals(DecisionStatus.Open, decision.status)
            assertEquals(Instant.parse("2026-11-01T09:00:00.000Z"), decision.createdAt)
            assertEquals(Instant.parse("2026-11-03T12:00:00.000Z"), decision.expiresAt)
            assertEquals("agent-eng-1", decision.originAgentId)
            assertEquals("issue-1182", decision.originIssueId)
            assertNull(decision.originAgentName)
            assertEquals(DecideBy.None, decision.decideBy)
            assertNull(decision.outcome)
            assertEquals(listOf("ship", "hold", "cancel"), decision.options.map { it.id })
            val styles = decision.options.map { it.style }
            assertEquals(listOf(OptionStyle.Primary, OptionStyle.Default, OptionStyle.Destructive), styles)
            assertEquals("About Hold", decision.options[1].description)
            assertEquals(
                listOf(DecisionEffect.CommentOnIssue("issue-1182", EffectStaleness.Lenient, COMMENT_TEXT)),
                decision.options[0].effects,
            )
            val cancel = DecisionEffect.CancelIssueTree("issue-1182", "Stopped by the board.")
            assertEquals(listOf(cancel), decision.options[2].effects)
            assertTrue(decision.options[2].isDestructive)
            assertEquals(listOf(DecisionInput("note", "Note", "Say why", false, 500)), decision.inputs)
        }

    @Test
    fun `AD-012 every effect type maps and an unknown one stays visible`() =
        runTest {
            val effects =
                """
                [{"type":"create_issue","targetIssueId":"i1","staleness":"strict","draft":{"title":"Follow up"}},
                 {"type":"update_issue_status","targetIssueId":"i2","staleness":"lenient","status":"done"},
                 {"type":"assign_issue","targetIssueId":"i3","staleness":"strict","assigneeAgentId":"agent-1"},
                 {"type":"resolve_blocker","targetIssueId":"i4","staleness":"lenient",
                  "removeBlockedByIssueIds":["a","b"]},
                 {"type":"cancel_issue_tree"},
                 {"type":"teleport","targetIssueId":"i6"},
                 {"nothing":true}]
                """.trimIndent()
            val body =
                bodyOf(StubDecision(D1, acme)).replace(
                    optionsPart,
                    "\"options\":[{\"id\":\"x\",\"label\":\"X\",\"effects\":$effects}],\"inputs\"",
                )
            val mapped =
                gatewayAnswering(body)
                    .get(world.acme, d1)
                    .ok()
                    .options
                    .single()
                    .effects
            assertEquals(
                listOf(
                    DecisionEffect.CreateIssue("i1", EffectStaleness.Strict, "Follow up"),
                    DecisionEffect.UpdateIssueStatus("i2", EffectStaleness.Lenient, "done"),
                    DecisionEffect.AssignIssue("i3", EffectStaleness.Strict, null),
                    DecisionEffect.ResolveBlocker("i4", EffectStaleness.Lenient, 2),
                    DecisionEffect.CancelIssueTree("", ""),
                    DecisionEffect.Unsupported("teleport", "i6"),
                    DecisionEffect.Unsupported("", ""),
                ),
                mapped,
            )
        }

    @Test
    fun `AD-012 a decided decision carries the choice the inputs and the per effect results`() =
        runTest {
            val row = StubDecision(D1, acme, inputs = DecisionFixtures.inputs("note"))
            row.status = "decided"
            row.executionStatus = "partial"
            row.chosenOptionId = "ship"
            row.inputValues = mapOf("note" to "go")
            row.executions += DecisionFixtures.execution(0, "comment_on_issue", "executed")
            row.executions += DecisionFixtures.execution(1, "update_issue_status", "skipped", "target_changed")
            val decision = gatewayAnswering(bodyOf(row)).get(world.acme, d1).ok()
            assertEquals(DecisionStatus.Decided, decision.status)
            assertEquals("ship", decision.chosenOptionId)
            assertEquals(mapOf("note" to "go"), decision.inputValues)
            assertFalse(decision.dismissed)
            val outcome = decision.outcome
            assertEquals(ExecutionStatus.Partial, outcome?.executionStatus)
            assertEquals(
                listOf(
                    EffectResult(0, "comment_on_issue", EffectStatus.Executed, null),
                    EffectResult(1, "update_issue_status", EffectStatus.Skipped, "target_changed"),
                ),
                outcome?.results,
            )
            assertTrue(outcome?.isTerminal == true)
        }

    @Test
    fun `AD-003 unknown statuses never crash and read as Unknown`() =
        runTest {
            val row = StubDecision(D1, acme)
            row.status = "frozen"
            row.executionStatus = "teleporting"
            row.executions += DecisionFixtures.execution(0, "comment_on_issue", "levitating")
            val decision = gatewayAnswering(bodyOf(row)).get(world.acme, d1).ok()
            assertEquals(DecisionStatus.Unknown, decision.status)
            assertEquals(ExecutionStatus.Unknown, decision.outcome?.executionStatus)
            assertEquals(
                EffectStatus.Unknown,
                decision.outcome
                    ?.results
                    ?.single()
                    ?.status,
            )
        }

    @Test
    fun `AD-003 a body without id title status options or a creation time is Malformed`() =
        runTest {
            val good = bodyOf(StubDecision(D1, acme))
            listOf(
                "{}",
                "[]",
                "null",
                good.replace("\"title\":\"Ship the billing migration?\",", ""),
                good.replace("\"id\":\"$D1\",", ""),
                good.replace("\"createdAt\":\"2026-11-01T09:00:00.000Z\"", "\"createdAt\":\"soon\""),
                good.replace(optionsPart, "\"inputs\""),
                good.replace(optionsPart, "\"options\":[{\"label\":\"no id\"}],\"inputs\""),
            ).forEach { assertEquals(fail(DomainError.Malformed), gatewayAnswering(it).get(world.acme, d1), it) }
            val html = gatewayAnswering("<html></html>", contentType = "text/html")
            assertEquals(fail(DomainError.Malformed), html.get(world.acme, d1))
            assertEquals(fail(DomainError.Malformed), gatewayAnswering("").get(world.acme, d1))
        }

    @Test
    fun `AD-012 a decision id that cannot be a uuid is NotFound without a request`() =
        runTest {
            assertEquals(fail(DomainError.NotFound), world.decisions.get(world.acme, DecisionId("no-such-decision")))
            assertEquals(fail(DomainError.NotFound), world.decisions.dismiss(world.acme, DecisionId("../x"), null))
            assertTrue(world.server.requests.isEmpty())
        }

    @Test
    fun `AD-012 a decision of another company or one the key cannot see is NotFound`() =
        runTest {
            world.home.seed(StubDecision(D1, ServerFixtures.COMPANY_GLOBEX))
            assertEquals(fail(DomainError.NotFound), world.decisions.get(world.acme, d1))
            assertEquals(fail(DomainError.NotFound), world.decisions.decide(world.acme, d1, "ship", emptyMap(), "k"))
            assertEquals(listOf("GET", "GET"), world.server.requests.map { it.method })
            world.home.seed(StubDecision(D2, "company-elsewhere"))
            assertEquals(fail(DomainError.NotFound), world.decisions.get(world.elsewhere, DecisionId(D2)))
        }

    @Test
    fun `AD-012 decide reads the decision first then posts option inputs and key once`() =
        runTest {
            world.home.seed(StubDecision(D1, acme, inputs = DecisionFixtures.inputs("note")))
            val decided = world.decisions.decide(world.acme, d1, "ship", mapOf("note" to "go"), "key-1").ok()
            assertEquals(DecisionStatus.Decided, decided.status)
            assertEquals("ship", decided.chosenOptionId)
            assertEquals(listOf("GET", "POST"), world.server.requests.map { it.method })
            val post = world.server.requests.last()
            assertEquals("/api/decisions/$D1/decide", post.path)
            assertEquals("""{"optionId":"ship","inputValues":{"note":"go"},"idempotencyKey":"key-1"}""", post.body)
        }

    @Test
    fun `AD-012 a decision that finishes later is read again until its effects are done`() =
        runTest {
            world.home.seed(StubDecision(D1, acme))
            world.home.decisions.runningReads = 2
            val decided = world.decisions.decide(world.acme, d1, "ship", emptyMap(), "key-1").ok()
            assertTrue(decided.isExecutionPending)
            assertTrue(
                world.decisions
                    .get(world.acme, d1)
                    .ok()
                    .isExecutionPending,
            )
            assertTrue(
                world.decisions
                    .get(world.acme, d1)
                    .ok()
                    .isExecutionPending,
            )
            val done = world.decisions.get(world.acme, d1).ok()
            assertFalse(done.isExecutionPending)
            assertEquals(ExecutionStatus.Succeeded, done.outcome?.executionStatus)
            assertEquals(
                EffectStatus.Executed,
                done.outcome
                    ?.results
                    ?.single()
                    ?.status,
            )
        }

    @Test
    fun `AD-012 dismiss posts the reason or an empty object and reports the dismissal`() =
        runTest {
            world.home.seed(StubDecision(D1, acme))
            val dismissed = world.decisions.dismiss(world.acme, d1, "not now").ok()
            assertTrue(dismissed.dismissed)
            assertEquals("hold", dismissed.chosenOptionId)
            val post = world.server.requests.last()
            assertEquals("/api/decisions/$D1/dismiss", post.path)
            assertEquals("""{"reason":"not now"}""", post.body)
            world.decisions.dismiss(world.acme, d1, null).ok()
            assertEquals(
                "{}",
                world.server.requests
                    .last()
                    .body,
            )
        }

    @Test
    fun `AD-012 the error shapes of decide arrive as typed errors`() =
        runTest {
            world.home.seed(StubDecision(D1, acme, inputs = listOf(DecisionFixtures.input("note", required = true))))
            assertEquals(
                fail(DomainError.Rejected("Unknown optionId")),
                world.decisions.decide(world.acme, d1, "nope", emptyMap(), "k"),
            )
            assertEquals(
                fail(DomainError.Rejected("Input note is required")),
                world.decisions.decide(world.acme, d1, "ship", emptyMap(), "k"),
            )
            world.decisions.decide(world.acme, d1, "ship", mapOf("note" to "x"), "k").ok()
            assertEquals(fail(DomainError.Conflict), world.decisions.decide(world.acme, d1, "cancel", emptyMap(), "k2"))
        }

    @Test
    fun `AD-012 an expired decision is a Conflict`() =
        runTest {
            world.home.seed(StubDecision(D1, acme, expiresAt = "2026-11-01T11:00:00.000Z"))
            assertEquals(fail(DomainError.Conflict), world.decisions.decide(world.acme, d1, "ship", emptyMap(), "k"))
            assertEquals(
                DecisionStatus.Expired,
                world.decisions
                    .get(world.acme, d1)
                    .ok()
                    .status,
            )
        }

    @Test
    fun `AD-002 a failing decide is sent once and never retried`() =
        runTest {
            var posts = 0
            val engine =
                MockEngine { request ->
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    if (request.method.value == "GET") {
                        respond(bodyOf(StubDecision(D1, acme)), HttpStatusCode.OK, json)
                    } else {
                        posts += 1
                        respond(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError, json)
                    }
                }
            val gateway = DecisionGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            assertEquals(fail(DomainError.ServerError), gateway.decide(world.acme, d1, "ship", emptyMap(), "k"))
            assertEquals(1, posts)
        }

    @Test
    fun `AD-012 a viewer may read but deciding and dismissing are Forbidden`() =
        runTest {
            world.home.seed(StubDecision(D1, ServerFixtures.COMPANY_GLOBEX))
            world.decisions.get(world.globex, d1).ok()
            assertEquals(fail(DomainError.Forbidden), world.decisions.decide(world.globex, d1, "ship", emptyMap(), "k"))
            assertEquals(fail(DomainError.Forbidden), world.decisions.dismiss(world.globex, d1, null))
        }

    @Test
    fun `AD-012 a factory with no key is Unauthorized and an unknown factory is NotFound`() =
        runTest {
            assertEquals(fail(DomainError.Unauthorized), world.decisions.get(world.signedOut, d1))
            assertEquals(fail(DomainError.NotFound), world.decisions.get(world.unknownFactory, d1))
            assertTrue(world.server.requests.isEmpty())
        }
}
