package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.LogChunk
import com.stainedhead.paperclipmobile.domain.RunEvent
import com.stainedhead.paperclipmobile.domain.RunEventLevel
import com.stainedhead.paperclipmobile.domain.RunId
import com.stainedhead.paperclipmobile.domain.RunStatus
import com.stainedhead.paperclipmobile.domain.RunStream
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
import kotlin.time.Instant

private const val ENG = "e0000001-0000-4000-8000-000000000001"
private const val ISSUE = "c0000001-0000-4000-8000-000000000001"
private const val GLOBEX_ISSUE = "c0000002-0000-4000-8000-000000000002"
private const val LIVE = "a0000001-0000-4000-8000-000000000001"
private const val DONE = "a0000002-0000-4000-8000-000000000002"
private const val GLOBEX_RUN = "a0000003-0000-4000-8000-000000000003"
private const val CHUNK = 65_536
private const val MISSING = "a0000009-0000-4000-8000-000000000009"
private val acme = ServerFixtures.COMPANY_ACME
private val live = RunId(LIVE)

/** AD-013: runs through `heartbeat-runs`, `live-runs` and `issues/{id}/runs`. */
class RunGatewayAdapterTest {
    private val world =
        WorkWorld().also { w ->
            w.home.agents.agents += StubAgent(ENG, acme)
            w.home.issues.issues += StubIssue(ISSUE, acme, "PAP-1182", "Ship the billing migration")
            w.home.issues.issues += StubIssue(GLOBEX_ISSUE, ServerFixtures.COMPANY_GLOBEX, "GLO-1", "Other")
            val running = StubRun(LIVE, acme, ENG, "Engineer 1", ISSUE, "running")
            running.events += StubRunEvent(1, "lifecycle", "system", "info", "Run started")
            running.events += StubRunEvent(2, "adapter.invoke", "stdout", "warn", "Slow")
            running.events += StubRunEvent(3, "adapter.invoke", "stderr", "error", "Failed")
            running.log = "a".repeat(CHUNK + 4_000) + "héllo wörld ✓\n"
            val done = StubRun(DONE, acme, ENG, "Engineer 1", ISSUE, "succeeded")
            done.finishedAt = "2026-11-01T10:30:00.000Z"
            w.home.runs.runs += listOf(done, running)
            w.home.runs.runs += StubRun(GLOBEX_RUN, ServerFixtures.COMPANY_GLOBEX, ENG, "Other", null, "running")
        }

    private fun gateway(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) = RunGatewayAdapter(answeringClient(body, status, contentType), world.instances, world.secrets)

