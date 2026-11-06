package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.AskUserQuestions
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionId
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.ItemVerdict
import com.stainedhead.paperclipmobile.domain.QuestionAnswer
import com.stainedhead.paperclipmobile.domain.RequestCheckboxConfirmation
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.RequestItemVerdicts
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.SuggestTasks
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.domain.Verdict
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val acme = ServerFixtures.COMPANY_ACME
private val issue = IssueId(PAP_1182)
private val notFound = failed(DomainError.NotFound)
private const val TASKS = "d2000000-0000-4000-8000-000000000001"
private const val QUESTIONS = "d2000000-0000-4000-8000-000000000002"
private const val CHECKBOX = "d2000000-0000-4000-8000-000000000003"
private const val VERDICTS = "d2000000-0000-4000-8000-000000000004"
private const val CONNECTION = "d2000000-0000-4000-8000-000000000005"
private const val TOOL = "d2000000-0000-4000-8000-000000000006"
private const val FUTURE = "d2000000-0000-4000-8000-000000000007"

private fun stub(
    id: String,
    kind: String,
    payload: JsonObject,
) = StubInteraction(id, acme, PAP_1182, kind, payload)

private fun id(value: String) = InteractionId(value)

private fun confirmation(
    id: String,
    prompt: String = "Go?",
    reason: Boolean = false,
) = stub(id, "request_confirmation", InteractionFixtures.confirmation(prompt, reason))

/** AD-013: interactions of an issue thread: the list in five supported kinds, the rest unsupported, and the answers. */
class InteractionGatewayAdapterTest {
    private val world = WorkWorld().also(::seedIssues)

    private val rows get() = world.home.interactions.interactions
    private val sent get() = world.server.requests

    private fun seedAll() {
        rows += confirmation(CONFIRM_ONE, "Switch traffic?", true)
        rows += stub(TASKS, "suggest_tasks", InteractionFixtures.suggestTasks("t1", "t2"))
        rows += stub(QUESTIONS, "ask_user_questions", InteractionFixtures.questions())
        rows += stub(CHECKBOX, "request_checkbox_confirmation", InteractionFixtures.checkbox())
        rows += stub(VERDICTS, "request_item_verdicts", InteractionFixtures.verdicts())
        rows += stub(CONNECTION, "connection_intent", InteractionFixtures.connectionIntent())
        val tool = InteractionFixtures.confirmation("Run it?", extra = InteractionFixtures.toolAction)
        rows += stub(TOOL, "request_confirmation", tool)
        rows += stub(FUTURE, "request_fancy_thing", InteractionFixtures.suggestTasks("x"))
    }

    private fun gateway(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) = InteractionGatewayAdapter(answeringClient(body, status, contentType), world.instances, world.secrets)

    private suspend fun listed(): Map<String, Interaction> =
        world.interactions
            .list(world.acme, issue)
            .value()
            .associateBy { it.meta.id.value }

    @Test
    fun `AD-013 list reads the thread of the issue with the stored key`() =
        runTest {
            seedAll()
            world.interactions.list(world.acme, issue).value()
            val request = sent.single()
            assertEquals("GET", request.method)
            assertEquals("/api/issues/$PAP_1182/interactions", request.path)
            assertEquals("Bearer ${TestData.TOKEN}", request.authorization)
        }

    @Test
    fun `AD-013 a confirmation maps its prompt labels reason rule and details`() =
        runTest {
            seedAll()
            val card = assertIs<RequestConfirmation>(listed().getValue(CONFIRM_ONE))
            assertEquals("Switch traffic?", card.prompt)
            assertEquals("Switch traffic", card.acceptLabel)
            assertEquals("Not yet", card.rejectLabel)
            assertTrue(card.rejectRequiresReason)
            assertEquals("Details **here**", card.detailsMarkdown)
            assertEquals(InteractionStatus.Pending, card.meta.status)
            assertEquals(Instant.parse("2026-11-01T09:00:00.000Z"), card.meta.createdAt)
        }

