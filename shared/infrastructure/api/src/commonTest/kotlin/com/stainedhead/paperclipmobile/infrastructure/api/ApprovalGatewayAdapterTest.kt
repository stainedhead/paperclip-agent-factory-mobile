package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.Approval
import com.stainedhead.paperclipmobile.domain.ApprovalComment
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.ApprovalPayload
import com.stainedhead.paperclipmobile.domain.ApprovalStatus
import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.LinkedIssue
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
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

private const val AP1 = "aaaaaaa1-0000-4000-8000-000000000001"
private const val AP2 = "aaaaaaa2-0000-4000-8000-000000000002"
private val ap1 = ApprovalId(AP1)
private val acme = ServerFixtures.COMPANY_ACME

private fun fail(error: DomainError) = DomainResult.Failure(error)

private fun <T> DomainResult<T>.ok(): T = (this as DomainResult.Success).value

private fun StubWork.seed(vararg rows: StubApproval) {
    approvals.approvals += rows
}

private fun gatewayAnswering(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    contentType: String = "application/json",
): ApprovalGateway {
    val world = WorkWorld()
    val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }
    return ApprovalGatewayAdapter(
        PaperclipHttpClient(engine, allowInsecureLocal = false),
        world.instances,
        world.secrets,
    )
}

private fun bodyWith(
    type: String,
    payload: String,
    status: String = "pending",
) = ApprovalFixtures.approval(StubApproval(AP1, acme, type, status, payload))

/** AD-012: approvals, one test per endpoint mapping and per error shape. */
class ApprovalGatewayAdapterTest {
    private val world = WorkWorld()

