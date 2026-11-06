package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.ApprovalType
import com.stainedhead.paperclipmobile.domain.AttentionDetail
import com.stainedhead.paperclipmobile.domain.AttentionFeed
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.BudgetAlertInfo
import com.stainedhead.paperclipmobile.domain.BudgetThreshold
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DecisionVerb
import com.stainedhead.paperclipmobile.domain.DismissalKind
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SubjectKind
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.FeedQuery
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private const val BUDGET_DEDUP = "budget:policy-1:2026-11-01T00:00:00.000Z:hard"
private val approval = AttentionSourceKind.Approval
private val teleport = AttentionSourceKind.Unsupported("teleport")

private fun fail(error: DomainError) = DomainResult.Failure(error)

private fun <T> DomainResult<T>.ok(): T = (this as DomainResult.Success).value

private fun gatewayAnswering(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    contentType: String = "application/json",
): AttentionGateway {
    val world = WorkWorld()
    val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }
    val client = PaperclipHttpClient(engine, allowInsecureLocal = false)
    return AttentionGatewayAdapter(client, world.instances, world.secrets)
}

private suspend fun pageOf(vararg items: kotlinx.serialization.json.JsonObject): AttentionFeed =
    gatewayAnswering(FeedFixtures.page(items.toList())).feed(WorkWorld().acme, FeedQuery()).ok()

/** AD-012: the feed request, the dismissal and triage requests, and how replies are read. */
class AttentionGatewayAdapterTest {
    private val world = WorkWorld()

    private fun seeded(): WorkWorld = world.also { it.home.attention.items += FeedFixtures.standard() }

    @Test
    fun `AD-012 feed asks for the decide order with the limit for its own company with the stored key`() =
        runTest {
            seeded().attention.feed(world.acme, FeedQuery(limit = 2)).ok()
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/companies/company-acme/attention", request.path)
            assertEquals("sort=decide&limit=2", request.query)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
        }

    @Test
    fun `AD-012 a cursor is passed through unchanged and a stale one is Rejected with the server reason`() =
        runTest {
            val first = seeded().attention.feed(world.acme, FeedQuery(limit = 2)).ok()
            val cursor = first.nextCursor.orEmpty()
            world.attention.feed(world.acme, FeedQuery(cursor = cursor, limit = 2)).ok()
            assertEquals(
                "sort=decide&limit=2&cursor=$cursor",
                world.server.requests
                    .last()
                    .query,
            )
            val gone = FeedQuery(cursor = StubCursor.encode("decide", "gone:1"), limit = 2)
            val stale = world.attention.feed(world.acme, gone)
            assertEquals(fail(DomainError.Rejected("Attention cursor no longer matches the filtered feed")), stale)
        }

    @Test
    fun `AD-012 the page maps its counts cursor and every item in server order`() =
        runTest {
            val page = seeded().attention.feed(world.acme, FeedQuery(limit = 3)).ok()
            assertEquals(Instant.parse("2026-11-01T12:00:00.000Z"), page.generatedAt)
            assertEquals(5, page.totalCount)
            assertEquals(3, page.items.size)
            assertEquals(
                listOf("approval:approval:ap-1", "decision:decision:dec-1", "budget_alert:$BUDGET_DEDUP"),
                page.items.map { it.id },
            )
            assertEquals(1, page.countsBySourceKind[AttentionSourceKind.Approval])
            assertEquals(0, page.countsBySourceKind[AttentionSourceKind.Review])
            assertTrue(page.nextCursor != null)
        }

    @Test
    fun `AD-012 an approval card maps the subject type verbs decide-by and detail`() =
        runTest {
            val card = pageOf(FeedFixtures.approvalItem()).items.single()
            assertEquals(AttentionSourceKind.Approval, card.sourceKind)
            assertEquals(SubjectKind.Approval, card.subject.kind)
            assertEquals("ap-1", card.subject.id)
            assertEquals(ApprovalType.HireAgent, card.subject.approvalType)
            assertEquals("issue-1", card.subject.issueId)
            assertEquals("/ACM/approvals/ap-1", card.subject.href)
            assertEquals("attention:approval:ap-1", card.dismissalKey)
            assertEquals(DecideBy.Today, card.decideBy)
            assertEquals(Severity.Medium, card.severity)
            assertEquals("CEO", card.originAgentName)
            assertTrue(card.inlineResolvable)
            val approve = DecisionVerb("approve", "Approve", "Approve it.")
            val verbs = listOf(approve, DecisionVerb("reject", "Reject", null))
            assertEquals(verbs, card.decisionVerbs)
            assertEquals(AttentionDetail.ApprovalSummary("hire_agent", "Hire a Designer"), card.detail)
            assertEquals(Instant.parse("2026-11-01T10:00:00.000Z"), card.activityAt)
            assertEquals(Instant.parse("2026-11-01T08:00:00.000Z"), card.createdAt)
            assertNull(card.resolverAudience)
        }

