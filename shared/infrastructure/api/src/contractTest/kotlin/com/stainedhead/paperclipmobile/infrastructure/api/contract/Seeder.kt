package com.stainedhead.paperclipmobile.infrastructure.api.contract

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** What a seed call created. [reply] is kept for diagnostics only. */
internal class Seeded(
    val id: String,
    val identifier: String?,
    val reply: RawResponse,
)

/**
 * Creates the data the live reads and mutations work on, through the public API with the paired board key (so the key
 * is exercised too). A seed the server refuses fails the test that needed it, with the server's
 * answer in the message (F-C-3): a refused seed is drift, not a reason to skip operations.
 */
internal class Seeder(
    private val raw: RawServer,
    private val key: String,
) {
    fun company(name: String): Seeded =
        required("POST", "/api/companies", """{"name":"$name","budgetMonthlyCents":100000}""")

    fun issue(
        companyId: String,
        title: String,
        status: String = "todo",
    ): Seeded =
        required(
            "POST",
            "/api/companies/$companyId/issues",
            """{"title":"$title","description":"$NOTE","priority":"medium","status":"$status"}""",
        )

    /** A board approval, optionally linked to issues (the server takes `issueIds` next to the approval fields). */
    fun approval(
        companyId: String,
        title: String,
        issueIds: List<String> = emptyList(),
    ): Seeded {
        val links = issueIds.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        return required(
            "POST",
            "/api/companies/$companyId/approvals",
            """{"type":"request_board_approval","payload":{"title":"$title","summary":"$NOTE"},"issueIds":$links}""",
        )
    }

    /** A direct agent running [command] with [args] on the server, or the pending hire the company requires. */
    fun agent(
        companyId: String,
        name: String,
        command: String = "true",
        args: List<String> = emptyList(),
    ): Seeded {
        val argList = args.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        val body =
            """{"name":"$name","role":"engineer","adapterType":"process","adapterConfig":""" +
                """{"command":"$command","args":$argList},"budgetMonthlyCents":5000}"""
        val direct = raw.call("POST", "/api/companies/$companyId/agents", body, bearer = key)
        if (direct.status in CREATED) return seeded(direct)
        LiveReport.note("seed agent direct", direct.describe())
        val hire = raw.call("POST", "/api/companies/$companyId/agent-hires", body, bearer = key)
        check(hire.status in CREATED) { "Seeding agent $name: direct ${direct.describe()}; hire ${hire.describe()}" }
        val agent = hire.obj?.get("agent") as? JsonObject
        val id = checkNotNull((agent?.get("id") as? JsonPrimitive)?.contentOrNull) { "The hire has no agent id" }
        val approval = ((hire.obj?.get("approval") as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
        approval?.let { raw.call("POST", "/api/approvals/$it/approve", "{}", bearer = key) }
        return Seeded(id, null, hire)
    }

    /** A cost event of [cents] a minute ago (the window-spend read stops at "now"), reported by the board key. */
    fun costEvent(
        companyId: String,
        agentId: String,
        cents: Int,
    ): RawResponse {
        val occurred = Clock.System.now() - 1.minutes
        val reply =
            raw.call(
                "POST",
                "/api/companies/$companyId/cost-events",
                """{"agentId":"$agentId","provider":"contract-provider","model":"contract-model",""" +
                    """"costCents":$cents,"billingType":"metered_api","inputTokens":10,"outputTokens":5,""" +
                    """"occurredAt":"$occurred"}""",
                bearer = key,
            )
        check(reply.status in CREATED) { "POST cost-events answered ${reply.describe()}" }
        return reply
    }

    /** A pending single-choice question on the issue thread. Returns the interaction id. */
    fun question(issueId: String): String {
        val body =
            """{"kind":"ask_user_questions","title":"Contract question","payload":{"version":1,"questions":[""" +
                """{"id":"go","prompt":"Proceed with the contract run?","selectionMode":"single","required":true,""" +
                """"options":[{"id":"yes","label":"Yes"},{"id":"no","label":"No"}]}]}}"""
        return required("POST", "/api/issues/$issueId/interactions", body).id
    }

    /** A small text attachment. Returns the attachment id. */
    fun attachment(
        companyId: String,
        issueId: String,
        fileName: String,
        content: String,
    ): String {
        val reply =
            raw.upload(
                "/api/companies/$companyId/issues/$issueId/attachments",
                fileName,
                "text/plain",
                content.toByteArray(),
                key,
            )
        check(reply.status in CREATED) { "POST attachments answered ${reply.describe()}" }
        return checkNotNull(reply.string("id")) { "The attachment reply has no id: ${reply.describe()}" }
    }

    /** A comment on an approval, through the raw client. */
    fun approvalComment(
        approvalId: String,
        body: String,
    ) {
        val reply = raw.call("POST", "/api/approvals/$approvalId/comments", """{"body":"$body"}""", bearer = key)
        check(reply.status in CREATED) { "POST approval comments answered ${reply.describe()}" }
    }

    private fun required(
        method: String,
        path: String,
        body: String,
    ): Seeded {
        val reply = raw.call(method, path, body, bearer = key)
        check(reply.status in CREATED) { "$method $path answered ${reply.describe()}" }
        return seeded(reply)
    }

    private fun seeded(reply: RawResponse): Seeded =
        Seeded(
            checkNotNull(reply.string("id")) {
                "The reply has no id: ${reply.describe()}"
            },
            reply.string("identifier"),
            reply,
        )

    private companion object {
        const val NOTE = "Created by the contract test"
        val CREATED = 200..201
    }
}
