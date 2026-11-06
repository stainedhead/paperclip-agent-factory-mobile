package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.BudgetIncidentId
import com.stainedhead.paperclipmobile.domain.BudgetResolution
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.RunId
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

private const val AUDIT_RUN = "a0000001-0000-4000-8000-000000000001"
private const val ATTACHMENT = "c3000000-0000-4000-8000-000000000001"
private const val QUESTION_CARD = "d1000000-0000-4000-8000-000000000001"
private const val FOREIGN_AGENT = "e0000007-0000-4000-8000-000000000007"
private const val FOREIGN_RUN = "a0000007-0000-4000-8000-000000000007"
private const val FOREIGN_ISSUE = "c0000007-0000-4000-8000-000000000007"
private const val FOREIGN_CARD = "d1000000-0000-4000-8000-000000000007"
private val globexCompany = ServerFixtures.COMPANY_GLOBEX

/** Work for Acme on [work], and one agent, run, issue and card of Globex that an Acme scope must never touch. */
private fun seed(work: StubWork) {
    work.attention.items += FeedFixtures.standard()
    work.approvals.approvals += StubApproval(APPROVAL, company)
    work.decisions.decisions += StubDecision(DECISION, company)
    work.budgets.incidents += StubIncident("b0000001-0000-4000-8000-000000000001", company, "policy-1")
    work.dashboard.spend += StubSpendRow("anthropic", "5h", "100")
    work.agents.agents += StubAgent(ENGINEER_ID, company)
    work.agents.agents += StubAgent(FOREIGN_AGENT, globexCompany)
    work.runs.runs += StubRun(AUDIT_RUN, company, ENGINEER_ID, "Engineer 1", PAP_1182, "running")
    work.runs.runs.last().apply {
        events += StubRunEvent(1, "lifecycle", "system", "info", "Started")
        log = "line one\n"
    }
    work.runs.runs += StubRun(FOREIGN_RUN, globexCompany, FOREIGN_AGENT, "Other", FOREIGN_ISSUE, "running")
    work.issueStore.issues += StubIssue(PAP_1182, company, "PAP-1182", "Ship it", "in_progress")
    work.issueStore.issues[0].assigneeAgentId = ENGINEER_ID
    work.issueStore.issues += StubIssue(FOREIGN_ISSUE, globexCompany, "GLB-1", "Not yours")
    val file = "hello".encodeToByteArray()
    work.thread.attachments += StubAttachment(ATTACHMENT, company, PAP_1182, "a.txt", "text/plain", file)
    val card = InteractionFixtures.confirmation("Go?")
    work.interactions.interactions += StubInteraction(QUESTION_CARD, company, PAP_1182, "request_confirmation", card)
    work.interactions.interactions +=
        StubInteraction(FOREIGN_CARD, globexCompany, FOREIGN_ISSUE, "request_confirmation", card)
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
    results.addAll(exerciseWork(scope))
    return results
}

/** Every method of the dashboard, agent, run, issue and interaction gateways, success and failure paths alike. */
private suspend fun WorkWorld.exerciseWork(scope: CompanyScope): List<Any?> {
    val results = mutableListOf<Any?>()
    val issue = IssueId(PAP_1182)
    val agent = AgentId(ENGINEER_ID)
    val run = RunId(AUDIT_RUN)
    results += dashboard.dashboard(scope)
    results += dashboard.windowSpend(scope)
    results += agents.list(scope)
    results += agents.get(scope, agent)
    results += agents.setBudget(scope, agent, Money.usdCents(50_000))
    results += agents.invokeHeartbeat(scope, agent)
    results += agents.pause(scope, agent)
    results += agents.resume(scope, agent)
    results += runs.get(scope, run)
    results += runs.events(scope, run, 0L)
    results += runs.log(scope, run, 0L)
    results += runs.live(scope)
    results += runs.forIssue(scope, issue)
    results += runs.forAgent(scope, agent)
    results += runs.cancel(scope, run)
    results += issues.list(scope, IssueQuery())
    results += issues.get(scope, issue)
    results += issues.comments(scope, issue)
    results += issues.addComment(scope, issue, "Hello", CommentOptions(reopen = true))
    results += issues.setStatus(scope, issue, IssueStatus.InReview)
    results += issues.assign(scope, issue, Assignee.AgentAssignee(agent))
    results += issues.create(scope, NewIssue("New"))
    results += issues.attachments(scope, issue)
    results += issues.attachmentContent(scope, issue, ATTACHMENT, 0, 2)
    results += interactions.list(scope, issue)
    results += interactions.answer(scope, issue, InteractionId(QUESTION_CARD), InteractionAnswer.Accept)
    return results
}