    @Test
    fun `AD-012 get reads the approval by id with the stored key and no query`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme))
            world.approvals.get(world.acme, ap1).ok()
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/approvals/$AP1", request.path)
            assertEquals("", request.query)
            assertEquals("Bearer ${com.stainedhead.paperclipmobile.testing.TestData.TOKEN}", request.authorization)
        }

    @Test
    fun `AD-012 a hire approval maps name role budget skills and requester`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme))
            val approval: Approval = world.approvals.get(world.acme, ap1).ok()
            assertEquals(ApprovalType.HireAgent, approval.type)
            assertEquals(ApprovalStatus.Pending, approval.status)
            assertEquals(Instant.parse("2026-11-01T08:00:00.000Z"), approval.createdAt)
            assertEquals("agent-ceo", approval.requestedByAgentId)
            val payload = assertIs<ApprovalPayload.HireAgent>(approval.payload)
            assertEquals("Designer", payload.name)
            assertEquals("designer", payload.role)
            assertEquals("Product Designer", payload.title)
            assertEquals("claude_local", payload.adapterType)
            assertEquals("agent-cto", payload.reportsToAgentId)
            assertEquals(Money.usdCents(20_000), payload.monthlyBudget)
            assertEquals(listOf("figma", "research"), payload.skills)
            assertEquals("Figma", payload.capabilities)
        }

    @Test
    fun `AD-012 the other three payload types map their fields`() =
        runTest {
            val ceo =
                gatewayAnswering(
                    bodyWith("approve_ceo_strategy", ApprovalFixtures.CEO_PAYLOAD),
                ).get(world.acme, ap1).ok()
            assertEquals(ApprovalPayload.CeoStrategy("Q4 strategy", "Focus on billing."), ceo.payload)
            val budget =
                gatewayAnswering(
                    bodyWith("budget_override_required", ApprovalFixtures.budgetPayload),
                ).get(world.acme, ap1).ok()
            assertEquals(
                ApprovalPayload.BudgetOverride(
                    "Engineer 2",
                    "calendar_month_utc",
                    Money.usdCents(10_000),
                    Money.usdCents(10_001),
                    "Raise the budget or keep the scope paused.",
                ),
                budget.payload,
            )
            val board =
                gatewayAnswering(
                    bodyWith("request_board_approval", ApprovalFixtures.boardPayload),
                ).get(world.acme, ap1).ok()
            assertEquals(
                ApprovalPayload.BoardRequest(
                    "Switch the CI provider",
                    "Cheaper runners.",
                    "Approve",
                    "Engineer 1 migrates.",
                    listOf("- Downtime tonight", "Learning curve"),
                    "Go.",
                ),
                board.payload,
            )
        }

    @Test
    fun `AD-003 an unknown type or status never crashes and a redacted or odd payload only loses fields`() =
        runTest {
            val future =
                gatewayAnswering(
                    bodyWith("grant_wish", """{"wish":"more"}""", "on_hold"),
                ).get(world.acme, ap1).ok()
            assertEquals(ApprovalType.Unsupported("grant_wish"), future.type)
            assertEquals(ApprovalStatus.Unknown, future.status)
            assertEquals(ApprovalPayload.Unsupported("grant_wish"), future.payload)
            val odd = """{"name":7,"role":["x"],"desiredSkills":"figma","budgetMonthlyCents":"lots","reportsTo":null}"""
            val hire = gatewayAnswering(bodyWith("hire_agent", odd)).get(world.acme, ap1).ok()
            assertEquals(
                ApprovalPayload.HireAgent(null, null, null, null, null, null, emptyList(), null),
                hire.payload,
            )
            val empty = gatewayAnswering(bodyWith("hire_agent", "{}")).get(world.acme, ap1).ok()
            assertIs<ApprovalPayload.HireAgent>(empty.payload)
            val notAnObject =
                gatewayAnswering(
                    bodyWith("request_board_approval", """["x"]"""),
                ).get(world.acme, ap1).ok()
            assertIs<ApprovalPayload.BoardRequest>(notAnObject.payload)
        }

    @Test
    fun `AD-003 a body without id type or a readable time is Malformed`() =
        runTest {
            listOf(
                "{}",
                "[]",
                "null",
                """{"id":"$AP1","status":"pending","payload":{},"createdAt":"2026-11-01T08:00:00Z"}""",
                """{"id":"$AP1","type":"hire_agent","status":"pending","payload":{},"createdAt":"yesterday"}""",
                """{"id":"","type":"hire_agent","status":"pending","payload":{},"createdAt":"2026-11-01T08:00:00Z"}""",
            ).forEach { assertEquals(fail(DomainError.Malformed), gatewayAnswering(it).get(world.acme, ap1), it) }
            assertEquals(
                fail(DomainError.Malformed),
                gatewayAnswering("", contentType = "text/plain").get(world.acme, ap1),
            )
            assertEquals(
                fail(DomainError.Malformed),
                gatewayAnswering("<html></html>", contentType = "text/html").get(world.acme, ap1),
            )
        }

    @Test
    fun `AD-012 an approval id that cannot be a uuid is NotFound without a request`() =
        runTest {
            assertEquals(fail(DomainError.NotFound), world.approvals.get(world.acme, ApprovalId("no-such-approval")))
            assertEquals(fail(DomainError.NotFound), world.approvals.approve(world.acme, ApprovalId("../x"), null))
            assertTrue(world.server.requests.isEmpty())
        }

    @Test
    fun `AD-012 an approval of another company is NotFound under this scope for reads and writes`() =
        runTest {
            world.home.seed(StubApproval(AP1, ServerFixtures.COMPANY_GLOBEX))
            assertEquals(fail(DomainError.NotFound), world.approvals.get(world.acme, ap1))
            assertEquals(fail(DomainError.NotFound), world.approvals.approve(world.acme, ap1, null))
            assertEquals(listOf("GET", "GET"), world.server.requests.map { it.method })
        }

    @Test
    fun `AD-012 an approval of a company the key cannot see is NotFound`() =
        runTest {
            world.home.seed(StubApproval(AP1, "company-elsewhere"))
            assertEquals(fail(DomainError.NotFound), world.approvals.get(world.elsewhere, ap1))
        }

    @Test
    fun `AD-012 linked issues map the id identifier title and status`() =
        runTest {
            world.home.seed(
                StubApproval(
                    AP1,
                    acme,
                    issues =
                        mutableListOf(
                            ApprovalFixtures.issue("i-1", "PAP-1182", "Ship it"),
                            ApprovalFixtures.issue("i-2", null, "No identifier"),
                        ),
                ),
            )
            val issues = world.approvals.linkedIssues(world.acme, ap1).ok()
            assertEquals(
                listOf(
                    LinkedIssue("i-1", "PAP-1182", "Ship it", "in_review"),
                    LinkedIssue("i-2", null, "No identifier", "in_review"),
                ),
                issues,
            )
            assertEquals(
                "/api/approvals/$AP1/issues",
                world.server.requests
                    .last()
                    .path,
            )
        }

    @Test
    fun `AD-003 an issue list with an untitled issue or a non-list body is Malformed`() =
        runTest {
            assertEquals(
                fail(DomainError.Malformed),
                gatewayAnswering("""[{"id":"i-1"}]""").linkedIssues(world.acme, ap1),
            )
            assertEquals(fail(DomainError.Malformed), gatewayAnswering("{}").linkedIssues(world.acme, ap1))
        }

    @Test
    fun `AD-012 comments map the author time and body in server order`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme))
            world.home.approvals.comments += StubComment("c-1", AP1, acme, "First")
            world.home.approvals.comments += StubComment("c-2", AP1, acme, "Second", "2026-11-01T11:00:00.000Z")
            val comments = world.approvals.comments(world.acme, ap1).ok()
            assertEquals(listOf("First", "Second"), comments.map { it.body })
            assertEquals(Instant.parse("2026-11-01T11:00:00.000Z"), comments[1].createdAt)
            assertEquals("user-1", comments[0].authorUserId)
            assertEquals(
                "/api/approvals/$AP1/comments",
                world.server.requests
                    .last()
                    .path,
            )
        }

    @Test
    fun `AD-003 a comment without body id or time is Malformed`() =
        runTest {
            listOf(
                """[{"id":"c","createdAt":"2026-11-01T10:00:00Z"}]""",
                """[{"id":"c","body":"x","createdAt":"never"}]""",
                "{}",
            ).forEach {
                assertEquals(
                    fail(DomainError.Malformed),
                    gatewayAnswering(it).comments(world.acme, ap1),
                    it,
                )
            }
        }

    @Test
    fun `AD-012 approve reads the approval first then posts the note once`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme))
            val approved = world.approvals.approve(world.acme, ap1, "ok").ok()
            assertEquals(ApprovalStatus.Approved, approved.status)
            assertEquals(listOf("GET", "POST"), world.server.requests.map { it.method })
            val post = world.server.requests.last()
            assertEquals("/api/approvals/$AP1/approve", post.path)
            assertEquals("""{"decisionNote":"ok"}""", post.body)
        }

    @Test
    fun `AD-012 a missing note is sent as no note and reject and revision use their own paths`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme), StubApproval(AP2, acme))
            world.approvals.reject(world.acme, ap1, null).ok()
            world.approvals.requestRevision(world.acme, ApprovalId(AP2), "needs more").ok()
            val posts = world.server.requests.filter { it.method == "POST" }
            assertEquals(
                listOf("/api/approvals/$AP1/reject", "/api/approvals/$AP2/request-revision"),
                posts.map { it.path },
            )
            assertEquals(listOf("{}", """{"decisionNote":"needs more"}"""), posts.map { it.body })
        }

    @Test
    fun `AD-012 an illegal transition is Rejected with the server reason`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme, status = "rejected"))
            assertEquals(
                fail(DomainError.Rejected("Only pending or revision requested approvals can be approved")),
                world.approvals.approve(world.acme, ap1, null),
            )
        }

    @Test
    fun `AD-012 add comment posts the body and returns the created comment`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme))
            val comment = world.approvals.addComment(world.acme, ap1, "Looks right.").ok()
            assertEquals("Looks right.", comment.body)
            val post = world.server.requests.last()
            assertEquals("POST", post.method)
            assertEquals("/api/approvals/$AP1/comments", post.path)
            assertEquals("""{"body":"Looks right."}""", post.body)
        }

    @Test
    fun `AD-002 a failing mutation is sent once and never retried`() =
        runTest {
            var posts = 0
            val engine =
                MockEngine { request ->
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    if (request.method.value == "GET") {
                        respond(ApprovalFixtures.approval(StubApproval(AP1, acme)), HttpStatusCode.OK, json)
                    } else {
                        posts += 1
                        respond(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError, json)
                    }
                }
            val gateway = ApprovalGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            assertEquals(fail(DomainError.ServerError), gateway.approve(world.acme, ap1, null))
            assertEquals(1, posts)
        }

    @Test
    fun `AD-012 a viewer may read but every write is Forbidden`() =
        runTest {
            world.home.seed(StubApproval(AP1, ServerFixtures.COMPANY_GLOBEX))
            world.approvals.get(world.globex, ap1).ok()
            assertEquals(fail(DomainError.Forbidden), world.approvals.approve(world.globex, ap1, null))
            assertEquals(fail(DomainError.Forbidden), world.approvals.addComment(world.globex, ap1, "x"))
        }

    @Test
    fun `AD-012 a factory with no key is Unauthorized and an unknown factory is NotFound`() =
        runTest {
            assertEquals(fail(DomainError.Unauthorized), world.approvals.get(world.signedOut, ap1))
            assertEquals(fail(DomainError.NotFound), world.approvals.get(world.unknownFactory, ap1))
            assertTrue(world.server.requests.isEmpty())
        }

    @Test
    fun `F-A-10 an approval of another company is NotFound for linked issues and comments even if empty`() =
        runTest {
            world.home.seed(StubApproval(AP1, ServerFixtures.COMPANY_GLOBEX))
            // The scope is Acme; the approval is Globex's and has no issues and no comments to prove ownership.
            assertEquals(fail(DomainError.NotFound), world.approvals.linkedIssues(world.acme, ap1))
            assertEquals(fail(DomainError.NotFound), world.approvals.comments(world.acme, ap1))
        }

    @Test
    fun `F-A-10 an own approval with nothing linked is an empty list after one ownership read`() =
        runTest {
            world.home.seed(StubApproval(AP1, acme))
            assertEquals(DomainResult.Success(emptyList<LinkedIssue>()), world.approvals.linkedIssues(world.acme, ap1))
            assertEquals(DomainResult.Success(emptyList<ApprovalComment>()), world.approvals.comments(world.acme, ap1))
            assertEquals(
                listOf(
                    "/api/approvals/$AP1/issues",
                    "/api/approvals/$AP1",
                    "/api/approvals/$AP1/comments",
                    "/api/approvals/$AP1",
                ),
                world.server.requests.map { it.path },
            )
        }
}
