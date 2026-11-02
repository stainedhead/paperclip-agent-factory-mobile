package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** AD-003: one place maps every HTTP status and body shape, and every transport failure, to a [DomainError]. */
class ErrorMapperTest {
    private val serverBody = """{"error":"Budget incident cannot be raised yet","details":{"code":"x"}}"""

    private fun status(
        code: Int,
        body: String = serverBody,
    ) = ErrorMapper.fromStatus(code, body)

    @Test
    fun `AD-003 401 is Unauthorized`() = assertEquals(DomainError.Unauthorized, status(401))

    @Test
    fun `AD-003 403 is Forbidden`() = assertEquals(DomainError.Forbidden, status(403))

    @Test
    fun `AD-003 404 is NotFound`() = assertEquals(DomainError.NotFound, status(404))

    @Test
    fun `AD-003 409 is Conflict`() = assertEquals(DomainError.Conflict, status(409))

    @Test
    fun `AD-003 429 is RateLimited`() = assertEquals(DomainError.RateLimited, status(429))

    @Test
    fun `AD-003 408 is Timeout`() = assertEquals(DomainError.Timeout, status(408))

    @Test
    fun `AD-003 422 carries the server reason`() =
        assertEquals(DomainError.Rejected("Budget incident cannot be raised yet"), status(422))

    @Test
    fun `AD-003 400 validation errors carry the server reason too`() =
        assertEquals(DomainError.Rejected("Validation error"), status(400, """{"error":"Validation error"}"""))

    @Test
    fun `AD-003 a 5xx the server itself answered is ServerError whatever the body`() {
        listOf(500, 501, 503, 599).forEach { assertEquals(DomainError.ServerError, status(it), "status $it") }
    }

    @Test
    fun `AD-003 a 5xx HTML page from a proxy is never Malformed`() =
        assertEquals(DomainError.ServerError, status(503, "<html><body>Service Unavailable</body></html>"))

    @Test
    fun `F-A-5 a gateway failure may have reached the server so it is ConnectionLost`() {
        listOf(502, 504).forEach { assertEquals(DomainError.ConnectionLost, status(it), "status $it") }
        assertEquals(DomainError.ConnectionLost, status(502, "<html><body>Bad Gateway</body></html>"))
    }

    @Test
    fun `AD-003 a 401 or 404 with an HTML or empty body keeps its status meaning`() {
        assertEquals(DomainError.Unauthorized, status(401, "<html>login</html>"))
        assertEquals(DomainError.NotFound, status(404, ""))
    }

    @Test
    fun `AD-003 a rejection without a readable reason falls back to the status`() {
        assertEquals(DomainError.Rejected("http_422"), status(422, ""))
        assertEquals(DomainError.Rejected("http_422"), status(422, "<html>nope</html>"))
        assertEquals(DomainError.Rejected("http_422"), status(422, """{"error":42}"""))
        assertEquals(DomainError.Rejected("http_422"), status(422, """{"error":"  "}"""))
    }

    @Test
    fun `AD-003 other client errors are rejections with their status`() {
        assertEquals(DomainError.Rejected("http_413"), status(413, ""))
        assertEquals(DomainError.Rejected("Unsupported"), status(415, """{"error":"Unsupported"}"""))
    }

    @Test
    fun `AD-003 a long server reason is cut so it cannot flood the screen`() {
        val reason = (status(422, """{"error":"${"x".repeat(1_000)}"}""") as DomainError.Rejected).reason
        assertEquals(ErrorMapper.MAX_REASON_LENGTH, reason.length)
    }

    @Test
    fun `AD-003 redirects and unknown statuses are Malformed because a redirect could carry the key elsewhere`() {
        listOf(301, 302, 307, 308, 100, 600).forEach { assertEquals(DomainError.Malformed, status(it), "status $it") }
    }

    @Test
    fun `AD-003 the three Ktor timeouts are Timeout`() {
        assertEquals(DomainError.Timeout, ErrorMapper.fromTransport(HttpRequestTimeoutException("https://x", 1L)))
        assertEquals(DomainError.Timeout, ErrorMapper.fromTransport(ConnectTimeoutException("connect")))
        assertEquals(DomainError.Timeout, ErrorMapper.fromTransport(SocketTimeoutException("read")))
    }

    @Test
    fun `AD-003 a timeout wrapped in another exception is still Timeout`() {
        val wrapped = IllegalStateException("outer", ConnectTimeoutException("connect"))
        assertEquals(DomainError.Timeout, ErrorMapper.fromTransport(wrapped))
    }

    @Test
    fun `AD-003 an IO failure with no better name is Unreachable`() =
        assertEquals(DomainError.Unreachable, ErrorMapper.fromTransport(IOException("connection refused")))

    @Test
    fun `AD-003 a body that cannot be decoded is Malformed`() =
        assertEquals(DomainError.Malformed, ErrorMapper.fromTransport(SerializationException("bad json")))

    @Test
    fun `AD-003 no exception message reaches the error`() {
        val error = ErrorMapper.fromTransport(IOException("GET https://x/api?token=pcp_cli_auth_TESTONLY"))
        assertIs<DomainError.Unreachable>(error)
        assertEquals("Unreachable", error.toString().substringAfterLast('.').removeSuffix(")"))
    }
}
