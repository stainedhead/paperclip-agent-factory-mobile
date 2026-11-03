package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Secret
import com.sun.net.httpserver.HttpServer
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F-A-8 and AD-003 with the REAL production engine (OkHttp on the JVM and Android), not MockEngine, which cannot follow
 * a redirect and so could never fail: a server that answers 302 to another host and port must not be followed, the
 * second server must see no request (so a bearer key can never be replayed there), and the reply is a typed error.
 */
class RealEngineRedirectTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val firstSeen = CopyOnWriteArrayList<String>()
    private val targetSeen = CopyOnWriteArrayList<String>()
    private val servers = mutableListOf<HttpServer>()

    private fun serve(
        seen: CopyOnWriteArrayList<String>,
        respond: (com.sun.net.httpserver.HttpExchange) -> Unit,
    ): HttpServer =
        HttpServer.create(InetSocketAddress(loopback, 0), 0).also { server ->
            server.createContext("/") { exchange ->
                seen +=
                    "${exchange.requestMethod} ${exchange.requestURI} auth=${exchange.requestHeaders.getFirst(
                        "Authorization",
                    )}"
                respond(exchange)
                exchange.close()
            }
            server.start()
            servers += server
        }

    @AfterTest
    fun stop() = servers.forEach { it.stop(0) }

    private fun send(
        port: Int,
        method: HttpMethod,
    ): DomainResult<ApiResponse> {
        val base = (BaseUri.parse("http://localhost:$port", allowInsecureLocal = true) as BaseUriResult.Valid).uri
        val client = PaperclipHttpClient(platformEngine(), allowInsecureLocal = true, pinsEnforced = false)
        val request =
            ApiRequest(
                Destination(base),
                method,
                listOf("companies"),
                jsonBody = if (method == HttpMethod.Post) "{}" else null,
                bearer = Secret.of("pcp_board_TESTONLY_redirect"),
            )
        return runBlocking { client.send(request) }
    }

    @Test
    fun `AD-003 the OkHttp engine does not follow a 302 to another port and the key is never replayed there`() {
        val target = serve(targetSeen) { it.sendResponseHeaders(200, -1) }
        val first =
            serve(firstSeen) {
                it.responseHeaders.add("Location", "http://localhost:${target.address.port}/api/companies")
                it.sendResponseHeaders(302, -1)
            }
        listOf(HttpMethod.Get, HttpMethod.Post).forEach { method ->
            val result = send(first.address.port, method)
            assertEquals(DomainResult.Failure(DomainError.Malformed), result, "$method")
        }
        assertEquals(2, firstSeen.size, "each call is sent exactly once: $firstSeen")
        assertTrue(firstSeen.all { it.endsWith("auth=Bearer pcp_board_TESTONLY_redirect") })
        assertEquals(emptyList<String>(), targetSeen.toList(), "the redirect target must see no request")
    }

    @Test
    fun `AD-003 307 and 308 redirects are not followed either`() {
        val target = serve(targetSeen) { it.sendResponseHeaders(200, -1) }
        listOf(301, 303, 307, 308).forEach { code ->
            val first =
                serve(firstSeen) {
                    it.responseHeaders.add("Location", "http://localhost:${target.address.port}/")
                    it.sendResponseHeaders(code, -1)
                }
            assertEquals(DomainResult.Failure(DomainError.Malformed), send(first.address.port, HttpMethod.Get), "$code")
        }
        assertEquals(emptyList<String>(), targetSeen.toList())
    }
}