    @Test
    fun `AD-012 an interaction card maps the kind and who may resolve it`() =
        runTest {
            val card = pageOf(FeedFixtures.interactionItem()).items.single()
            assertEquals(SubjectKind.Interaction, card.subject.kind)
            assertEquals(InteractionKind.AskUserQuestions, card.subject.interactionKind)
            assertEquals("issue-1182", card.subject.issueId)
            assertEquals(AttentionDetail.Questions(2, "Which database?"), card.detail)
            val audience = card.resolverAudience
            assertEquals(ResolverPolicy.NotCreator, audience?.requestedPolicy)
            assertEquals(ResolverPolicy.HumanOnly, audience?.effectivePolicy)
            assertEquals(ResolverPolicySource.CompanyCap, audience?.policySource)
            assertEquals("user-2", audience?.addresseeUserId)
            assertNull(audience?.addresseeName)
            assertEquals("Engineer 1", audience?.createdByAgentName)
        }

    @Test
    fun `AD-012 a budget card maps exact amounts rounded up and the threshold`() =
        runTest {
            val card = pageOf(FeedFixtures.budgetItem()).items.single()
            assertEquals(SubjectKind.BudgetIncident, card.subject.kind)
            val hard = Money.usdCents(10_000)
            val observed = Money.usdCents(10_001)
            assertEquals(BudgetAlertInfo(BudgetThreshold.Hard, observed, hard, "agent"), card.subject.budget)
            assertEquals(AttentionDetail.Budget(100, observed, hard), card.detail)
            assertEquals(DecideBy.OnDate(kotlinx.datetime.LocalDate(2026, 11, 2)), card.decideBy)
            assertEquals("b0000001-0000-4000-8000-000000000001", card.budgetIncident()?.id?.value)
        }

    @Test
    fun `AD-012 all twelve detail kinds map and an unknown or unreadable one is Unsupported`() =
        runTest {
            val cases =
                listOf(
                    FeedFixtures.detail("approval", "approvalType" to "hire_agent", "summaryExcerpt" to "S") to
                        AttentionDetail.ApprovalSummary("hire_agent", "S"),
                    FeedFixtures.detail(
                        "plan_approval",
                        "issueTitle" to "I",
                        "planTitle" to "P",
                        "summaryExcerpt" to "S",
                    ) to
                        AttentionDetail.PlanApproval("I", "P", "S"),
                    FeedFixtures.detail("confirmation", "promptExcerpt" to "Go?") to
                        AttentionDetail.Confirmation("Go?"),
                    FeedFixtures.detail("questions", "questionCount" to 3, "firstQuestionText" to "Q") to
                        AttentionDetail.Questions(3, "Q"),
                    FeedFixtures.detail("suggested_tasks", "taskCount" to 4, "firstTaskTitle" to "T") to
                        AttentionDetail.SuggestedTasks(4, "T"),
                    FeedFixtures.detail("checkbox_confirmation", "optionCount" to 2, "promptExcerpt" to "P") to
                        AttentionDetail.CheckboxConfirmation(2, "P"),
                    FeedFixtures.detail("item_verdicts", "itemCount" to 5, "promptExcerpt" to "P") to
                        AttentionDetail.ItemVerdicts(5, "P"),
                    FeedFixtures.detail("failed_run", "agentName" to "A", "failureReasonExcerpt" to "R") to
                        AttentionDetail.FailedRun("A", "R"),
                    FeedFixtures.detail("agent_error", "agentName" to "A", "failureReasonExcerpt" to "R") to
                        AttentionDetail.AgentError("A", "R"),
                    FeedFixtures.detail("generic", "summaryExcerpt" to "G") to AttentionDetail.Generic("G"),
                    FeedFixtures.detail("hologram", "x" to 1) to AttentionDetail.Unsupported("hologram"),
                    FeedFixtures.detail("budget", "amountObserved" to "lots") to AttentionDetail.Unsupported("budget"),
                    FeedFixtures.detail("questions") to AttentionDetail.Questions(0, null),
                )
            cases.forEach { (detail, expected) ->
                val card = pageOf(FeedFixtures.with(FeedFixtures.decisionItem(), "detail" to detail)).items.single()
                assertEquals(expected, card.detail, detail.toString())
            }
        }