    @Test
    fun `AD-013 suggested tasks questions checkboxes and verdicts map the fields a form renders`() =
        runTest {
            seedAll()
            val all = listed()
            val tasks = assertIs<SuggestTasks>(all.getValue(TASKS))
            assertEquals(listOf("t1", "t2"), tasks.tasks.map { it.clientKey })
            assertEquals("Do t1", tasks.tasks.first().description)
            val questions = assertIs<AskUserQuestions>(all.getValue(QUESTIONS))
            val question = questions.questions.single()
            assertEquals("Which region first?", question.prompt)
            assertEquals(false, question.multi)
            assertEquals(true, question.required)
            assertEquals(true, question.allowOther)
            assertEquals(listOf("eu", "us"), question.options.map { it.id })
            assertEquals("Send answers", questions.submitLabel)
            val checkbox = assertIs<RequestCheckboxConfirmation>(all.getValue(CHECKBOX))
            assertEquals(setOf("a"), checkbox.defaultSelected)
            assertEquals(1, checkbox.minSelected)
            assertEquals(2, checkbox.maxSelected)
            val verdicts = assertIs<RequestItemVerdicts>(all.getValue(VERDICTS))
            assertEquals(listOf("f1", "f2"), verdicts.items.map { it.id })
            assertEquals(setOf(Verdict.Approve, Verdict.Reject, Verdict.Defer), verdicts.verdicts)
            assertEquals(setOf(Verdict.Reject), verdicts.requireReasonOn)
        }

    @Test
    fun `AD-013 a question that is not required and has no allowOther reads with the server defaults`() =
        runTest {
            val multi = InteractionFixtures.questions(required = null, mode = "multi")
            val closed = InteractionFixtures.questions(required = false, allowOther = false)
            rows += stub(QUESTIONS, "ask_user_questions", multi)
            rows += stub(CHECKBOX, "ask_user_questions", closed)
            val all = listed()
            val first = assertIs<AskUserQuestions>(all.getValue(QUESTIONS)).questions.single()
            assertEquals(false, first.required)
            assertEquals(true, first.multi)
            assertEquals(true, first.allowOther)
            assertEquals(false, assertIs<AskUserQuestions>(all.getValue(CHECKBOX)).questions.single().allowOther)
        }

    @Test
    fun `AD-013 connection intents and unknown kinds and tool confirmations are Unsupported and keep their kind`() =
        runTest {
            seedAll()
            val all = listed()
            val connection = assertIs<UnsupportedInteraction>(all.getValue(CONNECTION))
            assertEquals(InteractionKind.ConnectionIntent, connection.kind)
            val future = assertIs<UnsupportedInteraction>(all.getValue(FUTURE))
            assertEquals(InteractionKind.Unsupported("request_fancy_thing"), future.kind)
            val tool = assertIs<UnsupportedInteraction>(all.getValue(TOOL))
            assertEquals(InteractionKind.RequestConfirmation, tool.kind)
            assertEquals(false, tool.isSupported)
        }

    @Test
    fun `AD-013 a supported kind with a payload that cannot be read becomes Unsupported and the thread still loads`() =
        runTest {
            val broken = InteractionFixtures.suggestTasks("t1").toString().replace("\"tasks\"", "\"jobs\"")
            val payload = Json.parseToJsonElement(broken) as JsonObject
            rows += stub(TASKS, "suggest_tasks", payload)
            rows += stub(QUESTIONS, "ask_user_questions", InteractionFixtures.questions(mode = "triple"))
            rows += confirmation(CONFIRM_ONE, "OK?")
            val all = listed()
            assertIs<UnsupportedInteraction>(all.getValue(TASKS))
            assertIs<UnsupportedInteraction>(all.getValue(QUESTIONS))
            assertIs<RequestConfirmation>(all.getValue(CONFIRM_ONE))
        }

