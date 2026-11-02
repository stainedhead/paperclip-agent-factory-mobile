package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Instant

/** What a work handler answers: a status and a JSON body, or no body (204). */
internal class StubReply(
    val status: Int,
    val body: String?,
) {
    companion object {
        fun ok(body: String) = StubReply(STATUS_OK, body)

        fun created(body: String) = StubReply(STATUS_CREATED, body)

        fun noContent() = StubReply(STATUS_NO_CONTENT, null)

        fun error(
            status: Int,
            message: String,
        ) = StubReply(status, ServerFixtures.error(message))

        fun notFound(message: String) = error(STATUS_NOT_FOUND, message)

        fun forbidden(message: String) = error(STATUS_FORBIDDEN, message)

        /** The generic Zod rejection of the server's `validate` middleware (400). */
        fun invalid() = error(STATUS_BAD_REQUEST, "Validation error")

        /** `unprocessable` of the server's `errors.ts` (422). */
        fun unprocessable(message: String) = error(STATUS_UNPROCESSABLE, message)

        const val STATUS_OK = 200
        const val STATUS_CREATED = 201
        const val STATUS_NO_CONTENT = 204
        const val STATUS_BAD_REQUEST = 400
        const val STATUS_FORBIDDEN = 403
        const val STATUS_NOT_FOUND = 404
        const val STATUS_CONFLICT = 409
        const val STATUS_UNPROCESSABLE = 422
    }
}

/** The company roles of the key that made a request. Null roles means owner everywhere (an instance admin). */
internal class StubAccess(
    private val roles: Map<String, String>?,
) {
    /** The role in [company], or null when the key has no membership there (`authz.ts:104-106`). */
    fun roleIn(company: String): String? = if (roles == null) OWNER else roles[company]

    companion object {
        const val OWNER = "owner"
        const val VIEWER = "viewer"
    }
}

/** One request after the `/api` prefix: percent-decoded path segments, so keys with colons arrive as written. */
internal class StubRequest(
    val method: HttpMethod,
    path: String,
    val query: Parameters,
    val body: String,
    val access: StubAccess,
) {
    val segments: List<String> = path.split('/').filter { it.isNotEmpty() }.map { it.decodeURLPart() }
    val isRead: Boolean get() = method == HttpMethod.Get

    /** The JSON object in the body, or null when it is not one. */
    fun jsonBody(): JsonObject? =
        try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        }

    /** The company role check of `assertCompanyAccess` (`authz.ts:95-118`); a reply means "refused". */
    fun companyRefusal(company: String): StubReply? {
        val role = access.roleIn(company) ?: return StubReply.forbidden("User does not have access to this company")
        return if (!isRead && role == StubAccess.VIEWER) StubReply.forbidden("Viewer access is read-only") else null
    }

    /**
     * The check of `getAccessibleResource` (`authz.ts:182-195`): a resource of a company the key cannot see is a 404,
     * and a viewer cannot write.
     */
    fun resourceRefusal(
        company: String,
        notFoundMessage: String,
    ): StubReply? {
        val role = access.roleIn(company) ?: return StubReply.notFound(notFoundMessage)
        return if (!isRead && role == StubAccess.VIEWER) StubReply.forbidden("Viewer access is read-only") else null
    }
}

/** The company-scoped endpoints of one stub host, grouped by domain. */
internal class StubWork(
    val clock: () -> Instant,
) {
    val budgets = StubBudgets()
    val approvals = StubApprovals()
    val decisions = StubDecisions(clock)
    val attention = StubAttention(clock)
    val dashboard = StubDashboard()
    val runs = StubRuns()
    val agents = StubAgents(runs, clock)

    fun handle(request: StubRequest): StubReply? {
        val segments = request.segments
        if (segments.size >= COMPANY_PATH_MIN && segments[0] == "companies") {
            request.companyRefusal(segments[1])?.let { return it }
        }
        return budgets.handle(request) ?: approvals.handle(request) ?: decisions.handle(request)
            ?: attention.handle(request) ?: dashboard.handle(request) ?: agents.handle(request)
    }

    private companion object {
        const val COMPANY_PATH_MIN = 3
    }
}

internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.has(name: String): Boolean = name in this && this[name] !is JsonNull

internal fun JsonElement?.contentText(): String? = (this as? JsonPrimitive)?.contentOrNull