    @Test
    fun `AD-012 a blocker detail names the blocking issue and counts blocked tasks`() =
        runTest {
            val blocker =
                kotlinx.serialization.json.buildJsonObject {
                    put("kind", JsonPrimitive("blocker"))
                    put(
                        "blockingIssue",
                        kotlinx.serialization.json.buildJsonObject {
                            put("id", JsonPrimitive("i-9"))
                            put("identifier", JsonPrimitive("PAP-9"))
                            put("title", JsonNull)
                        },
                    )
                    put("blockedTaskCount", JsonPrimitive(3))
                }
            val card = pageOf(FeedFixtures.with(FeedFixtures.decisionItem(), "detail" to blocker)).items.single()
            assertEquals(AttentionDetail.Blocker("PAP-9", null, 3), card.detail)
            val none = FeedFixtures.detail("blocker")
            val bare = pageOf(FeedFixtures.with(FeedFixtures.decisionItem(), "detail" to none)).items.single()
            assertEquals(AttentionDetail.Blocker(null, null, null), bare.detail)
        }

    @Test
    fun `AD-003 unknown kinds severities and decide-by values never crash`() =
        runTest {
            val odd =
                FeedFixtures.with(
                    FeedFixtures.item("teleport", "t-1", FeedFixtures.subject("portal", "p-1", "A portal")),
                    "severity" to JsonPrimitive("apocalyptic"),
                    "decideBy" to JsonPrimitive("next-century"),
                )
            val card = pageOf(odd).items.single()
            assertEquals(AttentionSourceKind.Unsupported("teleport"), card.sourceKind)
            assertEquals(SubjectKind.Unknown, card.subject.kind)
            assertEquals(Severity.Medium, card.severity)
            assertEquals(DecideBy.None, card.decideBy)
            val dates = listOf("today", "this_week", "whenever", "2026-02-30", null)
            val parsed =
                dates.map {
                    val subject = FeedFixtures.subject("approval", "a", "A")
                    val card = FeedFixtures.item("approval", "a", subject, decideBy = it)
                    pageOf(card).items.single().decideBy
                }
            val expected = listOf(DecideBy.Today, DecideBy.ThisWeek, DecideBy.Whenever, DecideBy.None, DecideBy.None)
            assertEquals(expected, parsed)
        }

    @Test
    fun `AD-012 the dismissal and triage snooze the server reports are kept`() =
        runTest {
            val dismissal =
                kotlinx.serialization.json.buildJsonObject {
                    put("kind", JsonPrimitive("snooze"))
                    put("dismissedAt", JsonPrimitive("2026-11-01T09:00:00.000Z"))
                    put("snoozedUntil", JsonPrimitive("2026-11-01T18:00:00.000Z"))
                    put("isActive", JsonPrimitive(true))
                }
            val item =
                FeedFixtures.with(
                    FeedFixtures.approvalItem(),
                    "dismissal" to dismissal,
                    "snoozedUntil" to JsonPrimitive("2026-11-02T08:00:00.000Z"),
                )
            val card = pageOf(item).items.single()
            assertEquals(DismissalKind.Snooze, card.dismissal?.kind)
            assertEquals(Instant.parse("2026-11-01T18:00:00.000Z"), card.dismissal?.snoozedUntil)
            assertTrue(card.dismissal?.isActive == true)
            assertEquals(Instant.parse("2026-11-02T08:00:00.000Z"), card.snoozedUntil)
        }