    @Test
    fun `AD-013 the audience and the acceptance blocker come from the row and an unknown status is Unknown`() =
        runTest {
            val row = confirmation(CONFIRM_ONE, "Go?")
            row.addresseeUserId = "user-1"
            row.effective = "human_only"
            row.source = "company_cap"
            row.blocker = true
            row.status = "pondering"
            row.title = "Switch"
            rows += row
            val meta = listed().getValue(CONFIRM_ONE).meta
            assertEquals(ResolverPolicy.HumanOnly, meta.audience.effectivePolicy)
            assertEquals("user-1", meta.audience.addresseeUserId)
            assertTrue(meta.acceptanceBlocked)
            assertEquals(InteractionStatus.Unknown, meta.status)
            assertEquals("Switch", meta.title)
        }

    @Test
    fun `AD-013 a pending interaction of a closed issue reads as expired`() =
        runTest {
            rows += confirmation(CONFIRM_ONE, "Go?")
            world.home.issueStore.issues
                .first { it.id == PAP_1182 }
                .status = "done"
            assertEquals(InteractionStatus.Expired, listed().getValue(CONFIRM_ONE).meta.status)
        }

    @Test
    fun `AD-003 lists that are not interaction lists are Malformed and an empty one is empty`() =
        runTest {
            val noTime =
                """[{"id":"$CONFIRM_ONE","companyId":"$acme","issueId":"$PAP_1182","kind":"request_confirmation"}]"""
            (NOT_THE_EXPECTED_SHAPE + "{}" + "[1]" + noTime).forEach { body ->
                val type = if ("html" in body) "text/html" else "application/json"
                val reply = gateway(body, contentType = type).list(world.acme, issue)
                assertEquals(failed(DomainError.Malformed), reply, body)
            }
            val empty = InteractionGatewayAdapter(answeringIssueClient("[]"), world.instances, world.secrets)
            assertEquals(emptyList(), empty.list(world.acme, issue).value())
        }

    @Test
    fun `AD-014 rows of another company and a bad issue id are not trusted`() =
        runTest {
            val row = InteractionFixtures.row(confirmation(CONFIRM_ONE, "Go?"))
            assertEquals(notFound, gateway(row.replace(acme, "company-globex").let { "[$it]" }).list(world.acme, issue))
            assertEquals(notFound, world.interactions.list(world.acme, IssueId("bad")))
            val bad = IssueId("bad")
            assertEquals(
                notFound,
                world.interactions.answer(world.acme, bad, id(CONFIRM_ONE), InteractionAnswer.Accept),
            )
            assertTrue(sent.isEmpty())
        }

    private suspend fun answer(
        interaction: String,
        answer: InteractionAnswer,
    ): DomainResult<Interaction> = world.interactions.answer(world.acme, issue, id(interaction), answer)

    @Test
    fun `AD-013 accept reads the thread first then posts an empty body once and returns the settled card`() =
        runTest {
            seedAll()
            val card = answer(CONFIRM_ONE, InteractionAnswer.Accept).value()
            assertEquals(InteractionStatus.Accepted, card.meta.status)
            assertEquals(listOf("GET", "POST"), sent.map { it.method })
            assertEquals("/api/issues/$PAP_1182/interactions/$CONFIRM_ONE/accept", sent[1].path)
            assertEquals("{}", sent[1].body)
        }

