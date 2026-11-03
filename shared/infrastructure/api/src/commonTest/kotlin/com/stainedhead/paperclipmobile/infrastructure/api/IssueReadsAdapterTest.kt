package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssueFilter
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueQuery
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val acme = ServerFixtures.COMPANY_ACME
private val root = IssueId(PAP_1182)

/** The query of a recorded request as name to decoded value. */
internal fun RecordedRequest.params(): Map<String, String> =
    query
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { it.substringBefore('=').decodeURLPart() to it.substringAfter('=', "").decodeURLPart() }

/** AD-013: the reads of the issue gateway: list, detail with subtasks and plan, and the comment thread. */
class IssueReadsAdapterTest {
    private val world = WorkWorld().also(::seedIssues)

    private fun gateway(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) = IssueGatewayAdapter(answeringClient(body, status, contentType), world.instances, world.secrets)

    @Test
    fun `AD-013 list asks for the compact view of its own company with the page and the open statuses`() =
        runTest {
            world.issues.list(world.acme, IssueQuery(limit = 20, offset = 40)).value()
            val request = world.server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/companies/company-acme/issues", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
            assertEquals(
                mapOf(
                    "view" to "compact",
                    "limit" to "20",
                    "offset" to "40",
                    "status" to "backlog,todo,in_progress,in_review,blocked",
                ),
                request.params(),
            )
        }

    @Test
    fun `AD-013 each saved filter becomes the parameters the server has for it`() =
        runTest {
            val sent = { filter: IssueFilter, search: String? -> IssueQuery(filter, search) }
            world.issues.list(world.acme, sent(IssueFilter.Blocked, null)).value()
            world.issues.list(world.acme, sent(IssueFilter.InReview, null)).value()
            world.issues.list(world.acme, sent(IssueFilter.AssignedToMe, "billing")).value()
            world.issues.list(world.acme, sent(IssueFilter.CriticalAndHigh, null)).value()
            val sentParams = world.server.requests.map { it.params() }
            val blocked = sentParams[0]
            val review = sentParams[1]
            val mine = sentParams[2]
            val urgent = sentParams[3]
            assertEquals("blocked", blocked["attention"])
            assertNull(blocked["status"])
            assertEquals("in_review", review["status"])
            assertEquals("me", mine["assigneeUserId"])
            assertEquals("billing", mine["q"])
            // There is no priority parameter: the server list for Critical and High is the open list (research W1).
            assertEquals("backlog,todo,in_progress,in_review,blocked", urgent["status"])
            assertNull(urgent["priority"])
        }

    @Test
    fun `AD-013 list rows map to issues and carry no description because the server sends a preview`() =
        runTest {
            val rows = world.issues.list(world.acme, IssueQuery(limit = 100)).value()
            val first = rows.first()
            assertEquals(IssueId(PAP_1182), first.id)
            assertEquals("PAP-1182", first.identifier)
            assertEquals(IssueStatus.InProgress, first.status)
            assertEquals(IssuePriority.High, first.priority)
            assertEquals(AgentId(ENGINEER_ID), first.assigneeAgentId)
            assertEquals("b1000000-0000-4000-8000-000000000001", first.projectId)
            assertEquals(Instant.parse("2026-10-30T09:00:00.000Z"), first.createdAt)
            assertNull(first.description)
            assertEquals(IssueId(PAP_1182), rows.first { it.id == IssueId(PAP_1190) }.parentId)
            assertTrue(rows.none { it.status == IssueStatus.Done })
        }

    @Test
    fun `AD-013 an unknown status and priority are Unknown and a missing identifier does not fail`() =
        runTest {
            val row =
                """{"id":"$PAP_1182","title":"T","status":"waiting","priority":"urgent",""" +
                    """"createdAt":"2026-11-01T10:00:00Z","updatedAt":"2026-11-01T10:00:00Z","identifier":null}"""
            val issue = gateway("[$row]").list(world.acme, IssueQuery()).value().single()
            assertEquals(IssueStatus.Unknown, issue.status)
            assertEquals(IssuePriority.Unknown, issue.priority)
            assertEquals(PAP_1182.take(8), issue.identifier)
        }

