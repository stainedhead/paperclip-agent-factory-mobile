package com.stainedhead.paperclipmobile.infrastructure.api.contract

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What a seed call created. [reply] is kept for diagnostics only. */
internal class Seeded(
    val id: String,
    val identifier: String?,
    val reply: RawResponse,
)

/**
 * Creates the data the live reads and mutations work on, through the public API with the paired board key (so the key
 * is exercised too). A seed the server refuses returns null with the server's answer recorded in [LiveReport]; the
 * test that needed it then reports a gap instead of guessing (AD-050).
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

    fun approval(
        companyId: String,
        title: String,
    ): Seeded? =
        optional(
            "approval",
            "POST",
            "/api/companies/$companyId/approvals",
            """{"type":"request_board_approval","payload":{"title":"$title","summary":"$NOTE"}}""",
        )

    /** A direct agent, or the pending hire the company requires instead. Returns the agent id, or null. */
    fun agent(
        companyId: String,
        name: String,
    ): Seeded? {
        val body =
            """{"name":"$name","role":"engineer","adapterType":"process","adapterConfig":{"command":"true"},""" +
                """"budgetMonthlyCents":5000}"""
        val direct = raw.call("POST", "/api/companies/$companyId/agents", body, bearer = key)
        if (direct.status in CREATED) return seeded(direct)
        LiveReport.note("seed agent direct", direct.describe())
        val hire = raw.call("POST", "/api/companies/$companyId/agent-hires", body, bearer = key)
        if (hire.status !in CREATED) {
            LiveReport.note("seed agent hire", hire.describe())
            return null
        }
        val agent = hire.obj?.get("agent") as? JsonObject
        val id = (agent?.get("id") as? JsonPrimitive)?.contentOrNull ?: return null
        val approval = ((hire.obj?.get("approval") as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
        approval?.let { raw.call("POST", "/api/approvals/$it/approve", "{}", bearer = key) }
        return Seeded(id, null, hire)
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

    private fun optional(
        topic: String,
        method: String,
        path: String,
        body: String,
    ): Seeded? {
        val reply = raw.call(method, path, body, bearer = key)
        if (reply.status !in CREATED) {
            LiveReport.note("seed $topic", reply.describe())
            return null
        }
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