/** The reads and writes of the five 1b-4b gateways against an engine that fails with secrets in its messages. */
private suspend fun WorkWorld.hostileWork(client: PaperclipHttpClient): List<Any?> {
    val issue = IssueId(PAP_1182)
    val agent = AgentId(ENGINEER_ID)
    val run = RunId(AUDIT_RUN)
    val results = mutableListOf<Any?>()
    results += DashboardGatewayAdapter(client, instances, secrets).dashboard(acme)
    val agentGateway = AgentGatewayAdapter(client, instances, secrets)
    results += agentGateway.get(acme, agent)
    results += agentGateway.pause(acme, agent)
    val runGateway = RunGatewayAdapter(client, instances, secrets)
    results += runGateway.log(acme, run, 0L)
    results += runGateway.cancel(acme, run)
    val issueGateway = IssueGatewayAdapter(client, instances, secrets)
    results += issueGateway.get(acme, issue)
    results += issueGateway.addComment(acme, issue, "x", CommentOptions())
    results += issueGateway.attachmentContent(acme, issue, ATTACHMENT, 0, 1)
    val interactionGateway = InteractionGatewayAdapter(client, instances, secrets)
    results += interactionGateway.answer(acme, issue, InteractionId(QUESTION_CARD), InteractionAnswer.Accept)
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
                        path.startsWith("decisions/") || path.startsWith("agents/") ||
                        path.startsWith("heartbeat-runs/") || path.startsWith("issues/") ||
                        path.startsWith("attachments/")
                assertTrue(ok, request.toString())
            }
            assertTrue(world.server.requests.none { "company-globex" in it.path || "company-elsewhere" in it.path })
        }

    @Test
    fun `AD-014 an Acme scope never reads the body of or mutates a Globex agent run issue or card`() =
        runTest {
            val agent = AgentId(FOREIGN_AGENT)
            val run = RunId(FOREIGN_RUN)
            val issue = IssueId(FOREIGN_ISSUE)
            val notFound = DomainResult.Failure(DomainError.NotFound)
            val replies =
                listOf(
                    world.agents.get(world.acme, agent),
                    world.agents.pause(world.acme, agent),
                    world.agents.resume(world.acme, agent),
                    world.agents.invokeHeartbeat(world.acme, agent),
                    world.agents.setBudget(world.acme, agent, Money.usdCents(1)),
                    world.runs.get(world.acme, run),
                    world.runs.events(world.acme, run, 0L),
                    world.runs.log(world.acme, run, 0L),
                    world.runs.cancel(world.acme, run),
                    world.runs.forIssue(world.acme, issue),
                    world.issues.get(world.acme, issue),
                    world.issues.comments(world.acme, issue),
                    world.issues.addComment(world.acme, issue, "x", CommentOptions()),
                    world.issues.setStatus(world.acme, issue, IssueStatus.Done),
                    world.issues.assign(world.acme, issue, Assignee.None),
                    world.issues.attachments(world.acme, issue),
                    world.issues.create(world.acme, NewIssue("x", parentId = issue)),
                    world.interactions.list(world.acme, issue),
                    world.interactions.answer(world.acme, issue, InteractionId(FOREIGN_CARD), InteractionAnswer.Accept),
                )
            replies.forEach { assertEquals(notFound, it) }
            val writes = setOf("POST", "PATCH", "PUT", "DELETE")
            assertTrue(world.server.requests.none { it.method in writes })
            assertTrue(world.server.requests.none { it.path.endsWith("/events") || it.path.endsWith("/log") })
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
                    // A proxy that echoes the submitted keys in a 422 error text (F-C-5).
                    respond(
                        """{"error":"cannot use ${keys.joinToString(" and ")}"}""",
                        HttpStatusCode.UnprocessableEntity,
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
                results.addAll(world.hostileWork(client))
            }
            val sink = results.joinToString("\n") { it.toString() } + trace.joinToString("\n")
            keys.forEach { key -> assertTrue(key !in sink, "a key leaked: $sink") }
            assertTrue("https://" !in sink && "token=" !in sink, sink)
        }
}