    @Test
    fun `AD-013 get reads one run with the stored key and maps it`() =
        runTest {
            val run = world.runs.get(world.acme, live).value()
            assertEquals(live, run.id)
            assertEquals(AgentId(ENG), run.agentId)
            assertEquals(IssueId(ISSUE), run.issueId)
            assertEquals(RunStatus.Running, run.status)
            assertEquals("assignment", run.invocationSource)
            assertEquals(Instant.parse("2026-11-01T10:00:00.000Z"), run.createdAt)
            assertEquals(null, run.finishedAt)
            val request = world.server.requests.single()
            assertEquals("/api/heartbeat-runs/$LIVE", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
        }

    @Test
    fun `AD-014 a run of another company is NotFound and an id that is not a uuid sends nothing`() =
        runTest {
            assertEquals(failed(DomainError.NotFound), world.runs.get(world.acme, RunId(GLOBEX_RUN)))
            val before = world.server.requests.size
            assertEquals(failed(DomainError.NotFound), world.runs.get(world.acme, RunId("no-such-run")))
            assertEquals(failed(DomainError.NotFound), world.runs.events(world.acme, RunId("x"), 0L))
            assertEquals(failed(DomainError.NotFound), world.runs.log(world.acme, RunId("x"), 0L))
            assertEquals(failed(DomainError.NotFound), world.runs.cancel(world.acme, RunId("x")))
            assertEquals(before, world.server.requests.size)
            assertEquals(failed(DomainError.NotFound), world.runs.get(world.acme, RunId(MISSING)))
        }

    @Test
    fun `AD-013 events are read after the sequence number in order with the run checked first`() =
        runTest {
            val events = world.runs.events(world.acme, live, 1L).value()
            assertEquals(
                listOf(
                    RunEvent(2, "adapter.invoke", RunStream.Stdout, RunEventLevel.Warn, "Slow", events[0].createdAt),
                    RunEvent(3, "adapter.invoke", RunStream.Stderr, RunEventLevel.Error, "Failed", events[1].createdAt),
                ),
                events,
            )
            val sent = world.server.requests
            assertEquals(listOf("/api/heartbeat-runs/$LIVE", "/api/heartbeat-runs/$LIVE/events"), sent.map { it.path })
            assertEquals("afterSeq=1&limit=500", sent[1].query)
        }

    @Test
    fun `AD-013 an event with an unknown stream or level reads as none and one without a time is Malformed`() =
        runTest {
            val ok =
                """[{"seq":7,"eventType":"x","stream":"pty","level":"debug","message":null,""" +
                    """"createdAt":"2026-11-01T10:00:00.000Z"}]"""
            val event = scriptedEvents(ok).events(world.acme, live, 0L).value().single()
            assertEquals(7L, event.seq)
            assertEquals(null, event.stream)
            assertEquals(null, event.level)
            assertEquals(null, event.message)
            listOf(
                """[{"seq":7,"eventType":"x"}]""",
                """[{"eventType":"x","createdAt":"2026-11-01T10:00:00.000Z"}]""",
                """[{"seq":"7","eventType":"x","createdAt":"2026-11-01T10:00:00.000Z"}]""",
                """{"seq":1}""",
                "<html></html>",
                "",
            ).forEach { body ->
                assertEquals(failed(DomainError.Malformed), scriptedEvents(body).events(world.acme, live, 0L), body)
            }
            assertEquals(emptyList(), scriptedEvents("[]").events(world.acme, live, 0L).value())
        }

    /** The run read answers from the stub; the second read answers [body]. */
    private fun scriptedEvents(body: String): RunGatewayAdapter {
        var reads = 0
        val engine =
            MockEngine {
                reads += 1
                val content =
                    if (reads == 1) {
                        RunFixtures.run(
                            world.home.runs.runs
                                .first { it.id == LIVE },
                        )
                    } else {
                        body
                    }
                val type = if ("html" in content) "text/html" else "application/json"
                respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, type))
            }
        return RunGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
    }

    private suspend fun tail(): Pair<String, List<LogChunk>> {
        var offset = 0L
        val chunks = mutableListOf<LogChunk>()
        var text = ""
        while (true) {
            val chunk = world.runs.log(world.acme, live, offset).value()
            chunks += chunk
            text += chunk.content
            offset = chunk.nextOffset ?: break
        }
        return text to chunks
    }

    @Test
    fun `AD-013 the log is read in byte offsets and the last short chunk still moves the offset forward`() =
        runTest {
            val (text, chunks) = tail()
            val log =
                world.home.runs.runs
                    .first { it.id == LIVE }
                    .log!!
            val bytes = log.encodeToByteArray().size
            assertEquals(log, text)
            assertEquals(3, chunks.size)
            assertEquals(CHUNK.toLong(), chunks[0].nextOffset)
            // The server sent no nextOffset for the short chunk; its end is the byte count, not the character count.
            assertTrue(log.length < bytes)
            assertEquals(bytes.toLong(), chunks[1].nextOffset)
            assertEquals(LogChunk("", null), chunks[2])
            val requests = world.server.requests.filter { it.path.endsWith("/log") }
            assertEquals("offset=0&limitBytes=$CHUNK", requests[0].query)
            assertEquals("offset=$CHUNK&limitBytes=$CHUNK", requests[1].query)
        }

    @Test
    fun `AD-013 a run that has no log yet reads as an empty chunk and an unknown run stays NotFound`() =
        runTest {
            val queued = StubRun("a0000004-0000-4000-8000-000000000004", acme, ENG, "Engineer 1", ISSUE, "queued")
            world.home.runs.runs += queued
            assertEquals(LogChunk("", null), world.runs.log(world.acme, RunId(queued.id), 0L).value())
            assertEquals(
                failed(DomainError.NotFound),
                world.runs.log(world.acme, RunId(MISSING), 0L),
            )
        }

    @Test
    fun `AD-003 a log reply that is not a log is Malformed`() =
        runTest {
            listOf("{}", """{"content":5}""", "[]", "<html></html>", "").forEach { body ->
                assertEquals(failed(DomainError.Malformed), scriptedLog(body).log(world.acme, live, 0L), body)
            }
            val chunk = scriptedLog("""{"content":"ok","nextOffset":9}""").log(world.acme, live, 3L).value()
            assertEquals(LogChunk("ok", 9L), chunk)
            assertEquals(LogChunk("ok", 5L), scriptedLog("""{"content":"ok"}""").log(world.acme, live, 3L).value())
        }

    private fun scriptedLog(body: String): RunGatewayAdapter = scriptedEvents(body)

    @Test
    fun `AD-013 cancel reads the run and posts once and a stopped run is a Conflict without a mutation`() =
        runTest {
            val cancelled = world.runs.cancel(world.acme, live).value()
            assertEquals(RunStatus.Cancelled, cancelled.status)
            assertEquals(listOf("GET", "POST"), world.server.requests.map { it.method })
            assertEquals(
                "/api/heartbeat-runs/$LIVE/cancel",
                world.server.requests
                    .last()
                    .path,
            )
            world.server.requests.clear()
            assertEquals(failed(DomainError.Conflict), world.runs.cancel(world.acme, live))
            assertEquals(failed(DomainError.Conflict), world.runs.cancel(world.acme, RunId(DONE)))
            assertTrue(world.server.requests.none { it.method == "POST" })
        }

    @Test
    fun `AD-013 a cancel that comes back as another ending or as null is not trusted`() =
        runTest {
            val running =
                RunFixtures.run(
                    world.home.runs.runs
                        .first { it.id == LIVE },
                )
            val finished = running.replace("\"status\":\"running\"", "\"status\":\"succeeded\"")
            assertEquals(failed(DomainError.Conflict), scriptedCancel(running, finished).cancel(world.acme, live))
            assertEquals(failed(DomainError.NotFound), scriptedCancel(running, "null").cancel(world.acme, live))
            val foreign = running.replace(acme, ServerFixtures.COMPANY_GLOBEX)
            assertEquals(failed(DomainError.NotFound), scriptedCancel(running, foreign).cancel(world.acme, live))
            val settling = scriptedCancel(running, running).cancel(world.acme, live).value()
            assertEquals(RunStatus.Running, settling.status)
        }

    private fun scriptedCancel(
        read: String,
        post: String,
    ): RunGatewayAdapter {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val engine =
            MockEngine { request ->
                respond(if (request.method.value == "GET") read else post, HttpStatusCode.OK, json)
            }
        return RunGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
    }

    @Test
    fun `AD-002 a failing cancel is sent once and never retried`() =
        runTest {
            val methods = mutableListOf<String>()
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val engine =
                MockEngine { request ->
                    methods += request.method.value
                    if (request.method.value == "GET") {
                        respond(
                            RunFixtures.run(
                                world.home.runs.runs
                                    .first { it.id == LIVE },
                            ),
                            HttpStatusCode.OK,
                            json,
                        )
                    } else {
                        respond(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError, json)
                    }
                }
            val failing = RunGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            assertEquals(failed(DomainError.ServerError), failing.cancel(world.acme, live))
            assertEquals(listOf("GET", "POST"), methods)
        }

    @Test
    fun `AD-013 runs of an issue check the issue first and carry its id`() =
        runTest {
            val runs = world.runs.forIssue(world.acme, IssueId(ISSUE)).value()
            assertEquals(listOf(RunId(LIVE), RunId(DONE)), runs.map { it.id })
            assertTrue(runs.all { it.issueId == IssueId(ISSUE) && it.agentName == null })
            assertEquals(listOf("/api/issues/$ISSUE", "/api/issues/$ISSUE/runs"), world.server.requests.map { it.path })
            assertEquals(failed(DomainError.NotFound), world.runs.forIssue(world.acme, IssueId(GLOBEX_ISSUE)))
            assertEquals(failed(DomainError.NotFound), world.runs.forIssue(world.acme, IssueId("no-such-issue")))
        }

    @Test
    fun `AD-013 runs of an agent are listed newest first with a limit`() =
        runTest {
            val runs = world.runs.forAgent(world.acme, AgentId(ENG)).value()
            assertEquals(listOf(RunId(LIVE), RunId(DONE)), runs.map { it.id })
            val request = world.server.requests.single()
            assertEquals("/api/companies/company-acme/heartbeat-runs", request.path)
            assertEquals("agentId=$ENG&limit=50", request.query)
        }

    @Test
    fun `AD-013 live runs name the agent and the issue`() =
        runTest {
            val runs = world.runs.live(world.acme).value()
            assertEquals(listOf(RunId(LIVE)), runs.map { it.id })
            assertEquals("Engineer 1", runs.single().agentName)
            assertEquals(IssueId(ISSUE), runs.single().issueId)
            assertEquals(
                "/api/companies/company-acme/live-runs",
                world.server.requests
                    .single()
                    .path,
            )
            assertEquals(
                "",
                world.server.requests
                    .single()
                    .query,
            )
        }

    @Test
    fun `AD-003 lists that are not run lists are Malformed`() =
        runTest {
            val noAgent = """[{"id":"$LIVE","status":"running","createdAt":"2026-11-01T10:00:00.000Z"}]"""
            val noTime = """[{"id":"$LIVE","agentId":"$ENG","status":"running"}]"""
            (NOT_THE_EXPECTED_SHAPE + "{}" + noAgent + noTime + "[1]").forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                val adapter = gateway(body, contentType = type)
                assertEquals(failed(DomainError.Malformed), adapter.live(world.acme), body)
                assertEquals(failed(DomainError.Malformed), adapter.forAgent(world.acme, AgentId(ENG)), body)
            }
            assertEquals(failed(DomainError.Malformed), gateway("{}").get(world.acme, live))
            assertEquals(emptyList(), gateway("[]").live(world.acme).value())
            val unknown =
                """{"id":"$LIVE","companyId":"$acme","agentId":"$ENG","status":"zombie",""" +
                    """"createdAt":"2026-11-01T10:00:00Z"}"""
            assertEquals(RunStatus.Unknown, gateway(unknown).get(world.acme, live).value().status)
        }

    @Test
    fun `AD-013 a viewer reads runs but may not cancel`() =
        runTest {
            val globex = ServerFixtures.COMPANY_GLOBEX
            val run = StubRun("a0000005-0000-4000-8000-000000000005", globex, ENG, "G", null, "running")
            world.home.runs.runs += run
            world.runs.get(world.globex, RunId(run.id)).value()
            world.runs.live(world.globex).value()
            assertEquals(failed(DomainError.Forbidden), world.runs.cancel(world.globex, RunId(run.id)))
            assertEquals(failed(DomainError.Forbidden), world.runs.live(world.elsewhere))
        }
}