    @Test
    fun `AD-013 each answer goes to its own endpoint with the body the server wants`() =
        runTest {
            seedAll()
            answer(TASKS, InteractionAnswer.AcceptTasks(linkedSetOf("t2", "t1"))).value()
            assertEquals("""{"selectedClientKeys":["t2","t1"]}""", sent.last().body)
            answer(CHECKBOX, InteractionAnswer.AcceptOptions(linkedSetOf("a", "c"))).value()
            assertEquals("""{"selectedOptionIds":["a","c"]}""", sent.last().body)
            val answers = listOf(QuestionAnswer("q1", listOf("eu"), "and more"))
            answer(QUESTIONS, InteractionAnswer.Respond(answers)).value()
            assertEquals("/api/issues/$PAP_1182/interactions/$QUESTIONS/respond", sent.last().path)
            val respondBody = """{"answers":[{"questionId":"q1","optionIds":["eu"],"otherText":"and more"}]}"""
            assertEquals(respondBody, sent.last().body)
            val verdicts = listOf(ItemVerdict("f1", Verdict.Approve), ItemVerdict("f2", Verdict.Reject, "Wrong file"))
            answer(VERDICTS, InteractionAnswer.SubmitVerdicts(verdicts)).value()
            assertEquals("/api/issues/$PAP_1182/interactions/$VERDICTS/verdicts", sent.last().path)
            assertEquals(
                """{"verdicts":[{"id":"f1","verdict":"approve"},""" +
                    """{"id":"f2","verdict":"reject","reason":"Wrong file"}]}""",
                sent.last().body,
            )
        }

    @Test
    fun `AD-013 reject and skip send the reason only when there is one`() =
        runTest {
            rows += confirmation(CONFIRM_ONE, "Go?")
            rows += confirmation(CONFIRM_TWO, "Go?")
            val rejected = answer(CONFIRM_ONE, InteractionAnswer.Reject("Not now")).value()
            assertEquals(InteractionStatus.Rejected, rejected.meta.status)
            assertEquals("""{"reason":"Not now"}""", sent.last().body)
            val skipped = answer(CONFIRM_TWO, InteractionAnswer.Skip("  ")).value()
            assertEquals(InteractionStatus.Cancelled, skipped.meta.status)
            assertEquals("/api/issues/$PAP_1182/interactions/$CONFIRM_TWO/skip", sent.last().path)
            assertEquals("{}", sent.last().body)
        }

    @Test
    fun `AD-013 an unsupported interaction is never answered and nothing is posted`() =
        runTest {
            seedAll()
            listOf(CONNECTION, FUTURE, TOOL).forEach { target ->
                val refused = answer(target, InteractionAnswer.Accept) as DomainResult.Failure
                assertIs<DomainError.Rejected>(refused.error)
                val skipped = answer(target, InteractionAnswer.Skip(null)) as DomainResult.Failure
                assertIs<DomainError.Rejected>(skipped.error)
            }
            assertTrue(sent.none { it.method == "POST" })
        }

    @Test
    fun `AD-013 an answer of the wrong kind or an empty task selection is refused here and never sent`() =
        runTest {
            seedAll()
            val wrong =
                listOf(
                    TASKS to InteractionAnswer.Accept,
                    TASKS to InteractionAnswer.AcceptTasks(emptySet()),
                    CONFIRM_ONE to InteractionAnswer.Respond(emptyList()),
                    CONFIRM_ONE to InteractionAnswer.AcceptTasks(setOf("t1")),
                    QUESTIONS to InteractionAnswer.Accept,
                    CHECKBOX to InteractionAnswer.Accept,
                    VERDICTS to InteractionAnswer.Reject("no"),
                )
            wrong.forEach { (target, given) ->
                val refused = answer(target, given) as DomainResult.Failure
                assertIs<DomainError.Rejected>(refused.error, "$target $given")
            }
            assertTrue(sent.none { it.method == "POST" })
        }

    @Test
    fun `AD-013 a settled interaction is a Conflict and an unknown one is NotFound without a mutation`() =
        runTest {
            seedAll()
            answer(CONFIRM_ONE, InteractionAnswer.Accept).value()
            sent.clear()
            assertEquals(failed(DomainError.Conflict), answer(CONFIRM_ONE, InteractionAnswer.Accept))
            assertEquals(notFound, answer("d9000000-0000-4000-8000-000000000009", InteractionAnswer.Accept))
            assertTrue(sent.none { it.method == "POST" })
        }

