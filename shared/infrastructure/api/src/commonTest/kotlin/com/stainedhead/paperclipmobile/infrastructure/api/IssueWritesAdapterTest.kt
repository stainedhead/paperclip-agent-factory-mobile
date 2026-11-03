package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.CommentAuthorType
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.NewIssue
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val acme = ServerFixtures.COMPANY_ACME
private val root = IssueId(PAP_1182)
private val closed = IssueId(PAP_1100)
private val engineer = AgentId(ENGINEER_ID)
private val notFound = failed(DomainError.NotFound)
private val forbidden = failed(DomainError.Forbidden)
private const val BLOCKED_RULE =
    "Entering blocked requires unresolved blockers, a pending interaction/approval, or unblockDescriptor"

/** AD-013: the writes of the issue gateway: comments, status, assignee and create. */
class IssueWritesAdapterTest {
    private val world = WorkWorld().also(::seedIssues)

    private val sent get() = world.server.requests

    @Test
    fun `AD-013 a comment reads the issue first then posts the body once and returns the comment`() =
        runTest {
            val comment = world.issues.addComment(world.acme, root, "Please retry.", CommentOptions()).value()
            assertEquals("Please retry.", comment.body)
            assertEquals(CommentAuthorType.User, comment.authorType)
            assertEquals(listOf("GET", "POST"), sent.map { it.method })
            assertEquals("/api/issues/$PAP_1182", sent[0].path)
            assertEquals("/api/issues/$PAP_1182/comments", sent[1].path)
            assertEquals("""{"body":"Please retry."}""", sent[1].body)
        }

    @Test
    fun `AD-013 reopen and interrupt are sent only when asked for and reopen moves a closed issue to todo`() =
        runTest {
            val both = CommentOptions(reopen = true, interrupt = true)
            world.issues.addComment(world.acme, closed, "Reopening.", both).value()
            assertEquals("""{"body":"Reopening.","reopen":true,"interrupt":true}""", sent.last().body)
            assertEquals(
                "todo",
                world.home.issueStore.issues
                    .first { it.id == PAP_1100 }
                    .status,
            )
            world.issues.addComment(world.acme, root, "Hi", CommentOptions(interrupt = true)).value()
            assertEquals("""{"body":"Hi","interrupt":true}""", sent.last().body)
        }

    @Test
    fun `AD-013 an interrupting comment stops the active run of the issue`() =
        runTest {
            val run = StubRun("a0000001-0000-4000-8000-000000000001", acme, ENGINEER_ID, "Eng", PAP_1182, "running")
            world.home.runs.runs += run
            world.issues.addComment(world.acme, root, "Stop", CommentOptions(interrupt = true)).value()
            assertEquals("cancelled", run.status)
        }

    @Test
    fun `AD-014 a comment on an issue of another company or a bad id sends no mutation`() =
        runTest {
            val globex = StubIssue("c0000009-0000-4000-8000-000000000009", "company-globex", "GLB-1", "Other")
            world.home.issueStore.issues += globex
            val none = CommentOptions()
            val bad = IssueId("bad")
            assertEquals(notFound, world.issues.addComment(world.acme, IssueId(globex.id), "x", none))
            assertEquals(notFound, world.issues.addComment(world.acme, bad, "x", none))
            assertEquals(notFound, world.issues.setStatus(world.acme, bad, IssueStatus.Done))
            assertEquals(notFound, world.issues.assign(world.acme, bad, Assignee.None))
            assertTrue(sent.none { it.method == "POST" || it.method == "PATCH" })
        }

    @Test
    fun `AD-013 status is patched alone and the changed issue is returned`() =
        runTest {
            val issue = world.issues.setStatus(world.acme, root, IssueStatus.InReview).value()
            assertEquals(IssueStatus.InReview, issue.status)
            assertEquals(listOf("GET", "PATCH"), sent.map { it.method })
            assertEquals("/api/issues/$PAP_1182", sent[1].path)
            assertEquals("""{"status":"in_review"}""", sent[1].body)
        }

    @Test
    fun `AD-013 a status the app does not know is refused here and the reasons of the server arrive`() =
        runTest {
            val unknown = world.issues.setStatus(world.acme, root, IssueStatus.Unknown) as DomainResult.Failure
            assertIs<DomainError.Rejected>(unknown.error)
            assertTrue(sent.isEmpty())
            assertEquals(
                failed(DomainError.Rejected("in_progress issues require an assignee")),
                world.issues.setStatus(world.acme, IssueId(PAP_1191), IssueStatus.InProgress),
            )
            assertEquals(
                failed(DomainError.Rejected(BLOCKED_RULE)),
                world.issues.setStatus(world.acme, IssueId(PAP_1191), IssueStatus.Blocked),
            )
        }

    @Test
    fun `AD-013 assigning an agent clears the user in the same patch and none clears both`() =
        runTest {
            world.home.issueStore.issues
                .first { it.id == PAP_1150 }
                .assigneeUserId = "user-1"
            val review = IssueId(PAP_1150)
            val assigned = world.issues.assign(world.acme, review, Assignee.AgentAssignee(engineer)).value()
            assertEquals(engineer, assigned.assigneeAgentId)
            assertEquals(null, assigned.assigneeUserId)
            assertEquals("""{"assigneeAgentId":"$ENGINEER_ID","assigneeUserId":null}""", sent.last().body)
            world.issues.assign(world.acme, review, Assignee.UserAssignee("user-1")).value()
            assertEquals("""{"assigneeUserId":"user-1","assigneeAgentId":null}""", sent.last().body)
            val cleared = world.issues.assign(world.acme, review, Assignee.None).value()
            assertEquals(null, cleared.assigneeAgentId)
            assertEquals("""{"assigneeAgentId":null,"assigneeUserId":null}""", sent.last().body)
        }

