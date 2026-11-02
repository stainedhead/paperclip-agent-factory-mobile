package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.testing.TestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = headersOf(HttpHeaders.ContentType, "application/json")

private fun MockRequestHandleScope.ok(body: String = """{"ok":true}"""): HttpResponseData =
    respond(body, HttpStatusCode.OK, json)

/** AD-001, AD-002, AD-004, AD-005: the request pipeline every gateway shares. */
class PaperclipHttpClientTest {
    private val home = TestData.uri()
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        allowInsecureLocal: Boolean = false,
        pinsEnforced: Boolean = false,
        trace: ((String) -> Unit)? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { ok() },
    ) = PaperclipHttpClient(
        MockEngine { request ->
            requests += request
            handler(request)
        },
        allowInsecureLocal,
        pinsEnforced,
        trace,
    )

    private fun get(
        vararg segments: String,
        bearer: Secret? = null,
        pins: List<SpkiPin> = emptyList(),
        base: BaseUri = home,
    ) = ApiRequest(Destination(base, pins), HttpMethod.Get, segments.toList(), bearer = bearer)

    @Test
    fun `AD-001 requests go to the base uri plus api and ask for JSON`() =
        runTest {
            client().send(get("health"))
            assertEquals("https://home.example.com/api/health", requests.single().url.toString())
            assertEquals("application/json", requests.single().headers[HttpHeaders.Accept])
        }

    @Test
    fun `AD-001 a path prefix of the instance is kept`() =
        runTest {
            client().send(get("health", base = TestData.uri("https://example.com/paperclip")))
            assertEquals("https://example.com/paperclip/api/health", requests.single().url.toString())
        }

    @Test
    fun `AD-001 path segments are encoded and query values are encoded`() =
        runTest {
            val request = ApiRequest(Destination(home), HttpMethod.Get, listOf("a/b c"), listOf("token" to "x y&z=1"))
            client().send(request)
            assertEquals("https://home.example.com/api/a%2Fb%20c?token=x+y%26z%3D1", requests.single().url.toString())
        }

    @Test
    fun `AD-001 the bearer key is sent as Authorization and only when a key is given`() =
        runTest {
            val c = client()
            c.send(get("a", bearer = Secret.of(TestData.TOKEN)))
            c.send(get("b"))
            assertEquals("Bearer ${TestData.TOKEN}", requests[0].headers[HttpHeaders.Authorization])
            assertNull(requests[1].headers[HttpHeaders.Authorization])
        }

    @Test
    fun `AD-001 a wiped key is Unauthorized and nothing is sent`() =
        runTest {
            val key = Secret.of(TestData.TOKEN).also { it.wipe() }
            val result = client().send(get("a", bearer = key))
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), result)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `AD-001 a JSON body is sent with its content type`() =
        runTest {
            val request = ApiRequest(Destination(home), HttpMethod.Post, listOf("x"), jsonBody = """{"a":1}""")
            client().send(request)
            assertEquals(
                "application/json",
                requests
                    .single()
                    .body.contentType
                    .toString(),
            )
            assertEquals(HttpMethod.Post, requests.single().method)
        }

    @Test
    fun `AD-001 connect and read timeouts are 15 and 30 seconds`() =
        runTest {
            client().send(get("health"))
            val config = requests.single().getCapabilityOrNull(HttpTimeoutCapability)
            assertEquals(15_000L, config?.connectTimeoutMillis)
            assertEquals(30_000L, config?.socketTimeoutMillis)
        }

    @Test
    fun `AD-001 a 2xx reply returns its status and body`() =
        runTest {
            val result = client { ok("""{"a":1}""") }.send(get("x"))
            assertEquals(DomainResult.Success(ApiResponse(200, """{"a":1}""", isJson = true)), result)
        }

    @Test
    fun `AD-003 a failing status becomes the mapped error`() =
        runTest {
            val result = client { respondError(HttpStatusCode.Forbidden, """{"error":"nope"}""", json) }.send(get("x"))
            assertEquals(DomainResult.Failure(DomainError.Forbidden), result)
        }

    @Test
    fun `AD-003 a transport failure becomes the mapped error and keeps no message`() =
        runTest {
            val result =
                client { throw IOException("GET https://home.example.com/api/x?token=pcp_cli_auth_TESTONLY") }
                    .send(get("x"))
            assertEquals(DomainResult.Failure(DomainError.Unreachable), result)
        }

    @Test
    fun `AD-003 a redirect is not followed and so the key cannot reach another host`() =
        runTest {
            val c =
                client {
                    respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://evil.example.com/api/x"))
                }
            val result = c.send(get("x", bearer = Secret.of(TestData.TOKEN)))
            assertEquals(DomainResult.Failure(DomainError.Malformed), result)
            assertEquals(1, requests.size)
        }

    @Test
    fun `AD-002 a mutation is sent exactly once even when the server fails`() =
        runTest {
            val c = client { respondError(HttpStatusCode.ServiceUnavailable) }
            val result = c.send(ApiRequest(Destination(home), HttpMethod.Post, listOf("x"), jsonBody = "{}"))
            assertEquals(DomainResult.Failure(DomainError.ServerError), result)
            assertEquals(1, requests.size)
        }

    @Test
    fun `AD-002 a read is also sent once here because retries belong to the use case`() =
        runTest {
            client { throw IOException("down") }.send(get("x"))
            assertEquals(1, requests.size)
        }

    @Test
    fun `AD-001 cancelling the caller cancels the request and is never turned into an error`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val c =
                client {
                    started.complete(Unit)
                    CompletableDeferred<HttpResponseData>().await()
                }
            val call = async(start = CoroutineStart.UNDISPATCHED) { c.send(get("x")) }
            started.await()
            call.cancel()
            assertFailsWith<kotlinx.coroutines.CancellationException> { call.await() }
        }

    private fun insecure(text: String): BaseUri =
        (BaseUri.parse(text, allowInsecureLocal = true) as BaseUriResult.Valid).uri

    @Test
    fun `AD-004 http is refused unless insecure local addresses are allowed`() =
        runTest {
            val local = insecure("http://localhost:3100")
            val refused = client(allowInsecureLocal = false).send(get("health", base = local))
            assertEquals(DomainResult.Failure(DomainError.CertificateInvalid), refused)
            assertTrue(requests.isEmpty())
            assertIs<DomainResult.Success<*>>(client(allowInsecureLocal = true).send(get("health", base = local)))
            assertEquals("http://localhost:3100/api/health", requests.single().url.toString())
        }

    @Test
    fun `AD-004 the Android emulator host is allowed over http when insecure local is on`() =
        runTest {
            val result = client(allowInsecureLocal = true).send(get("health", base = insecure("http://10.0.2.2:3100")))
            assertIs<DomainResult.Success<*>>(result)
            assertEquals("http://10.0.2.2:3100/api/health", requests.single().url.toString())
        }

    @Test
    fun `AD-005 an instance with pins fails closed while no engine enforces them`() =
        runTest {
            val pin = SpkiPin.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")!!
            val result = client(pinsEnforced = false).send(get("x", pins = listOf(pin)))
            assertEquals(DomainResult.Failure(DomainError.CertificateInvalid), result)
            assertTrue(requests.isEmpty())
            assertIs<DomainResult.Success<*>>(client(pinsEnforced = true).send(get("x", pins = listOf(pin))))
        }

    @Test
    fun `AD-001 the trace hook sees method path and status but never query headers or bodies`() =
        runTest {
            val lines = mutableListOf<String>()
            val c = client(trace = { lines += it }) { ok("""{"secret":"body"}""") }
            val request =
                ApiRequest(
                    Destination(home),
                    HttpMethod.Get,
                    listOf("cli-auth", "challenges", "id1"),
                    listOf("token" to "pcp_cli_auth_TESTONLY"),
                    bearer = Secret.of(TestData.TOKEN),
                )
            c.send(request)
            assertEquals(listOf("GET home.example.com /api/cli-auth/challenges/id1 -> 200"), lines)
        }

    @Test
    fun `AD-001 no trace hook means nothing is logged`() =
        runTest {
            assertIs<DomainResult.Success<*>>(client().send(get("x")))
        }
}