    @Test
    fun `AD-003 a page or card missing what identifies it is Malformed and never partly shown`() =
        runTest {
            val good = FeedFixtures.approvalItem()
            val without = { name: String -> withoutField(good, name) }
            val bad =
                listOf(
                    "{}",
                    "[]",
                    "null",
                    FeedFixtures.page(listOf(without("id"))),
                    FeedFixtures.page(listOf(without("dismissalKey"))),
                    FeedFixtures.page(listOf(without("subject"))),
                    FeedFixtures.page(listOf(without("activityAt"))),
                    FeedFixtures.page(listOf(good, FeedFixtures.with(good, "createdAt" to JsonPrimitive("yesterday")))),
                    FeedFixtures.page(listOf(good)).replace("\"generatedAt\":\"2026", "\"generatedAt\":\"now2026"),
                    FeedFixtures.page(listOf(good)).replace("\"totalCount\":1", "\"totalCount\":\"one\""),
                    FeedFixtures.page(listOf(good)).replace("\"items\":[", "\"entries\":["),
                )
            bad.forEach {
                val result = gatewayAnswering(it).feed(world.acme, FeedQuery())
                assertEquals(fail(DomainError.Malformed), result, it.take(100))
            }
            val html = gatewayAnswering("<html></html>", contentType = "text/html")
            assertEquals(fail(DomainError.Malformed), html.feed(world.acme, FeedQuery()))
            assertEquals(fail(DomainError.Malformed), gatewayAnswering("").feed(world.acme, FeedQuery()))
        }

    @Test
    fun `AD-012 dismiss posts the item key and answers with the dismissal`() =
        runTest {
            seeded()
            val dismissal = world.attention.dismiss(world.acme, "attention:approval:ap-1").ok()
            assertEquals(DismissalKind.Dismiss, dismissal.kind)
            assertEquals(world.clock.now(), dismissal.dismissedAt)
            assertNull(dismissal.snoozedUntil)
            assertTrue(dismissal.isActive)
            val post = world.server.requests.single()
            assertEquals("POST", post.method)
            assertEquals("/api/companies/company-acme/inbox-dismissals", post.path)
            assertEquals("""{"itemKey":"attention:approval:ap-1","kind":"dismiss"}""", post.body)
        }

    @Test
    fun `AD-012 snooze posts the time and restore deletes the encoded key`() =
        runTest {
            seeded()
            val until = world.clock.now() + 3.hours
            val snoozed = world.attention.snooze(world.acme, "attention:approval:ap-1", until).ok()
            assertEquals(DismissalKind.Snooze, snoozed.kind)
            assertEquals(until, snoozed.snoozedUntil)
            assertEquals(
                """{"itemKey":"attention:approval:ap-1","kind":"snooze","snoozedUntil":"$until"}""",
                world.server.requests
                    .single()
                    .body,
            )
            val key = "attention:$BUDGET_DEDUP"
            world.attention.restore(world.acme, key).ok()
            val delete = world.server.requests.last()
            assertEquals("DELETE", delete.method)
            assertTrue(delete.path.startsWith("/api/companies/company-acme/inbox-dismissals/attention"), delete.path)
            val tail = BUDGET_DEDUP.substringAfter("policy-1:")
            assertTrue(delete.path.endsWith(tail) || delete.path.endsWith(tail.replace(":", "%3A")), delete.path)
        }

    @Test
    fun `F-A-4 an HTML or empty 200 is Malformed for restore and for setDecideBy`() =
        runTest {
            val approval = AttentionSourceKind.Approval
            listOf("<html>login</html>" to "text/html", "" to "text/plain").forEach { (body, type) ->
                val gateway = gatewayAnswering(body, HttpStatusCode.OK, type)
                assertEquals(fail(DomainError.Malformed), gateway.restore(world.acme, "attention:x"), type)
                assertEquals(
                    fail(DomainError.Malformed),
                    gateway.setDecideBy(world.acme, approval, "ap-1", DecideBy.Today),
                    type,
                )
            }
        }

    @Test
    fun `F-A-4 a JSON 200 or a 204 is a success for restore and for setDecideBy`() =
        runTest {
            val approval = AttentionSourceKind.Approval
            val json = gatewayAnswering("""{"ok":true}""", HttpStatusCode.OK)
            assertEquals(DomainResult.Success(Unit), json.restore(world.acme, "attention:x"))
            assertEquals(DomainResult.Success(Unit), json.setDecideBy(world.acme, approval, "ap-1", DecideBy.Today))
            val none = gatewayAnswering("", HttpStatusCode.NoContent, "text/plain")
            assertEquals(DomainResult.Success(Unit), none.setDecideBy(world.acme, approval, "ap-1", DecideBy.Today))
        }