    @Test
    fun `AD-013 an agent that is not a uuid or does not exist is refused`() =
        runTest {
            val bad = world.issues.assign(world.acme, root, Assignee.AgentAssignee(AgentId("no-such-agent")))
            assertIs<DomainError.Rejected>((bad as DomainResult.Failure).error)
            assertTrue(sent.isEmpty())
            val missing = AgentId("e0000099-0000-4000-8000-000000000099")
            assertEquals(
                failed(DomainError.Rejected("Agent not found")),
                world.issues.assign(world.acme, root, Assignee.AgentAssignee(missing)),
            )
        }

    @Test
    fun `AD-013 a viewer can read but every write is Forbidden`() =
        runTest {
            val g = StubIssue("c0000008-0000-4000-8000-000000000008", "company-globex", "GLB-2", "Viewer's")
            world.home.issueStore.issues += g
            val id = IssueId(g.id)
            world.issues.get(world.globex, id).value()
            assertEquals(forbidden, world.issues.addComment(world.globex, id, "x", CommentOptions()))
            assertEquals(forbidden, world.issues.setStatus(world.globex, id, IssueStatus.Done))
            assertEquals(forbidden, world.issues.assign(world.globex, id, Assignee.None))
            assertEquals(forbidden, world.issues.create(world.globex, NewIssue("x")))
        }

    @Test
    fun `AD-013 create sends the title priority and allowDuplicate and returns the new issue`() =
        runTest {
            val created = world.issues.create(world.acme, NewIssue("Follow up", "Details", IssuePriority.High)).value()
            assertEquals("Follow up", created.title)
            assertEquals(IssuePriority.High, created.priority)
            assertEquals(IssueStatus.Backlog, created.status)
            assertEquals("Details", created.description)
            assertEquals(listOf("POST"), sent.map { it.method })
            assertEquals("/api/companies/company-acme/issues", sent[0].path)
            assertEquals(
                """{"title":"Follow up","description":"Details","priority":"high","allowDuplicate":true}""",
                sent[0].body,
            )
        }

    @Test
    fun `AD-013 create never lets the server hand back an existing issue of the same title`() =
        runTest {
            val first = world.issues.create(world.acme, NewIssue("Follow up")).value()
            val second = world.issues.create(world.acme, NewIssue("Follow up")).value()
            assertNotEquals(first.id, second.id)
            val body =
                """{"id":"$PAP_1182","companyId":"company-acme","deduplicated":true,"title":"T","status":"todo",""" +
                    """"priority":"low","identifier":"PAP-1","createdAt":"2026-11-01T10:00:00Z",""" +
                    """"updatedAt":"2026-11-01T10:00:00Z"}"""
            val answering = IssueGatewayAdapter(answeringClient(body), world.instances, world.secrets)
            assertEquals(failed(DomainError.Conflict), answering.create(world.acme, NewIssue("T")))
        }

    @Test
    fun `AD-013 a subtask names its parent after the parent is read and an assignee goes in the body`() =
        runTest {
            val sub = NewIssue("Sub", parentId = root, assignee = Assignee.AgentAssignee(engineer))
            val created = world.issues.create(world.acme, sub).value()
            assertEquals(root, created.parentId)
            assertEquals(engineer, created.assigneeAgentId)
            assertEquals(IssueStatus.Todo, created.status)
            assertEquals(listOf("GET", "POST"), sent.map { it.method })
            assertEquals(
                """{"title":"Sub","priority":"medium","parentId":"$PAP_1182",""" +
                    """"assigneeAgentId":"$ENGINEER_ID","allowDuplicate":true}""",
                sent[1].body,
            )
        }

    @Test
    fun `AD-014 a parent that does not exist or belongs to another company is NotFound with nothing posted`() =
        runTest {
            val globex = StubIssue("c0000009-0000-4000-8000-000000000009", "company-globex", "GLB-1", "Other")
            world.home.issueStore.issues += globex
            val missing = IssueId("c0000099-0000-4000-8000-000000000099")
            listOf(IssueId(globex.id), IssueId("bad"), missing).forEach { parent ->
                assertEquals(notFound, world.issues.create(world.acme, NewIssue("x", parentId = parent)))
            }
            assertTrue(sent.none { it.method == "POST" })
        }

    @Test
    fun `AD-013 an empty title with no description is refused by the server as a reason`() =
        runTest {
            val result = world.issues.create(world.acme, NewIssue("  "))
            assertEquals(failed(DomainError.Rejected("Validation error")), result)
        }

    @Test
    fun `AD-002 a failing write is sent once and never retried`() =
        runTest {
            val methods = mutableListOf<String>()
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val engine =
                MockEngine { request ->
                    methods += request.method.value
                    if (request.method.value == "GET") {
                        respond("""{"id":"$PAP_1182","companyId":"company-acme"}""", HttpStatusCode.OK, json)
                    } else {
                        respond(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError, json)
                    }
                }
            val failing = IssueGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            assertEquals(failed(DomainError.ServerError), failing.setStatus(world.acme, root, IssueStatus.Done))
            assertEquals(failed(DomainError.ServerError), failing.addComment(world.acme, root, "x", CommentOptions()))
            assertEquals(listOf("GET", "PATCH", "GET", "POST"), methods)
        }
}
