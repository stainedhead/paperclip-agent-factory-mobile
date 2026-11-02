package com.stainedhead.paperclipmobile.infrastructure.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What one request looked like to the stub: enough to assert where a key went and what a URL carried. */
data class RecordedRequest(
    val host: String,
    val method: String,
    val path: String,
    val query: String,
    val authorization: String?,
    val body: String,
)

/** How an address answers `GET /api/health`. */
enum class HealthMode { Ready, LocalTrusted, Bootstrapping, Unhealthy, HtmlPage, Starting }

/**
 * A stateful stand-in for the pinned Paperclip server, behind a Ktor [MockEngine] (research Q6). Each host name is a
 * separate server with its own challenges and keys, so "a key issued by one server means nothing on another" is real
 * here. It follows the server's rules from `access.ts` and `board-auth.ts`: ids must be UUIDs (else 400), a challenge
 * is looked up by id and token (else 404), status precedence is cancelled > expired > approved > pending, and a
 * pending key works only once its challenge is approved. An unregistered host refuses the connection.
 */
class StubPaperclipServer(
    private val clock: () -> Instant,
) {
    private val hosts = mutableMapOf<String, StubHost>()
    private var counter = 0

    val requests = mutableListOf<RecordedRequest>()

    /** Every exception message this stub could leak: none by design; the audit test reads responses instead. */
    val engine: MockEngine = MockEngine { request -> handle(request) }

    fun host(name: String): StubHost = hosts.getOrPut(name) { StubHost(name) }

    /** Seeds an already issued, active key (a device that paired earlier). */
    fun issueKey(
        host: String,
        token: String,
        id: String,
        name: String,
    ) {
        host(host).keys[token] = StubKey(id, name)
    }

    fun approve(
        host: String,
        challengeId: String,
    ) {
        val server = host(host)
        val challenge = server.challenges.getValue(challengeId)
        challenge.approved = true
        server.keys[challenge.pendingKey] = StubKey(newUuid(), "Alex's phone (board)")
    }

    fun expire(
        host: String,
        challengeId: String,
    ) {
        host(host).challenges.getValue(challengeId).expiresAt = clock() - 1.minutes
    }

    fun cancel(
        host: String,
        challengeId: String,
    ) {
        host(host).challenges.getValue(challengeId).cancelled = true
    }

    private fun newUuid(): String {
        counter += 1
        return "00000000-0000-4000-8000-" + counter.toString().padStart(UUID_TAIL, '0')
    }

    private fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val host = request.url.host
        val body = (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
        requests +=
            RecordedRequest(
                host = host,
                method = request.method.value,
                path = request.url.encodedPath,
                query = request.url.encodedQuery,
                authorization = request.headers[HttpHeaders.Authorization],
                body = body,
            )
        val server = hosts[host] ?: throw IOException("connection refused")
        val path = request.url.encodedPath.substringAfter("/api")
        return route(server, request, path, body)
    }

    private fun MockRequestHandleScope.route(
        server: StubHost,
        request: HttpRequestData,
        path: String,
        body: String,
    ): HttpResponseData {
        val method = request.method
        val bearer = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
        return when {
            method == HttpMethod.Get && path == "/health" -> {
                health(server)
            }

            method == HttpMethod.Post && path == "/cli-auth/challenges" -> {
                createChallenge(server, request, body)
            }

            method == HttpMethod.Get && path.startsWith("/cli-auth/challenges/") -> {
                poll(server, path.substringAfterLast('/'), request.url.parameters["token"].orEmpty())
            }

            else -> {
                authenticated(server, method, path, bearer)
            }
        }
    }

    private fun MockRequestHandleScope.authenticated(
        server: StubHost,
        method: HttpMethod,
        path: String,
        bearer: String?,
    ): HttpResponseData {
        val key = bearer?.let { server.keys[it] }?.takeUnless { it.revoked }
        if (key == null) return failure(HttpStatusCode.Unauthorized, "Board authentication required")
        return when {
            method == HttpMethod.Get && path == "/cli-auth/me" -> {
                json(ServerFixtures.me(key.id))
            }

            method == HttpMethod.Get && path == "/companies" -> {
                json(ServerFixtures.companies())
            }

            method == HttpMethod.Get && path == "/board-api-keys" -> {
                json(keyList(server))
            }

            method == HttpMethod.Post && path == "/cli-auth/revoke-current" -> {
                key.revoked = true
                json("""{"revoked":true,"keyId":"${key.id}"}""")
            }

            method == HttpMethod.Delete && path.startsWith("/board-api-keys/") -> {
                revokeKey(server, path.substringAfterLast('/'))
            }

            else -> {
                failure(HttpStatusCode.NotFound, "Not found")
            }
        }
    }

    private fun keyList(server: StubHost): String =
        server.keys.values
            .filterNot { it.revoked }
            .joinToString(",", "[", "]") { ServerFixtures.key(it.id, it.name) }

    private fun MockRequestHandleScope.revokeKey(
        server: StubHost,
        id: String,
    ): HttpResponseData {
        if (!UUID.matches(id)) return failure(HttpStatusCode.BadRequest, "Invalid board API key ID")
        val key = server.keys.values.firstOrNull { it.id == id && !it.revoked }
        if (key == null) return failure(HttpStatusCode.NotFound, "Board API key not found")
        key.revoked = true
        return json("""{"ok":true,"keyId":"$id"}""")
    }

    private fun MockRequestHandleScope.health(server: StubHost): HttpResponseData =
        when (server.health) {
            HealthMode.Ready -> {
                json(ServerFixtures.healthRedacted())
            }

            HealthMode.Starting -> {
                json(ServerFixtures.healthRedacted(status = "starting"))
            }

            HealthMode.LocalTrusted -> {
                json(ServerFixtures.healthFull())
            }

            HealthMode.Bootstrapping -> {
                json(ServerFixtures.healthRedacted(bootstrap = "bootstrap_pending"))
            }

            HealthMode.Unhealthy -> {
                respond(ServerFixtures.HEALTH_UNHEALTHY, HttpStatusCode.ServiceUnavailable, JSON)
            }

            HealthMode.HtmlPage -> {
                respond(ServerFixtures.HTML_PAGE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
            }
        }

    private fun MockRequestHandleScope.createChallenge(
        server: StubHost,
        request: HttpRequestData,
        body: String,
    ): HttpResponseData {
        if ("\"command\"" !in body) return failure(HttpStatusCode.BadRequest, "Validation error")
        val id = newUuid()
        val challenge =
            StubChallenge(
                "pcp_cli_auth_TESTONLY$counter",
                "pcp_board_TESTONLY_pending$counter",
                clock() + CHALLENGE_TTL,
            )
        server.challenges[id] = challenge
        val origin = "${request.url.protocol.name}://${request.url.host}"
        val created =
            ServerFixtures.challengeCreated(
                id,
                challenge.token,
                challenge.pendingKey,
                origin,
                challenge.expiresAt.toString(),
            )
        return respond(created, HttpStatusCode.Created, JSON)
    }

    private fun MockRequestHandleScope.poll(
        server: StubHost,
        id: String,
        token: String,
    ): HttpResponseData {
        if (token.isEmpty()) return failure(HttpStatusCode.NotFound, "CLI auth challenge not found")
        if (!UUID.matches(id)) return failure(HttpStatusCode.BadRequest, "Invalid CLI auth challenge ID")
        val challenge =
            server.challenges[id]?.takeIf { it.token == token }
                ?: return failure(HttpStatusCode.NotFound, "CLI auth challenge not found")
        val status =
            when {
                challenge.cancelled -> "cancelled"
                challenge.expiresAt <= clock() -> "expired"
                challenge.approved -> "approved"
                else -> "pending"
            }
        return json(ServerFixtures.challengePolled(id, status))
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, HttpStatusCode.OK, JSON)

    private fun MockRequestHandleScope.failure(
        status: HttpStatusCode,
        message: String,
    ) = respond(ServerFixtures.error(message), status, JSON)

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val CHALLENGE_TTL = 10.minutes
        const val UUID_TAIL = 12
    }
}

/** One simulated server: how it reports health, its challenges, and its board keys by token. */
class StubHost(
    val name: String,
) {
    var health: HealthMode = HealthMode.Ready
    internal val challenges = mutableMapOf<String, StubChallenge>()
    internal val keys = mutableMapOf<String, StubKey>()
}

internal class StubChallenge(
    val token: String,
    val pendingKey: String,
    var expiresAt: Instant,
) {
    var approved = false
    var cancelled = false
}

internal class StubKey(
    val id: String,
    val name: String,
) {
    var revoked = false
}