    @Test
    fun `AD-003 bodies that are not an issue list are Malformed`() =
        runTest {
            val noTitle = """[{"id":"$PAP_1182","status":"todo","priority":"low","createdAt":"2026-11-01T10:00:00Z","""
            val rows =
                NOT_THE_EXPECTED_SHAPE + "{}" + "[1]" + """[{"id":"$PAP_1182"}]""" +
                    noTitle + """"updatedAt":"2026-11-01T10:00:00Z"}]""" +
                    """[{"id":"$PAP_1182","title":"T","status":"todo","priority":"low",""" +
                    """"createdAt":"nope","updatedAt":"x"}]"""
            rows.forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                val reply = gateway(body, contentType = type).list(world.acme, IssueQuery())
                assertEquals(failed(DomainError.Malformed), reply, body)
            }
            assertEquals(emptyList(), gateway("[]").list(world.acme, IssueQuery()).value())
        }

    @Test
    fun `AD-013 a 400 from a bad parameter arrives as the reason and a viewer can list`() =
        runTest {
            val reply = ServerFixtures.error("limit must be a positive integer up to 1000")
            assertEquals(
                failed(DomainError.Rejected("limit must be a positive integer up to 1000")),
                gateway(reply, HttpStatusCode.BadRequest).list(world.acme, IssueQuery()),
            )
            world.issues.list(world.globex, IssueQuery()).value()
            assertEquals(failed(DomainError.Forbidden), world.issues.list(world.elsewhere, IssueQuery()))
        }

    @Test
    fun `AD-013 get reads the issue then its subtasks by parent and maps parent blockers project and plan`() =
        runTest {
            val child =
                world.home.issueStore.issues
                    .first { it.id == PAP_1190 }
            child.blockedBy += PAP_1191
            child.description = "Backfill all invoices.\nCheck totals."
            child.planBody = "1. Backfill\n2. Verify"
            child.planUpdatedAt = "2026-11-01T08:30:00.000Z"
            val detail = world.issues.get(world.acme, IssueId(PAP_1190)).value()
            assertEquals("Backfill all invoices.\nCheck totals.", detail.issue.description)
            assertEquals("Billing", detail.projectName)
            assertEquals(IssueId(PAP_1182), detail.parent?.id)
            assertEquals("PAP-1182", detail.parent?.identifier)
            assertEquals(IssueStatus.InProgress, detail.parent?.status)
            assertEquals(listOf(IssueId(PAP_1191)), detail.blockedBy.map { it.id })
            assertEquals("Update the runbook", detail.blockedBy.single().title)
            assertEquals("Plan", detail.plan?.title)
            assertEquals("1. Backfill\n2. Verify", detail.plan?.body)
            assertEquals(Instant.parse("2026-11-01T08:30:00.000Z"), detail.plan?.updatedAt)
            assertEquals(emptyList(), detail.subtasks)
            val sent = world.server.requests
            assertEquals(listOf("/api/issues/$PAP_1190", "/api/companies/company-acme/issues"), sent.map { it.path })
            assertEquals(mapOf("parentId" to PAP_1190, "view" to "compact"), sent[1].params())
        }

    @Test
    fun `AD-013 get lists the subtasks and falls back to the legacy plan from the description`() =
        runTest {
            val rootIssue =
                world.home.issueStore.issues
                    .first { it.id == PAP_1182 }
            rootIssue.legacyPlan = "Legacy plan text"
            rootIssue.projectName = null
            val detail = world.issues.get(world.acme, root).value()
            assertEquals(listOf(IssueId(PAP_1190), IssueId(PAP_1191)), detail.subtasks.map { it.id })
            assertNull(detail.parent)
            assertNull(detail.projectName)
            assertEquals(null, detail.plan?.title)
            assertEquals("Legacy plan text", detail.plan?.body)
            assertNull(detail.plan?.updatedAt)
        }

    @Test
    fun `AD-013 get without any plan has none`() =
        runTest {
            assertNull(
                world.issues
                    .get(world.acme, root)
                    .value()
                    .plan,
            )
        }

    @Test
    fun `AD-014 an issue of another company is NotFound with no subtask request and a bad id sends nothing`() =
        runTest {
            val globex = StubIssue("c0000009-0000-4000-8000-000000000009", "company-globex", "GLB-1", "Other")
            world.home.issueStore.issues += globex
            assertEquals(failed(DomainError.NotFound), world.issues.get(world.acme, IssueId(globex.id)))
            assertEquals(1, world.server.requests.size)
            assertEquals(failed(DomainError.NotFound), world.issues.get(world.acme, IssueId("no-such-issue")))
            assertEquals(failed(DomainError.NotFound), world.issues.comments(world.acme, IssueId("no-such-issue")))
            assertEquals(failed(DomainError.NotFound), world.issues.attachments(world.acme, IssueId("no-such-issue")))
            assertEquals(1, world.server.requests.size)
        }

    @Test
    fun `AD-003 an issue reply that is not an issue is Malformed`() =
        runTest {
            (NOT_THE_EXPECTED_SHAPE + "[]" + "{}" + """{"id":"$PAP_1182","companyId":"$acme"}""").forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                val reply = gateway(body, contentType = type).get(world.acme, root)
                assertEquals(failed(DomainError.Malformed), reply, body)
            }
        }

    private fun comment(
        n: Int,
        text: String,
        at: String,
        company: String = acme,
    ) = StubIssueComment("c2000000-0000-4000-8000-00000000000$n", company, PAP_1182, text, at)

    private fun seedThread() {
        val thread = world.home.thread
        thread.comments += comment(1, "First", "2026-11-01T09:00:00.000Z")
        thread.comments +=
            comment(2, "Agent here", "2026-11-01T10:00:00.000Z").also {
                it.authorType = "agent"
                it.authorUserId = null
                it.derivedAgentId = ENGINEER_ID
            }
        thread.comments += comment(3, "secret", "2026-11-01T10:30:00.000Z").also { it.deleted = true }
        thread.comments += comment(4, "Newest", "2026-11-01T11:00:00.000Z").also { it.authorType = "robot" }
    }

    @Test
    fun `AD-013 comments are asked oldest first because the server default is newest first`() =
        runTest {
            seedThread()
            val comments = world.issues.comments(world.acme, root).value()
            assertEquals(listOf("First", "Agent here", "Newest"), comments.map { it.body })
            val request = world.server.requests.single()
            assertEquals("/api/issues/$PAP_1182/comments", request.path)
            assertEquals(mapOf("order" to "asc"), request.params())
        }

    @Test
    fun `AD-013 comments map the author and a deleted comment is left out`() =
        runTest {
            seedThread()
            val comments = world.issues.comments(world.acme, root).value()
            assertEquals(CommentAuthorType.User, comments[0].authorType)
            assertEquals("user-1", comments[0].authorUserId)
            assertEquals(CommentAuthorType.Agent, comments[1].authorType)
            assertEquals(AgentId(ENGINEER_ID), comments[1].authorAgentId)
            assertEquals(CommentAuthorType.System, comments[2].authorType)
            assertEquals(Instant.parse("2026-11-01T09:00:00Z"), comments[0].createdAt)
            assertTrue(comments.none { "secret" in it.body })
        }

    @Test
    fun `AD-013 an empty thread is empty and a comment of another company is not trusted`() =
        runTest {
            assertEquals(emptyList(), world.issues.comments(world.acme, root).value())
            // An empty thread proves nothing about whose issue it is, so the issue is read as well.
            assertEquals(
                listOf("/api/issues/$PAP_1182/comments", "/api/issues/$PAP_1182"),
                world.server.requests.map { it.path },
            )
            val foreign = IssueRowFixtures.comment(comment(9, "x", "2026-11-01T09:00:00Z", "company-globex"))
            assertEquals(failed(DomainError.NotFound), gateway("[$foreign]").comments(world.acme, root))
            listOf("{}", "[1]", """[{"id":"x"}]""", "").forEach { body ->
                assertEquals(failed(DomainError.Malformed), gateway(body).comments(world.acme, root), body)
            }
        }
}