    @Test
    fun `AD-013 the reasons of the server arrive when the person's answer is not acceptable`() =
        runTest {
            seedAll()
            assertEquals(
                failed(DomainError.Rejected("A decline reason is required for this confirmation")),
                answer(CONFIRM_ONE, InteractionAnswer.Reject(null)),
            )
            assertEquals(
                failed(DomainError.Rejected("Unknown questionId: q9")),
                answer(QUESTIONS, InteractionAnswer.Respond(listOf(QuestionAnswer("q9", listOf("eu"))))),
            )
            assertEquals(
                failed(DomainError.Rejected("Question q1 requires an answer")),
                answer(QUESTIONS, InteractionAnswer.Respond(emptyList())),
            )
            assertEquals(
                failed(DomainError.Rejected("A reason is required when verdict is reject")),
                answer(VERDICTS, InteractionAnswer.SubmitVerdicts(listOf(ItemVerdict("f1", Verdict.Reject)))),
            )
        }

    @Test
    fun `AD-013 a card addressed to someone else and a viewer are Forbidden`() =
        runTest {
            rows += confirmation(CONFIRM_ONE, "Go?").also { it.addresseeUserId = "user-9" }
            assertEquals(failed(DomainError.Forbidden), answer(CONFIRM_ONE, InteractionAnswer.Accept))
            val g = StubIssue("c0000008-0000-4000-8000-000000000008", "company-globex", "GLB-2", "Viewer's")
            world.home.issueStore.issues += g
            world.home.interactions.interactions +=
                StubInteraction(CONFIRM_TWO, "company-globex", g.id, "request_confirmation", confirmation("x").payload)
            val denied =
                world.interactions.answer(
                    world.globex,
                    IssueId(g.id),
                    id(CONFIRM_TWO),
                    InteractionAnswer.Accept,
                )
            assertEquals(failed(DomainError.Forbidden), denied)
            world.interactions.list(world.globex, IssueId(g.id)).value()
        }

    @Test
    fun `AD-002 a failing answer is sent once and never retried`() =
        runTest {
            val methods = mutableListOf<String>()
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val row = InteractionFixtures.row(confirmation(CONFIRM_ONE, "Go?"))
            val engine =
                MockEngine { request ->
                    methods += request.method.value
                    if (request.method.value == "GET") {
                        respond("[$row]", HttpStatusCode.OK, json)
                    } else {
                        respond(ServerFixtures.error("boom"), HttpStatusCode.InternalServerError, json)
                    }
                }
            val failing = InteractionGatewayAdapter(PaperclipHttpClient(engine, false), world.instances, world.secrets)
            val reply = failing.answer(world.acme, issue, id(CONFIRM_ONE), InteractionAnswer.Accept)
            assertEquals(failed(DomainError.ServerError), reply)
            assertEquals(listOf("GET", "POST"), methods)
        }

    @Test
    fun `AD-014 an answer reply that names another company or is not an interaction is not trusted`() =
        runTest {
            val row = InteractionFixtures.row(confirmation(CONFIRM_ONE, "Go?"))
            val json = headersOf(HttpHeaders.ContentType, "application/json")

            fun scripted(post: String) =
                InteractionGatewayAdapter(
                    PaperclipHttpClient(
                        MockEngine { request ->
                            respond(if (request.method.value == "GET") "[$row]" else post, HttpStatusCode.OK, json)
                        },
                        false,
                    ),
                    world.instances,
                    world.secrets,
                )
            val foreign = scripted(row.replace(acme, "company-globex"))
            assertEquals(notFound, foreign.answer(world.acme, issue, id(CONFIRM_ONE), InteractionAnswer.Accept))
            val empty = scripted("{}")
            val reply = empty.answer(world.acme, issue, id(CONFIRM_ONE), InteractionAnswer.Accept)
            assertEquals(failed(DomainError.Malformed), reply)
        }
}