    @Test
    fun `AD-012 a restore whose reply has no body is a success and a dismiss reply without a time is Malformed`() =
        runTest {
            val noBody = gatewayAnswering("", HttpStatusCode.NoContent)
            assertEquals(DomainResult.Success(Unit), noBody.restore(world.acme, "attention:x"))
            val empty = gatewayAnswering("{}", HttpStatusCode.Created)
            assertEquals(fail(DomainError.Malformed), empty.dismiss(world.acme, "attention:x"))
            val html = gatewayAnswering("<b>", HttpStatusCode.Created, "text/html")
            assertEquals(fail(DomainError.Malformed), html.dismiss(world.acme, "attention:x"))
        }

    @Test
    fun `AD-012 decide-by is put with the source identity and clearing it sends an explicit null`() =
        runTest {
            seeded()
            world.attention.setDecideBy(world.acme, AttentionSourceKind.Approval, "ap-1", DecideBy.ThisWeek).ok()
            val put = world.server.requests.single()
            assertEquals("PUT", put.method)
            assertEquals("/api/companies/company-acme/decision-triage/approval/ap-1", put.path)
            assertEquals("""{"decideBy":"this_week"}""", put.body)
            val onDate = DecideBy.OnDate(kotlinx.datetime.LocalDate(2026, 11, 9))
            world.attention.setDecideBy(world.acme, AttentionSourceKind.Approval, "ap-1", onDate).ok()
            assertEquals(
                """{"decideBy":"2026-11-09"}""",
                world.server.requests
                    .last()
                    .body,
            )
            world.attention.setDecideBy(world.acme, AttentionSourceKind.Approval, "ap-1", DecideBy.None).ok()
            assertEquals(
                """{"decideBy":null}""",
                world.server.requests
                    .last()
                    .body,
            )
        }

    @Test
    fun `AD-012 a triage source the server does not know is a typed error`() =
        runTest {
            seeded()
            val result = world.attention.setDecideBy(world.acme, approval, "no-such", DecideBy.Today)
            assertEquals(fail(DomainError.NotFound), result)
            val bad = world.attention.setDecideBy(world.acme, teleport, "x", DecideBy.Today)
            assertIs<DomainError.Rejected>((bad as DomainResult.Failure).error)
        }

    @Test
    fun `AD-002 a failing mutation is sent once and never retried`() =
        runTest {
            var posts = 0
            val engine =
                MockEngine {
                    posts += 1
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    respond(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError, json)
                }
            val gateway = AttentionGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            assertEquals(fail(DomainError.ServerError), gateway.dismiss(world.acme, "attention:x"))
            assertEquals(fail(DomainError.ServerError), gateway.restore(world.acme, "attention:x"))
            assertEquals(fail(DomainError.ServerError), gateway.setDecideBy(world.acme, approval, "a", DecideBy.Today))
            assertEquals(3, posts)
        }

    @Test
    fun `AD-012 a viewer may read the feed but every triage write is Forbidden`() =
        runTest {
            world.attention.feed(world.globex, FeedQuery()).ok()
            assertEquals(fail(DomainError.Forbidden), world.attention.dismiss(world.globex, "attention:x"))
            assertEquals(fail(DomainError.Forbidden), world.attention.restore(world.globex, "attention:x"))
            val triage = world.attention.setDecideBy(world.globex, approval, "a", DecideBy.None)
            assertEquals(fail(DomainError.Forbidden), triage)
            assertEquals(fail(DomainError.Forbidden), world.attention.feed(world.elsewhere, FeedQuery()))
        }

    @Test
    fun `AD-012 a factory with no key is Unauthorized and an unknown factory is NotFound`() =
        runTest {
            assertEquals(fail(DomainError.Unauthorized), world.attention.feed(world.signedOut, FeedQuery()))
            assertEquals(fail(DomainError.NotFound), world.attention.feed(world.unknownFactory, FeedQuery()))
            assertTrue(world.server.requests.isEmpty())
        }
}

private fun withoutField(
    source: kotlinx.serialization.json.JsonObject,
    name: String,
) = kotlinx.serialization.json.JsonObject(source - name)
